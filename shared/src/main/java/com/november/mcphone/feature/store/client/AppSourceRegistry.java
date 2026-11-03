package com.november.mcphone.feature.store.client;

import com.november.mcphone.MCphone;
import com.november.mcphone.api.client.store.AppInfo;
import com.november.mcphone.api.client.store.IAppSource;
import com.november.mcphone.util.SpiLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.function.Consumer;

/** 应用来源注册表，通过 SPI 发现所有 {@link IAppSource} */
public final class AppSourceRegistry {

    private static final Map<ResourceLocation, IAppSource> SOURCES = new LinkedHashMap<>();
    private static boolean loaded = false;

    private AppSourceRegistry() {}

    /** 同 id 冲突时保留先注册者并告警 */
    public static boolean register(IAppSource source) {
        if (source == null || source.getId() == null) {
            MCphone.LOGGER.warn("[MCphone] 应用来源注册失败: id 为空");
            return false;
        }
        IAppSource old = SOURCES.get(source.getId());
        if (old != null) {
            MCphone.LOGGER.warn("[MCphone] 应用来源 id 冲突: '{}' 已由 {} 注册，忽略 {}",
                    source.getId(), old.getClass().getName(), source.getClass().getName());
            return false;
        }
        SOURCES.put(source.getId(), source);
        MCphone.LOGGER.info("[MCphone] 应用来源已注册: {}", source.getId());
        return true;
    }

    /** 全部已注册来源，保持注册顺序 */
    public static List<IAppSource> getSources() {
        ensureLoaded();
        return List.copyOf(SOURCES.values());
    }

    public static IAppSource getSource(ResourceLocation id) {
        ensureLoaded();
        return SOURCES.get(id);
    }

    /**
     * 等一个来源最多等这么久。到点就按已经回来的那些出列表。
     *
     * <p>取 10 秒：服务器商店那条路要等服务端回包（§14.1），几百毫秒是常态、网络差时几秒也有；
     * 而玩家盯着一张空列表超过十秒就会以为坏了。宁可少列几个来源的东西，也不能永远出不来。
     */
    public static final long LIST_TIMEOUT_MS = 10_000L;

    /**
     * 正在飞的那一批。同一时刻只有一批 —— 新的一批开始时上一批作废（见 {@link #listAllAvailable}）。
     */
    private static Batch pending;

    /**
     * 一次 listAllAvailable 的现场。
     *
     * <p>全部访问都在 {@code this} 上同步：这套加固防的正是<b>不守契约</b>的来源
     * （{@link IAppSource} 要求回调在客户端主线程，而守规矩的来源本来也不需要这套）。
     * 一个从后台线程回调的来源会与超时那条路撞在一起，不同步就是一次数据竞争。
     */
    private static final class Batch {

        private final Consumer<List<AppInfo>> callback;
        private final int total;
        private final long deadline;
        private final List<AppInfo> merged = new ArrayList<>();
        private final Set<ResourceLocation> answered = new HashSet<>();
        private boolean finished;

        Batch(Consumer<List<AppInfo>> callback, int total, long deadline) {
            this.callback = callback;
            this.total = total;
            this.deadline = deadline;
        }

        /** 一个来源答了。齐了就出列表，并且只出这一次。 */
        void answer(ResourceLocation id, List<AppInfo> list) {
            boolean full;
            synchronized (this) {
                if (finished) {
                    // 超时之后、或者这一批已经作废之后才回来的：下次进商店会带上它
                    MCphone.LOGGER.warn("[MCphone] 应用来源 {} 回调来晚了，这一批已经出过列表", id);
                    return;
                }
                if (!answered.add(id)) {
                    // 回调两次的来源：多出来的那次会让同一批 App 在商店里出现两遍
                    MCphone.LOGGER.warn("[MCphone] 应用来源 {} 回调了不止一次，后面的已忽略", id);
                    return;
                }
                if (list != null) merged.addAll(list);
                full = answered.size() == total;
            }
            if (full) finish();
        }

        /** 到点了没有？ */
        synchronized boolean expired(long now) {
            return !finished && now >= deadline;
        }

        /** 还差谁，报进日志用。 */
        synchronized List<ResourceLocation> missing(List<IAppSource> sources) {
            List<ResourceLocation> out = new ArrayList<>();
            for (IAppSource s : sources) {
                if (!answered.contains(s.getId())) out.add(s.getId());
            }
            return out;
        }

        /**
         * 出列表，只出一次。
         *
         * <p>不在锁里调 callback：那是外人的代码（商店那一页），持锁调外部回调就是一个死锁面。
         * 而且要回客户端主线程 —— 这套加固防的正是<b>不守契约、从后台线程回调</b>的来源，
         * 它的那次 answer 会一路走到这里，而 callback 里碰的是 GUI 状态。
         */
        void finish() {
            List<AppInfo> out;
            synchronized (this) {
                if (finished) return;
                finished = true;
                out = List.copyOf(merged);
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.isSameThread()) {
                callback.accept(out);
            } else {
                mc.execute(() -> callback.accept(out));
            }
        }

        /** 作废：不出列表，之后来的回调也不算数。 */
        synchronized void cancel() {
            finished = true;
        }

        synchronized boolean done() {
            return finished;
        }
    }

    /**
     * 各来源可能异步返回，全部到齐后才调用 callback 一次；callback 在客户端主线程执行。
     *
     * <p><b>超时与恰好一次</b>（§14.1 ⚠、§14.6）：一个来源不回调、或者回调两次，原来都会让商店
     * 永远出不来列表 / 出两遍。现在三头都堵上 —— 每个来源的回调只认第一次；整体到
     * {@link #LIST_TIMEOUT_MS} 还没齐就按已有的出（由 {@link #tick()} 盯着）；
     * <b>新的一批开始时上一批作废</b>，两次重叠的刷新不会互相覆盖（进商店一次、装完一个 App 再刷一次，
     * 这两下本来就可能叠在一起，而旧那次晚到的结果会把新的冲掉）。
     */
    public static void listAllAvailable(Consumer<List<AppInfo>> callback) {
        List<IAppSource> sources = new ArrayList<>();
        for (IAppSource s : getSources()) {
            if (s.isReady()) sources.add(s);
        }

        Batch previous = pending;
        if (previous != null) previous.cancel();
        pending = null;

        if (sources.isEmpty()) {
            callback.accept(List.of());
            return;
        }

        Batch batch = new Batch(callback, sources.size(), System.currentTimeMillis() + LIST_TIMEOUT_MS);
        pending = batch;

        for (IAppSource s : sources) {
            ResourceLocation id = s.getId();
            try {
                s.listAvailable(list -> batch.answer(id, list));
            } catch (Throwable t) {
                // 列的时候当场抛的来源：当它答过了，否则这一批永远凑不齐，只能干等超时
                MCphone.LOGGER.error("[MCphone] 应用来源 {} 的 listAvailable 抛异常，已跳过", id, t);
                batch.answer(id, null);
            }
        }
        // 认一下是不是自己那一批：回调里又发起一次 listAllAvailable 的话，pending 已经是新的那个了
        if (pending == batch && batch.done()) pending = null;   // 全是同步来源，已经出过了
    }

    /**
     * 由 {@code ClientTicks} 每客户端 tick 调一次：在飞的那一批到点还没齐就按已有的出。
     *
     * <p>用 tick 泵而不是挂一个定时任务：这个仓库里等待都是这么写的（见 ChatImageCache 的重试），
     * 而且挂定时任务的话，每开一次商店都留一个十秒后才醒来、醒来发现没事可做的线程任务。
     */
    public static void tick() {
        Batch batch = pending;
        if (batch == null) return;
        if (batch.done()) {
            pending = null;
            return;
        }
        if (!batch.expired(System.currentTimeMillis())) return;

        pending = null;
        List<IAppSource> sources = new ArrayList<>();
        for (IAppSource s : getSources()) {
            if (s.isReady()) sources.add(s);
        }
        MCphone.LOGGER.warn("[MCphone] 等了 {} 毫秒还没回来的应用来源: {}，先按已有的出列表",
                LIST_TIMEOUT_MS, batch.missing(sources));
        batch.finish();
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        // 必须走 SpiLoader：一个来源类构造失败会连本地来源一起丢掉，商店直接变空
        int count = 0;
        for (IAppSource s : SpiLoader.loadSafely(IAppSource.class, "应用来源")) {
            try {
                if (register(s)) count++;
            } catch (Throwable t) {
                MCphone.LOGGER.error("[MCphone] 应用来源 {} 登记时抛异常，已跳过",
                        s.getClass().getName(), t);
            }
        }
        MCphone.LOGGER.info("[MCphone] 应用来源扫描完成，共 {} 个", count);
    }
}
