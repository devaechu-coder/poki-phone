package com.november.mcphone.core.script.server.economy;

import com.november.mcphone.MCphone;
import com.november.mcphone.core.script.engine.ScriptBudget;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 货币的线程模型，<b>只有这一份实现</b>（§15.5、§20.4、E25 ③）：一切对余额与托管的读写都在服务端主线程上执行。
 *
 * <ul>
 *   <li>调用本来就在主线程上 → 直接执行。排队等自己就是死锁。</li>
 *   <li>在 worker 上 → 交给主线程执行，worker 等结果。满队、超时、停服、被打断 → 拒，给原因（翻译键）。</li>
 * </ul>
 *
 * <h2>超时的调用以后绝不会被执行</h2>
 *
 * 每个调用在「排队 → 执行中 → 完成」与「排队 → 已取消」之间只能走一条，靠 CAS 定。
 * worker 等超时后先把它取消掉才回 UNAVAILABLE；取消不掉说明主线程已经开始执行了，那就等它做完、交出真结果 ——
 * 否则调用方以为没转成，钱却转出去了。这一段等待不受单次上限约束，所以主线程上那一步<b>必须快</b>：
 * builtin 与计分板只动内存；adapter 档的外部钱包也在这一步里跑，见 {@link AdapterProvider.ExternalWallet}。
 *
 * <h2>拒绝用 UNAVAILABLE，不用 FAILED</h2>
 *
 * FAILED 的意思是"实现内部出错"（E26 ②）；排队满、主线程忙不是出错，重试可能就过了。
 */
public final class CurrencyGateway {

    /** 排着等主线程的调用最多几个。worker 只有两条，这个数主要挡的是主线程卡死时的堆积。 */
    public static final int MAX_PENDING = 256;

    /** 单次调用最多等主线程多久。工程常值，实测后再调；一次求值里的累计另有上限（{@link ScriptBudget}）。 */
    public static final long CALL_WAIT_NANOS = 250_000_000L;

    public static final String KEY_BUSY = "mcphone.economy.unavailable.main_thread_busy";
    public static final String KEY_QUEUE_FULL = "mcphone.economy.unavailable.queue_full";
    public static final String KEY_CLOSED = "mcphone.economy.unavailable.stopping";
    public static final String KEY_INTERRUPTED = "mcphone.economy.unavailable.interrupted";
    public static final String KEY_WAIT_BUDGET = "mcphone.economy.unavailable.wait_budget";

    private static final int PENDING = 0;
    private static final int RUNNING = 1;
    private static final int DONE = 2;
    private static final int CANCELLED = 3;

    private final Executor mainThread;
    private final BooleanSupplier onMainThread;
    private final int maxPending;
    private final long callWaitNanos;
    private final AtomicInteger pending = new AtomicInteger();
    private final Set<Call<?>> inFlight = ConcurrentHashMap.newKeySet();
    private final AtomicLong lastWarn = new AtomicLong();
    private volatile boolean open;

    /**
     * @param mainThread   把一个活儿交给主线程。生产环境是 {@code server::execute}
     * @param onMainThread 当前是不是主线程。生产环境比 {@code server.getRunningThread()}
     */
    public CurrencyGateway(Executor mainThread, BooleanSupplier onMainThread) {
        this(mainThread, onMainThread, MAX_PENDING, CALL_WAIT_NANOS);
    }

    /** 断言测试用：排队上限与单次等待调小，满队与超时才测得动。 */
    CurrencyGateway(Executor mainThread, BooleanSupplier onMainThread, int maxPending, long callWaitNanos) {
        this.mainThread = mainThread;
        this.onMainThread = onMainThread;
        this.maxPending = maxPending;
        this.callWaitNanos = callWaitNanos;
    }

    /** 开服扫描完托管之后再开：扫描完成之前不接受任何货币调用。 */
    public void open() {
        open = true;
    }

    /**
     * 停服时调，<b>要在停 worker 之前</b>：还在排队的一律取消，等着的 worker 立刻拿到 UNAVAILABLE。
     * 反过来的话，worker 等主线程、主线程等 worker 停下，要白等到超时。
     */
    public void close() {
        open = false;
        for (Call<?> c : inFlight) c.cancel();
    }

    public boolean isOpen() {
        return open;
    }

    /** 排着的个数。只给日志与测试用。 */
    public int pending() {
        return pending.get();
    }

    /**
     * 在主线程上执行 {@code op}，把结果带回来。{@code op} 抛的非受检异常原样抛出；受检的（Kotlin、@SneakyThrows）换成
     * {@link ProviderFailure} —— 调用方的 catch 都不认受检异常，挂着原来那个的话日志渲染时它的 getMessage 可能会炸。
     *
     * @throws CurrencyUnavailableException 没执行（原因见 {@link CurrencyUnavailableException#reasonKey()}）——
     *                                      抛这个的时候 {@code op} 一定没有、也永远不会被执行
     */
    public <T> T call(Supplier<T> op) {
        if (onMainThread.getAsBoolean()) {
            try {
                return op.get();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable checked) {
                throw ProviderFailure.of(checked);
            }
        }
        if (!open) throw refuse(KEY_CLOSED);
        long allowance = Math.min(callWaitNanos, ScriptBudget.hostWaitLeftNanos());
        if (allowance <= 0) throw refuse(KEY_WAIT_BUDGET);
        if (pending.incrementAndGet() > maxPending) {
            pending.decrementAndGet();
            throw refuse(KEY_QUEUE_FULL);
        }
        Call<T> c = new Call<>(op, CallingApp.current());
        inFlight.add(c);
        // close() 可能正好在上面那次判断之后扫过 inFlight，漏掉了这一个
        if (!open && c.cancel()) {
            pending.decrementAndGet();
            throw refuse(KEY_CLOSED);
        }
        try {
            mainThread.execute(c);
        } catch (RuntimeException e) {
            if (c.cancel()) {
                pending.decrementAndGet();
                throw refuse(KEY_CLOSED);
            }
        }

        long start = System.nanoTime();
        boolean interrupted = false;
        try {
            boolean finished;
            try {
                finished = c.done.await(allowance, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                interrupted = true;
                finished = false;
            }
            if (!finished && c.cancel()) throw refuse(interrupted ? KEY_INTERRUPTED : KEY_BUSY);
            // 取消不掉：主线程已经开始执行（或已被 close 取消）。等它落定，不许半路走开
            while (true) {
                try {
                    c.done.await();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            ScriptBudget.hostWaited(System.nanoTime() - start);
            if (interrupted) Thread.currentThread().interrupt();
        }
        if (c.state.get() == CANCELLED) throw refuse(KEY_CLOSED);
        if (c.error instanceof RuntimeException re) throw re;
        if (c.error instanceof Error er) throw er;
        // 受检异常换成替身抛：原来那个挂成 cause 的话，日志渲染 Caused by 时会调它的 getMessage，它可能自己会炸
        if (c.error != null) throw ProviderFailure.of(c.error);
        return c.result;
    }

    private CurrencyUnavailableException refuse(String key) {
        // 限流：主线程卡住时每个调用都会走到这里，一秒记一次就够看出问题
        long now = System.nanoTime();
        long last = lastWarn.get();
        if (now - last > 1_000_000_000L && lastWarn.compareAndSet(last, now)) {
            MCphone.LOGGER.warn("[MCphone] 货币调用没执行：{}（排着 {} 个）", key, pending.get());
        }
        return new CurrencyUnavailableException(key);
    }

    private final class Call<T> implements Runnable {
        final Supplier<T> op;
        final String app;
        final AtomicInteger state = new AtomicInteger(PENDING);
        final CountDownLatch done = new CountDownLatch(1);
        T result;
        Throwable error;

        Call(Supplier<T> op, String app) {
            this.op = op;
            this.app = app;
        }

        /** 主线程上跑。 */
        @Override
        public void run() {
            pending.decrementAndGet();
            // 原版服务器停了之后 execute() 会就地在调用方线程上跑 —— 那就是在 worker 上碰账，绝不许
            if (!onMainThread.getAsBoolean()) {
                cancel();
                return;
            }
            if (!state.compareAndSet(PENDING, RUNNING)) return;
            String outer = CallingApp.current();
            CallingApp.enter(app);
            try {
                result = op.get();
            } catch (Throwable t) {
                error = t;
            } finally {
                CallingApp.enter(outer);
                state.set(DONE);
                inFlight.remove(this);
                done.countDown();
            }
        }

        boolean cancel() {
            if (!state.compareAndSet(PENDING, CANCELLED)) return false;
            inFlight.remove(this);
            done.countDown();
            return true;
        }
    }
}
