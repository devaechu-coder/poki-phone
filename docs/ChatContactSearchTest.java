package com.november.mcphone.feature.chat.client;

import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** 搜索使用真实 EditBox 的输入与长度规则；测试不接触系统输入法或剪贴板。 */
public final class ChatContactSearchTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static void field(Object owner, String name, Object value) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); f.set(owner, value);
    }
    private static final class TestFont extends com.november.mcphone.test.TestFontBase {
        private final StringSplitter splitter = new StringSplitter((cp, style) -> 6);
        @Override public StringSplitter getSplitter() { return splitter; }
        @Override public int width(String text) { return (int) Math.ceil(splitter.stringWidth(text)); }
        @Override public int width(FormattedText text) { return (int) Math.ceil(splitter.stringWidth(text)); }
        @Override public int width(FormattedCharSequence text) { return (int) Math.ceil(splitter.stringWidth(text)); }
        @Override public String plainSubstrByWidth(String text, int width) { return splitter.plainHeadByWidth(text, width, Style.EMPTY); }
        @Override public String plainSubstrByWidth(String text, int width, boolean tail) {
            return tail ? splitter.plainTailByWidth(text, width, Style.EMPTY) : plainSubstrByWidth(text, width);
        }
    }
    public static void main(String[] args) throws Exception {
        var search = new ChatContactSearch();
        check(search.query().isEmpty() && !search.focused() && !search.charTyped('e', 0), "未绘制时没有输入焦点");
        var box = com.november.mcphone.test.TestEditBoxFactory.create(new TestFont(), 0, 0, 100, 9, Component.empty());
        box.setMaxLength(64); field(search, "box", box);
        box.setFocused(true);
        check(search.charTyped('e', 0) && search.query().equals("e"), "搜索输入 e 不触发背包导航");
        check(search.charTyped('张', 0) && search.query().equals("e张"), "中文提交进入真实控件");
        check(search.keyPressed(999, 0, 0), "搜索握有焦点时消费宿主按键，即使控件不认识");
        box.setValue("Alice"); box.setCursorPosition(2); box.setHighlightPos(5);
        check(search.charTyped('X', 0) && search.query().equals("AlX"), "字符替换真实选区");
        box.setValue("x".repeat(64)); search.charTyped('a', 0);
        check(search.query().length() == 64, "搜索长度受控");
        search.mouseClicked(-10, -10, 0); check(!search.focused(), "点其他区域释放搜索焦点");
        check(!search.charTyped('a', 0) && !search.keyPressed(999, 0, 0), "失焦交回宿主输入");
        search.close(); check(search.query().length() == 64, "临时占位页只失焦，不丢搜索");
        search.reset(); check(search.query().isEmpty() && !search.focused(), "重进联系人清理旧搜索和焦点");
        System.out.println("全部通过：" + checks + " 条断言");
    }
}
