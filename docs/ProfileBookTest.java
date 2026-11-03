package com.november.mcphone.feature.chat.profile;
import java.util.Map;
import java.util.UUID;
public final class ProfileBookTest {
    private static int checks;
    private static void check(boolean ok,String why) { checks++;if (!ok) throw new AssertionError(why); }
    private static void rejects(Runnable operation,String why) { boolean rejected=false;try { operation.run(); } catch (RuntimeException e) { rejected=true; } check(rejected,why); }
    public static void main(String[] args) {
        UUID a=new UUID(0,1),b=new UUID(0,2);var book=new ProfileBook();
        check(book.get(a).isEmpty(),"新玩家没有介绍");
        rejects(()->book.update(a,b,"冒充"),"认证身份不能编辑别人");check(book.get(b).isEmpty(),"冒充失败不改变存档");
        check(book.update(a,a,"  你好§a\n世界\u0000  "),"有效介绍标记修改");check(book.get(a).equals("你好a世界"),"格式符控制字符去除并收尾空白");
        check(!book.update(a,a,"你好a世界"),"未变化不重复写存档");
        var frozen=book.snapshot();check(book.update(a,a,"新内容"),"正常修改");check(frozen.get(a).equals("你好a世界"),"存档快照不可变");
        var restored=ProfileBook.restore(book.snapshot());check(restored.get(a).equals("新内容"),"重载介绍");
        check(book.update(a,a," "),"可以清空介绍");check(book.snapshot().isEmpty(),"空介绍不占用容量");
        rejects(()->book.update(a,a,"x".repeat(ProfileBook.MAX_INTRO+1)),"超长拒绝而非静默截断");
        rejects(()->ProfileBook.restore(Map.of(a,"x".repeat(ProfileBook.MAX_INTRO+1))),"损坏值阻止恢复");
        check(restored.get(a).equals("新内容"),"后续改动不会污染重载副本");
        System.out.println("全部通过："+checks+" 条断言");
    }
}
