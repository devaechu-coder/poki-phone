package com.november.mcphone.feature.chat.client;

import java.util.Map;
import java.util.UUID;

/** 实际缓存的无 GL 路径：空条目也有容量上限，被逐出的解码回调不能重新创建条目。 */
public final class ChatImageCacheBoundsTest {
    private static int checks;
    private static void check(boolean ok,String why) { checks++;if(!ok)throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        var field=ChatImageCache.class.getDeclaredField("ENTRIES");field.setAccessible(true);
        var entries=(Map<?,?>)field.get(null);
        ChatImageCache.clear();
        UUID old=new UUID(0,0),recent=new UUID(0,999);
        ChatImageCache.declare(old,1,0);Object oldOwner=entries.get(old);
        for(int i=0;i<1000;i++) ChatImageCache.declare(new UUID(0,i),1,0);
        check(entries.size()==ChatImageCache.MAX_ENTRIES,"加载中条目不会无限增长");
        check(!entries.containsKey(old)&&entries.containsKey(recent),"逐出最旧条目并保留最近条目");
        for(int i=1000;i<2000;i++) ChatImageCache.accept(new UUID(0,i),new byte[0]);
        check(entries.size()==ChatImageCache.MAX_ENTRIES,"过期图片条目同样受上限约束");
        check(ChatImageCache.status(new UUID(0,1999))==ChatImageCache.Status.GONE,"最新过期状态正确保留");
        var generation=ChatImageCache.class.getDeclaredField("generation");generation.setAccessible(true);
        var install=ChatImageCache.class.getDeclaredMethod("install",UUID.class,int.class,oldOwner.getClass(),com.mojang.blaze3d.platform.NativeImage.class);
        install.setAccessible(true);install.invoke(null,old,generation.getInt(null),oldOwner,null);
        check(!entries.containsKey(old),"被逐出的后台结果不能复活旧条目");
        ChatImageCache.declare(old,1,0);Object newOwner=entries.get(old);
        install.invoke(null,old,generation.getInt(null),oldOwner,null);
        check(ChatImageCache.status(old)==ChatImageCache.Status.LOADING,"同 ID 重建后旧解码也不能污染新条目");
        install.invoke(null,old,generation.getInt(null),newOwner,null);
        check(ChatImageCache.status(old)==ChatImageCache.Status.BROKEN,"当前条目的解码失败仍正常落状态");
        ChatImageCache.clear();check(entries.isEmpty(),"断线释放全部元数据");
        System.out.println("全部通过："+checks+" 条断言");
    }
}
