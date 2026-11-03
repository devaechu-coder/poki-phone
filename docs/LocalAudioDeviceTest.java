package com.november.mcphone.feature.music.client.playback;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/** 上下文/能力表配对、嵌套、线程归属与失败回收；不依赖本机声卡或 OpenAL 原生库。 */
public final class LocalAudioDeviceTest {
    private static int checks;
    private static void check(boolean value,String why) { checks++;if(!value)throw new AssertionError(why); }
    private interface Action { void run() throws Exception; }
    private static Throwable fails(Action action) {
        try { action.run(); } catch(Throwable failure) { return failure; }
        throw new AssertionError("预期失败");
    }
    public static void main(String[] args) throws Exception {
        lifecycle(); previousThreadContext(); failures(); ownership(); noGlobalContextApi();
        System.out.println("Local audio device assertions: "+checks);
    }
    private static void lifecycle() throws Exception {
        var driver = new Driver();var device = LocalAudioDevice.open(driver);
        check(driver.current==0&&driver.caps==null,"初始化后保留进程上下文回退，不能把游戏能力表钉在线程上");
        var first=device.enter();check(driver.current==2&&"music".equals(driver.caps),"本地句柄与能力表成对绑定");
        var nested=device.enter();check(fails(first::close) instanceof IllegalStateException,"错误嵌套顺序拒绝恢复");
        check(fails(device::close) instanceof IllegalStateException,"仍有使用者时不能销毁上下文");
        nested.close();check(driver.current==2&&"music".equals(driver.caps),"嵌套恢复外层音乐上下文");
        first.close();first.close();check(driver.current==0&&driver.caps==null,"离开后恢复线程默认回退，重复关闭安全");
        device.close();device.close();check(driver.destroyed==1&&driver.closed==1,"设备和上下文只关闭一次");
        check(fails(device::enter) instanceof IllegalStateException,"已关闭设备不能再分配作用域");
    }
    private static void previousThreadContext() throws Exception {
        var driver=new Driver();driver.current=9;driver.caps="other";
        try(var device=LocalAudioDevice.open(driver)) {
            check(driver.current==9&&"other".equals(driver.caps),"初始化也保留其他模组的线程上下文");
            try(var scope=device.enter()) { check(driver.current==2,"音乐使用自己的上下文"); }
            check(driver.current==9&&"other".equals(driver.caps),"恢复真正的先前线程上下文及能力表");
        }
        check(driver.current==9&&"other".equals(driver.caps),"关闭音乐设备不解除其他线程上下文");
    }
    private static void failures() throws Exception {
        for(String phase:new String[]{"open","extension","context","bind","capabilities","fatal"}) {
            var driver=new Driver();driver.failure=phase;
            var error=fails(()->LocalAudioDevice.open(driver));
            check(phase.equals("fatal")?error==driver.fatal:error instanceof IOException||error instanceof IllegalStateException,"启动失败原样交给上层: "+phase);
            check(driver.closed==(phase.equals("open")?0:1),"失败不漏设备: "+phase);
            check(driver.destroyed==((phase.equals("bind")||phase.equals("capabilities")||phase.equals("fatal"))?1:0),"只销毁已成功分配的上下文: "+phase);
            check(driver.current==0&&driver.caps==null,"启动失败不遗留线程状态: "+phase);
        }
        var driver=new Driver();var device=LocalAudioDevice.open(driver);
        driver.current=9;driver.caps="other";var scope=device.enter();driver.refuse=9;
        check(fails(scope::close) instanceof IllegalStateException,"恢复失败必须显式报告");
        device.close();check(driver.current==0&&driver.caps==null&&driver.closed==1,"恢复失败后仍可清理自有上下文");
        driver=new Driver();device=LocalAudioDevice.open(driver);driver.cleanupFailure=true;
        var error=fails(device::close);
        check(driver.destroyed==1&&driver.closed==1,"销毁异常仍尝试关闭设备");
        check(error.getSuppressed().length==1,"保留主错误并附带关闭设备错误");
    }
    private static void ownership() throws Exception {
        var driver=new Driver();var device=LocalAudioDevice.open(driver);
        var error=new AtomicReference<Throwable>();
        var worker=new Thread(()->error.set(fails(device::enter)));worker.start();worker.join();
        check(error.get() instanceof IllegalStateException,"其他线程不能借用客户端设备");
        worker=new Thread(()->error.set(fails(device::close)));worker.start();worker.join();
        check(error.get() instanceof IllegalStateException&&driver.closed==0,"错误线程不能销毁设备");
        device.close();
    }
    private static void noGlobalContextApi() throws Exception {
        try(var input=LocalAudioDeviceTest.class.getResourceAsStream("LocalAudioDevice$OpenAlDriver.class")) {
            check(input!=null,"检查真正编译的原生驱动");
            var constants=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.ISO_8859_1);
            check(!constants.contains("alcMakeContextCurrent")&&!constants.contains("setCurrentProcess"),"禁止重新引入全进程上下文切换");
            check(!constants.contains("com/mojang/blaze3d/audio/Library"),"设备初始化也不借原版 Library");
        }
    }
    private static final class Driver implements LocalAudioDevice.Driver<String> {
        String failure="",caps;
        long current,refuse=-1;
        int closed,destroyed;
        boolean cleanupFailure;
        final Error fatal=new AssertionError("模拟致命错误");
        public long openDevice() { return failure.equals("open")?0:1; }
        public boolean supportsThreadContext(long device) { return !failure.equals("extension"); }
        public long createContext(long device) { return failure.equals("context")?0:2; }
        public long threadContext() { return current; }
        public String capabilities() { return caps; }
        public boolean bindThread(long context) {
            if(context==refuse||failure.equals("bind")&&context==2)return false;
            current=context;return true;
        }
        public void capabilities(String value) { caps=value; }
        public String createCapabilities(long device) {
            check(current==2,"能力表只能在自有上下文中创建");
            if(failure.equals("capabilities"))throw new IllegalStateException("模拟初始化错误");
            if(failure.equals("fatal"))throw fatal;
            caps="music";return caps;
        }
        public void destroyContext(long context) { destroyed++;if(cleanupFailure)throw new IllegalStateException("销毁失败"); }
        public void closeDevice(long device) { closed++;if(cleanupFailure)throw new IllegalStateException("关闭失败"); }
    }
}
