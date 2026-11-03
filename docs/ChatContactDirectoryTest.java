package com.november.mcphone.feature.chat.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** 使用实际 Minecraft ICU 依赖，覆盖中文首字母、名称过滤和相同名称的稳定次序。 */
public final class ChatContactDirectoryTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static ChatContactDirectory.Contact contact(int n, String name) {
        return new ChatContactDirectory.Contact(new UUID(0, n), name, n % 2 == 0);
    }
    public static void main(String[] args) {
        var directory = new ChatContactDirectory();
        for (String[] pair : new String[][]{{"alice", "A"}, {"Bob", "B"}, {"张三", "Z"}, {"李四", "L"},
                {"阿雨", "A"}, {"艾尔潘", "A"}, {"十一月", "S"}, {"猫猫", "M"}, {"Émile", "E"},
                {" Ａlice ", "A"}, {"123", "#"}, {"😊", "#"}, {"", "#"}, {"_alice", "#"}}) {
            check(directory.letter(pair[0]).equals(pair[1]), "首字母：" + pair[0]);
        }
        var source = new ArrayList<>(List.of(contact(9, "张三"), contact(4, "Bob"), contact(5, "alice"),
                contact(6, "Aaron"), contact(7, "123"), contact(8, "李四")));
        var original = List.copyOf(source);
        var sections = directory.sections(source, "");
        check(sections.stream().map(ChatContactDirectory.Section::letter).toList().equals(List.of("A", "B", "L", "Z", "#")), "按 A-Z 后 # 分组");
        check(sections.get(0).contacts().stream().map(ChatContactDirectory.Contact::name).toList().equals(List.of("Aaron", "alice")), "组内按名称排列");
        check(source.equals(original), "联系人排序不会改变会话缓存的时间排序");
        check(sections.stream().flatMap(s -> s.contacts().stream()).count() == 6, "离线和未聊天好友不会漏掉");
        check(directory.sections(source, " ALI ").get(0).contacts().get(0).name().equals("alice"), "搜索忽略大小写和首尾空白");
        check(directory.sections(source, "张").get(0).letter().equals("Z"), "中文搜索保持真实分组");
        check(directory.sections(source, "不存在").isEmpty(), "无匹配没有假分组");
        check(directory.sections(source, "ＢＯＢ").get(0).contacts().get(0).name().equals("Bob"), "搜索全角兼容");
        try { sections.clear(); throw new AssertionError("目录必须不可变"); } catch (UnsupportedOperationException expected) { checks++; }
        try { sections.get(0).contacts().clear(); throw new AssertionError("分组必须不可变"); } catch (UnsupportedOperationException expected) { checks++; }
        var same = directory.sections(List.of(contact(2, "Alice"), contact(1, "Alice")), "");
        check(same.get(0).contacts().get(0).id().equals(new UUID(0, 1)), "同名用 UUID 保证稳定次序");
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            check(new ChatContactDirectory().sections(source, "ALI").equals(directory.sections(source, "ali")), "系统语言不改变拼音排序和搜索");
        } finally { Locale.setDefault(old); }
        source.clear(); check(sections.size() == 5, "源数据变化不污染已生成快照");
        System.out.println("全部通过：" + checks + " 条断言");
    }
}
