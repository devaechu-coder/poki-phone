package com.november.mcphone.feature.music.client.playback;

import org.lwjgl.openal.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** 手动原生回归。独立 JVM 配合 ALSOFT_DRIVERS=null，不启动游戏、不使用玩家声卡。 */
public final class NativeAudioProbe {
    private static int checks;
    private static void check(boolean value,String why) { checks++;if(!value)throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        try(var world=new World()) {
            int worldSource=AL10.alGenSources();
            // 重现旧设计：两上下文编号相同，后台线程按原版编号删除时误删音乐声源。
            try(var oldMusic=new World()) {
                int musicSource=AL10.alGenSources();
                check(musicSource==worldSource,"OpenAL Soft 的两个独立上下文复用了相同编号");
                var failure=new AtomicReference<Throwable>();
                var worker=new Thread(()->{
                    try { AL10.alDeleteSources(worldSource); }
                    catch(Throwable t) { failure.set(t); }
                });worker.start();worker.join(5000);
                check(!worker.isAlive()&&failure.get()==null,"模拟原版后台线程已执行");
                AL10.alGetSourcei(musicSource,AL10.AL_BUFFERS_PROCESSED);
                int error=AL10.alGetError();
                check(error==AL10.AL_INVALID_NAME,"旧设计确定复现日志中的 0xa001");
                System.out.println("Old process context race reproduced: 0x"+Integer.toHexString(error));
            }
            world.activate();
            check(AL10.alIsSource(worldSource),"旧竞态删掉的是音乐声源，原版声源仍存在");
            try(var music=LocalAudioDevice.open()) {
                check(ALC10.alcGetCurrentContext()==world.context,"初始化没有切换原版进程上下文");
                try(var scope=music.enter()) {
                    int musicSource=AL10.alGenSources();
                    check(EXTThreadLocalContext.alcGetThreadContext()!=0,"音乐操作绑定当前线程的独立上下文");
                    var failure=new AtomicReference<Throwable>();
                    var start=new CountDownLatch(1);var done=new CountDownLatch(1);
                    var worker=new Thread(()->{
                        try {
                            check(start.await(5,TimeUnit.SECONDS),"并发回归按时启动");
                            check(ALC10.alcGetCurrentContext()==world.context,"后台线程仍使用原版进程上下文");
                            AL10.alDeleteSources(worldSource);
                            for(int i=0;i<10000;i++) {
                                int source=AL10.alGenSources();AL10.alDeleteSources(source);
                                if(AL10.alGetError()!=AL10.AL_NO_ERROR)throw new AssertionError("原版线程发生 AL 错误");
                            }
                        }catch(Throwable t){failure.set(t);}finally{done.countDown();}
                    });worker.start();start.countDown();
                    for(int i=0;i<10000;i++) {
                        if(!AL10.alIsSource(musicSource))throw new AssertionError("音乐声源被后台线程误删");
                        AL10.alGetSourcei(musicSource,AL10.AL_BUFFERS_PROCESSED);
                        if(AL10.alGetError()!=AL10.AL_NO_ERROR)throw new AssertionError("音乐线程发生 AL 错误");
                    }
                    check(done.await(10,TimeUnit.SECONDS)&&failure.get()==null,"两线程各一万次原生操作没有串上下文");
                    AL10.alDeleteSources(musicSource);
                }
                check(EXTThreadLocalContext.alcGetThreadContext()==0,"音乐作用域恢复线程默认回退");
            }
            check(ALC10.alcGetCurrentContext()==world.context,"关闭音乐不销毁原版上下文");
            check(AL10.alGetError()==AL10.AL_NO_ERROR,"最终原版上下文没有残留错误");
        }
        System.out.println("Native audio assertions: "+checks);
    }
    /** 只在手动回归中模拟旧版原版设备，生产代码不使用这种全局切换方式。 */
    private static final class World implements AutoCloseable {
        final long device=ALC10.alcOpenDevice((ByteBuffer)null);
        final long context;
        final ALCapabilities caps;
        World() {
            if(device==0)throw new IllegalStateException("请确认 OpenAL Soft null 驱动可用");
            var deviceCaps=ALC.createCapabilities(device);
            context=ALC10.alcCreateContext(device,(IntBuffer)null);
            if(context==0||!ALC10.alcMakeContextCurrent(context))throw new IllegalStateException("测试上下文创建失败");
            caps=AL.createCapabilities(deviceCaps);
        }
        void activate() { ALC10.alcMakeContextCurrent(context);AL.setCurrentProcess(caps); }
        public void close() {
            ALC10.alcMakeContextCurrent(0);AL.setCurrentProcess(null);
            ALC10.alcDestroyContext(context);ALC10.alcCloseDevice(device);
        }
    }
}
