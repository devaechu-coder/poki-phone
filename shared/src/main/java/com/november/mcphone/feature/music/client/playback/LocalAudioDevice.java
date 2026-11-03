package com.november.mcphone.feature.music.client.playback;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.openal.EXTThreadLocalContext;
import org.lwjgl.openal.SOFTHRTF;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** 独立耳机设备。只改调用线程的上下文，绝不切换游戏声音线程使用的进程上下文。 */
final class LocalAudioDevice<C> implements AutoCloseable {
    /** 原生边界；能力表与上下文成对保存，测试无需加载声卡。 */
    interface Driver<C> {
        long openDevice();
        boolean supportsThreadContext(long device);
        long createContext(long device);
        long threadContext();
        C capabilities();
        boolean bindThread(long context);
        void capabilities(C value);
        C createCapabilities(long device);
        void destroyContext(long context);
        void closeDevice(long device);
    }

    private final Driver<C> driver;
    private final long device, context;
    private final Thread owner = Thread.currentThread();
    /** 仅在 open 返回前初始化，之后不再改变。 */
    private C capabilities;
    private Scope active;
    private boolean closed;

    private LocalAudioDevice(Driver<C> driver, long device, long context) {
        this.driver = driver;
        this.device = device;
        this.context = context;
    }

    static LocalAudioDevice<ALCapabilities> open() throws IOException { return open(new OpenAlDriver()); }

    static <C> LocalAudioDevice<C> open(Driver<C> driver) throws IOException {
        long device = driver.openDevice();
        if (device == 0) throw new IOException("无法打开本地音频设备");
        long context = 0;
        try {
            // 不退回全进程切换：本模组无法给原版及其他模组的声音线程加锁。
            if (!driver.supportsThreadContext(device)) throw new IOException("音频设备不支持线程局部上下文");
            context = driver.createContext(device);
            if (context == 0) throw new IOException("无法创建本地音频上下文");
            var result = new LocalAudioDevice<>(driver,device,context);
            try (var ignored = result.enter()) {
                result.capabilities = driver.createCapabilities(device);
            }
            return result;
        } catch (IOException | RuntimeException | Error failure) {
            // 初始化及作用域恢复失败都不能漏掉半开的设备；Error 回收后原样抛出。
            if (context != 0) {
                long allocated = context;
                cleanup(failure,() -> {
                    if (driver.threadContext() == allocated) {
                        if (!driver.bindThread(0)) throw new IllegalStateException("无法解除失败的音频上下文");
                        driver.capabilities(null);
                    }
                    driver.destroyContext(allocated);
                });
            }
            cleanup(failure,() -> driver.closeDevice(device));
            throw failure;
        }
    }

    Scope enter() {
        requireOwner();
        if (closed) throw new IllegalStateException("本地音频设备已关闭");
        return new Scope();
    }

    final class Scope implements AutoCloseable {
        private final long previous = driver.threadContext();
        // previous=0 时应恢复全局回退，不能把旧的进程能力表固定在线程上。
        private final C previousCaps = previous == 0 ? null : driver.capabilities();
        private final Scope parent = active;
        private boolean restored;

        private Scope() {
            if (!driver.bindThread(context)) throw new IllegalStateException("无法绑定本地音频上下文");
            try {
                driver.capabilities(capabilities);
            } catch (RuntimeException | Error failure) {
                cleanup(failure,this::restore);
                throw failure;
            }
            active = this;
        }

        private void restore() {
            if (!driver.bindThread(previous)) throw new IllegalStateException("无法恢复线程音频上下文");
            driver.capabilities(previousCaps);
        }

        @Override public void close() {
            requireOwner();
            if (restored) return;
            if (active != this) throw new IllegalStateException("音频作用域必须按嵌套顺序关闭");
            try { restore(); }
            finally { active = parent; restored = true; }
        }
    }

    @Override public void close() {
        requireOwner();
        if (closed) return;
        if (active != null) throw new IllegalStateException("音频作用域尚未关闭");
        closed = true;
        try {
            if (driver.threadContext() == context) {
                if (!driver.bindThread(0)) throw new IllegalStateException("无法解除本地音频上下文");
                driver.capabilities(null);
            }
            driver.destroyContext(context);
        } catch (RuntimeException | Error failure) {
            cleanup(failure,() -> driver.closeDevice(device));
            throw failure;
        }
        driver.closeDevice(device);
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("本地音频只能由所属客户端线程操作");
    }

    private static void cleanup(Throwable failure, Runnable action) {
        try { action.run(); }
        catch (RuntimeException | Error cleanupFailure) {
            if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
        }
    }

    private static final class OpenAlDriver implements Driver<ALCapabilities> {
        private ALCCapabilities deviceCaps;
        public long openDevice() { return ALC10.alcOpenDevice((ByteBuffer)null); }
        public boolean supportsThreadContext(long device) {
            deviceCaps = ALC.createCapabilities(device);
            return deviceCaps.ALC_EXT_thread_local_context;
        }
        public long createContext(long device) {
            return deviceCaps.ALC_SOFT_HRTF ? ALC10.alcCreateContext(device,new int[]{SOFTHRTF.ALC_HRTF_SOFT,ALC10.ALC_FALSE,0})
                    : ALC10.alcCreateContext(device,(IntBuffer)null);
        }
        public long threadContext() { return EXTThreadLocalContext.alcGetThreadContext(); }
        public ALCapabilities capabilities() { return AL.getCapabilities(); }
        public boolean bindThread(long context) { return EXTThreadLocalContext.alcSetThreadContext(context); }
        public void capabilities(ALCapabilities value) { AL.setCurrentThread(value); }
        public ALCapabilities createCapabilities(long device) { return AL.createCapabilities(deviceCaps); }
        public void destroyContext(long context) { ALC10.alcDestroyContext(context); }
        public void closeDevice(long device) {
            if (!ALC10.alcCloseDevice(device)) throw new IllegalStateException("本地音频设备关闭失败");
        }
    }
}
