package com.november.mcphone.core.script.server;

import com.november.mcphone.MCphone;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 脚本求值的 worker 池（施工方案 §15.5：固定 2 线程 + 有界队列）。
 *
 * <h2>有界队列只挡得住这一跳</h2>
 *
 * §15.5 画的是"网络线程 → 廉价准入 → 有界队列"。<b>实际做不到</b>：三个平台的登记门面
 * 在我们的代码跑到之前就已经把每个包塞进了服务器任务队列
 * （forge 的 {@code consumerMainThread}、neoforge 的 {@code ctx.enqueueWork}、
 * fabric 的 {@code server.execute}），而那个队列无界、且任务书禁止碰门面的登记语义。
 *
 * <p>所以实际是四层，这个类是第三层的入口：
 *
 * <pre>
 * 网络线程   decode：定长读 + 上限（readUtf(64) / Wire.readBytes(4096)）—— 内存放大的唯一防线
 * 主线程     门面已 enqueue（这一跳无界，够不着）→ O(1) 准入 → 这个类的有界队列
 * worker×2   求值
 * 主线程     落地 + 重查授权
 * </pre>
 *
 * 「网络线程不许做昂贵的事」这条<b>仍然成立</b>：netty 上只有定长读。
 * 变的是"廉价准入"落在主线程而不是网络线程 —— 而准入本身不是新增的 tick 成本，
 * enqueue 才是，那是现状（37 个现有包都这样）。
 *
 * <h2>必须关，daemon 不是关闭方案</h2>
 *
 * <b>单人游戏里服务器会在同一个 JVM 里停掉再起来</b>（退出世界再进另一个）。
 * 不关的话，这个池连同它排队中的任务会带着上一个世界的引用活到下一个世界，
 * 而排队中的求值会在服务器已经停了之后回调到主线程，落地时拿到一个死掉的 MinecraftServer。
 *
 * <p>{@code setDaemon(true)} 仍然要设，那是"万一 {@link #stop()} 漏了别挂住 JVM"的兜底，
 * 不是关闭方案本身。
 */
public final class ScriptWorkers {

    /** 固定 2 线程（§15.5）。 */
    public static final int THREADS = 2;

    /** 有界队列的容量。满了就回 RATE_LIMITED，不排队、不丢弃。 */
    public static final int QUEUE_CAPACITY = 256;

    /** 停的时候等多久。等不到就不等了 —— 世界已经在关，拖着只会让玩家以为卡死。 */
    public static final long SHUTDOWN_WAIT_MS = 5_000L;

    private ScriptWorkers() {
    }

    private static volatile ThreadPoolExecutor pool;

    /** 服务器起来时调。重复调是幂等的。 */
    public static synchronized void start() {
        if (pool != null) return;
        int[] n = {0};
        pool = new ThreadPoolExecutor(THREADS, THREADS, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                r -> {
                    Thread t = new Thread(r, "MCphone-Script-" + n[0]++);
                    // 兜底，不是关闭方案：真正的关闭是 stop()
                    t.setDaemon(true);
                    return t;
                });
    }

    /**
     * 服务器停止时调。<b>顺序是写死的</b>：拒收新任务 → 打断在跑的 → 等一小会儿 → 撒手。
     *
     * <p>调用方在这之后还要清掉账本、桶表、epoch 表 —— 那些静态表同样会把上一个世界钉住。
     */
    public static synchronized void stop() {
        ThreadPoolExecutor p = pool;
        pool = null;
        if (p == null) return;
        p.shutdownNow();
        try {
            if (!p.awaitTermination(SHUTDOWN_WAIT_MS, TimeUnit.MILLISECONDS)) {
                MCphone.LOGGER.warn("[MCphone] 脚本 worker 没在 {} 毫秒内停下，不等了", SHUTDOWN_WAIT_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 丢一个活儿进去。
     *
     * @return 收没收下。<b>false = 队列满了或者池没起来</b>，调用方要回 RATE_LIMITED，
     *         不许静默丢弃 —— 静默丢弃会让客户端干等到超时
     */
    public static boolean submit(Runnable task) {
        ThreadPoolExecutor p = pool;
        if (p == null) return false;
        try {
            p.execute(task);
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    /** 队列里还排着几个。只给日志与测试用。 */
    public static int queued() {
        ThreadPoolExecutor p = pool;
        return p == null ? 0 : p.getQueue().size();
    }

    /** 池起来了没有。 */
    public static boolean running() {
        return pool != null;
    }
}
