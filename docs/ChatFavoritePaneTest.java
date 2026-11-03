package com.november.mcphone.feature.chat.client;

import com.november.mcphone.feature.chat.client.favorite.*;
import com.november.mcphone.feature.chat.group.GroupMember;
import java.nio.file.*;
import java.util.*;

/** 驱动生产点击方法，验证完整复制、移除结果、失败保护及头像入口的裁剪命中。 */
public final class ChatFavoritePaneTest {
    private static int checks;
    private static void check(boolean ok,String why) { checks++;if (!ok) throw new AssertionError(why); }
    private static void field(Object o,String name,Object value) throws Exception { var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value); }
    private static Object field(Object o,String name) throws Exception { var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o); }
    private static Object hit(FavoriteEntry entry) throws Exception {
        var constructor=Class.forName("com.november.mcphone.feature.chat.client.ChatFavoritePane$Hit").getDeclaredConstructor(ChatLayout.Rect.class,FavoriteEntry.class);
        constructor.setAccessible(true);return constructor.newInstance(new ChatLayout.Rect(10,22,100,25),entry);
    }
    private static void buttons(ChatFavoritePane pane) throws Exception {
        field(pane,"area",new ChatLayout.Rect(10,20,100,60));
        field(pane,"copyButton",new ChatLayout.Rect(10,62,45,14));field(pane,"removeButton",new ChatLayout.Rect(58,62,52,14));
    }
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("mcphone-favorite-ui-");UUID a=new UUID(0,1),b=new UUID(0,2);
        try {
            var original=new FavoriteText(a,b,false,a,"Player","Friend","完整正文\n第二行😀",100,200);
            var entry=FavoriteEntry.text(original);
            var file=dir.resolve("favorites.json");var store=new FavoriteStore(file);store.add(original);
            var copied=new ArrayList<String>();var pane=new ChatFavoritePane(()->store,copied::add,Runnable::run,Runnable::run);
            field(pane,"area",new ChatLayout.Rect(10,20,100,60));field(pane,"hits",List.of(hit(entry)));
            check(!pane.click(0,0,0)&&field(pane,"selected")==null,"列表外不选择");
            check(!pane.click(20,30,1)&&field(pane,"selected")==null,"右键不打开收藏正文");
            check(pane.click(20,30,0)&&field(pane,"selected").equals(entry),"实际行点击打开对应不可变快照");
            buttons(pane);pane.click(20,64,0);
            check(copied.isEmpty(),"正文尚未加载时不复制占位提示");
            check(field(pane,"status").equals(""),"读取状态由正文加载状态决定，不留下永久的读取中提示");
            field(pane,"selectedText",store.text(entry));
            buttons(pane);pane.click(20,64,0);check(copied.equals(List.of(original.text())),"实际复制按钮保留完整正文与换行");
            check(store.snapshot().size()==1,"复制不改变收藏");pane.click(99,64,0);
            check(field(pane,"selected")==null&&store.snapshot().isEmpty(),"成功移除返回列表");
            check(new FavoriteStore(file).snapshot().isEmpty(),"按钮移除真实落盘");pane.click(20,64,0);check(copied.size()==1,"移除后旧复制按钮不触发");
            store.add(original);var failing=new FavoriteStore(file,(p,j)->{ throw new java.io.IOException("模拟失败"); });
            var failed=new ChatFavoritePane(()->failing,copied::add,Runnable::run,Runnable::run);field(failed,"selected",entry);buttons(failed);failed.click(99,64,0);
            check(field(failed,"selected").equals(entry)&&failing.snapshot().size()==1,"移除失败保留正文和收藏");
            check(field(failed,"status").equals("mcphone.chat.favorite.storage_error"),"失败有可见提示");
            field(failed,"textMaximum",100);check(!failed.scroll(0,0,-1),"正文外滚轮不滚动");failed.scroll(20,40,-1);
            check((int)field(failed,"textOffset")==12,"长收藏正文支持滚动");check(failed.back()&&field(failed,"selected")==null,"正文返回列表");
            failed.close();check(!failed.click(99,64,0),"关闭后清理旧点击区");

            // 实际图片行点击和异步移除；旧操作失败不能污染后来选中的另一条。
            var pngBytes=new java.io.ByteArrayOutputStream();
            var png=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            javax.imageio.ImageIO.write(png,"PNG",pngBytes);png.flush();byte[] data=pngBytes.toByteArray();
            var image=new FavoriteEntry(new UUID(0,3),b,true,a,"Player",100,201,FavoriteEntry.Kind.IMAGE,data.length,2,2,1,0);
            check(store.add(image,data)==FavoriteStore.Result.OK,"准备真实图片收藏");
            var imagePane=new ChatFavoritePane(()->store,copied::add,Runnable::run,Runnable::run);
            field(imagePane,"area",new ChatLayout.Rect(10,20,100,60));field(imagePane,"hits",List.of(hit(image)));
            imagePane.click(20,30,0);check(field(imagePane,"selected").equals(image),"图片行进入对应详情");
            buttons(imagePane);imagePane.click(99,64,0);
            check(field(imagePane,"selected")==null && !Files.exists(store.contentPath(image)),"图片取消收藏清理文件并返回列表");
            var work=new ArrayDeque<Runnable>();
            var delayed=new ChatFavoritePane(()->failing,copied::add,work::add,Runnable::run);
            field(delayed,"selected",entry);buttons(delayed);delayed.click(99,64,0);
            check((boolean)field(delayed,"removing") && work.size()==1,"取消收藏在后台排队");
            delayed.back();field(delayed,"selected",image);
            while(!work.isEmpty()) work.remove().run();
            check(field(delayed,"status").equals("") && field(delayed,"selected").equals(image),"旧条目移除失败不修改新详情状态");
            delayed.close();imagePane.close();
        } finally { try(var paths=Files.walk(dir)) { for(var p:paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); } }

        var pane=new ChatContactPane();field(pane,"bounds",new ChatLayout.Rect(10,20,100,30));
        var scroll=(ChatContactScroll)field(pane,"scroll");scroll.update(List.of(new ChatContactDirectory.Section("A",List.of(
                new ChatContactDirectory.Contact(a,"A",true),new ChatContactDirectory.Contact(b,"B",false)))),30);
        check(pane.avatarAt(14,22,0)==null,"分组标题没有资料入口");check(a.equals(pane.avatarAt(14,34,0)),"好友方形头像打开对应资料");
        check(pane.avatarAt(30,34,0)==null&&a.equals(pane.click(30,34,0)),"姓名区继续打开会话");
        check(pane.avatarAt(14,34,1)==null,"头像右键不导航");pane.mouseScrolled(14,35,-1);
        check(a.equals(pane.avatarAt(14,21,0))&&b.equals(pane.avatarAt(14,35,0)),"滚动后的裁剪头像与新行对齐");
        check(pane.avatarAt(14,19,0)==null,"裁剪区外不命中未显示头像");pane.close();check(pane.avatarAt(14,35,0)==null,"联系人关闭后清除头像入口");

        var grid=new ChatGroupMemberGrid();field(grid,"renderedMembers",List.of(new GroupMember(a,"A")));
        field(grid,"cells",List.of(new ChatGroupMemberGrid.Cell(0,new ChatLayout.Rect(10,20,24,28)),new ChatGroupMemberGrid.Cell(-1,new ChatLayout.Rect(40,20,24,28))));
        check(a.equals(grid.memberClicked(20,30,0)),"群成员使用已绘制快照 UUID");check(grid.memberClicked(50,30,0)==null&&grid.inviteClicked(50,30,0),"邀请格不被当成成员资料");
        grid.close();check(grid.memberClicked(20,30,0)==null,"网格关闭清理资料入口");
        System.out.println("全部通过："+checks+" 条断言");
    }
}
