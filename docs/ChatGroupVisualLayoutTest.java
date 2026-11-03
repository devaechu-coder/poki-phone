package com.november.mcphone.feature.chat.client;

import com.november.mcphone.feature.chat.ChatMessage;
import com.november.mcphone.feature.chat.ImageBody;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** 验证实际群头像几何、收发昵称、排版缓存和移位后的选字/右键命中；不启动游戏窗口。 */
public final class ChatGroupVisualLayoutTest {
    private static int checks;
    private static final UUID SELF = new UUID(0, 1), PEER = new UUID(0, 2);
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static boolean near(float a, float b) { return Math.abs(a - b) < 0.0001f; }
    private static Object field(Object o, String name) throws Exception { var f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); }
    private static void field(Object o, String name, Object value) throws Exception { var f = o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o, value); }
    private static Object call(Object o, String name, Class<?>[] types, Object... args) throws Exception {
        var method = o.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(o, args);
    }
    private static final class TestFont extends com.november.mcphone.test.TestFontBase {
        private final StringSplitter splitter = new StringSplitter((cp, style) -> cp > 255 ? 9 : cp == 'i' ? 2 : 6);
        @Override public StringSplitter getSplitter() { return splitter; }
        @Override public int width(String text) { return (int) Math.ceil(splitter.stringWidth(text)); }
        @Override public int width(FormattedText text) { return (int) Math.ceil(splitter.stringWidth(text)); }
        @Override public int width(FormattedCharSequence text) { return (int) Math.ceil(splitter.stringWidth(text)); }
        @Override public String plainSubstrByWidth(String text, int width) { return splitter.plainHeadByWidth(text, width, Style.EMPTY); }
        @Override public String plainSubstrByWidth(String text, int width, boolean tail) {
            return tail ? splitter.plainTailByWidth(text, width, Style.EMPTY) : plainSubstrByWidth(text, width);
        }
        @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
            return splitter.splitLines(text, width, Style.EMPTY).stream()
                    .map(line -> FormattedCharSequence.forward(line.getString(), Style.EMPTY)).toList();
        }
    }
    private static ChatMessagePane pane(Font font, List<ChatMessage> source, boolean group) throws Exception {
        var pane = new ChatMessagePane(); field(pane, "selfId", SELF); field(pane, "group", group);
        field(pane, "senderName", (java.util.function.Function<ChatMessage, String>) m -> m.sender().equals(SELF) ? "LocalPlayer自己" : "TamsinPax");
        field(pane, "messageTop", 0); field(pane, "messageBottom", 200);
        call(pane, "relayout", new Class<?>[]{Font.class, int.class, List.class, boolean.class}, font, 112, source, group);
        return pane;
    }
    private static Object nickname(ChatMessagePane pane, Font font, ChatMessageLayout.Block block, int x, int y) throws Exception {
        return call(pane, "nickname", new Class<?>[]{Font.class, ChatMessageLayout.Block.class, int.class, int.class, int.class}, font, block, x, y, 112);
    }
    private static void hits(ChatMessagePane pane, int y, Font font) throws Exception {
        call(pane, "layoutHits", new Class<?>[]{int.class, int.class, int.class, int.class}, 10, y, 112, ChatUi.lineHeight(font, ChatLayout.TEXT_SCALE));
    }
    public static void main(String[] args) throws Exception {
        check(ChatGroupAvatar.layout(0, 12).isEmpty(), "没有资料时不绘制虚构成员");
        for (int size : new int[]{12, 16, 24}) {
            for (int count = 1; count <= 4; count++) {
                var tiles = ChatGroupAvatar.layout(count, size);
                check(tiles.size() == count, "头像数量与成员预览一致");
                float left = Float.MAX_VALUE, top = Float.MAX_VALUE, right = 0, bottom = 0;
                for (var tile : tiles) {
                    check(tile.x() >= 0 && tile.y() >= 0 && tile.size() > 0
                            && tile.x() + tile.size() <= size && tile.y() + tile.size() <= size, "头像留在方形容器内");
                    left = Math.min(left, tile.x()); top = Math.min(top, tile.y());
                    right = Math.max(right, tile.x() + tile.size()); bottom = Math.max(bottom, tile.y() + tile.size());
                }
                check(near(left + right, size) && near(top + bottom, size), "组合整体水平、垂直居中");
                for (int i = 0; i < count; i++) for (int j = i + 1; j < count; j++) {
                    var a = tiles.get(i); var b = tiles.get(j);
                    check(a.x() + a.size() <= b.x() || b.x() + b.size() <= a.x()
                            || a.y() + a.size() <= b.y() || b.y() + b.size() <= a.y(), "各成员头像不重叠");
                }
                if (count == 2) check(tiles.get(0).y() == tiles.get(1).y(), "双人头像横排而不是占四宫格上半部");
                if (count == 3) check(tiles.get(0).y() < tiles.get(1).y() && tiles.get(1).y() == tiles.get(2).y()
                        && near(tiles.get(0).x() + tiles.get(0).size() / 2f, size / 2f), "三人头像上单下双且顶部居中");
            }
        }
        check(ChatLayout.GROUP_NAME_SCALE < ChatLayout.META_SCALE && ChatLayout.GROUP_NAME_SCALE < ChatLayout.TEXT_SCALE, "群昵称小于旧昵称和正文");
        Font font = new TestFont();
        var src = List.of(ChatMessage.text(PEER, "你好", 1), ChatMessage.text(SELF, "你好", 2),
                ChatMessage.text(PEER, "第一行\n第二行，换行仍然左对齐", 3),
                new ChatMessage(SELF, 4, new ImageBody(new UUID(0, 3), 32, 56, 2, 100)));
        var group = ChatMessageLayout.build(font, src, 112, SELF, true);
        var direct = ChatMessageLayout.build(font, src, 112, SELF);
        var pane = pane(font, src, true);
        for (int i = 0; i < group.size(); i++) {
            var block = group.get(i); var old = direct.get(i);
            check(block.placement().avatarY() == 0, "群头像从消息顶部开始，不再在昵称下方");
            check(block.placement().contentY() > 0 && block.rowHeight() >= block.h() + block.placement().contentY(), "正文在昵称下方且不超出消息行");
            check(block.w() == old.w() && block.h() == old.h() && Objects.equals(block.textOrigin(), old.textOrigin()), "重构构图不改变正文尺寸及光学校准");
            var name = nickname(pane, font, block, 10, 20);
            check(name != null, "文字、图片、己方和对方都绘制昵称");
            String text = (String) field(name, "text"); float nx = (float) field(name, "x"), ny = (float) field(name, "y");
            int bx = 10 + ChatLayout.messageContentX(112, block.w(), block.self());
            check(block.self() ? near(nx + font.width(text) * ChatLayout.GROUP_NAME_SCALE, bx + block.w()) : near(nx, bx), "己方昵称靠气泡右边，对方昵称靠左边");
            float gap = 20 + block.placement().contentY() - ny - font.lineHeight * ChatLayout.GROUP_NAME_SCALE;
            check(gap >= 1 && gap <= 2, "昵称紧贴气泡，保留一至两个逻辑像素间隔");
        }
        var own = group.get(1); var ownName = nickname(pane, font, own, 10, 20);
        check(field(ownName, "text").equals("LocalPlayer自己")
                && font.width((String) field(ownName, "text")) * ChatLayout.GROUP_NAME_SCALE > own.w(), "短气泡上方也保留完整己方昵称");
        field(pane, "senderName", (java.util.function.Function<ChatMessage, String>) m -> "很长的群昵称VeryLongName".repeat(10));
        var longName = nickname(pane, font, own, 10, 20);
        check(font.width((String) field(longName, "text")) * ChatLayout.GROUP_NAME_SCALE <= ChatLayout.messageContentWidth(112)
                && (float) field(longName, "x") >= 10 + ChatLayout.AVATAR_SIZE + ChatLayout.MESSAGE_AVATAR_GAP, "长昵称截断在正文列内，不覆盖另一侧头像");
        field(pane, "senderName", (java.util.function.Function<ChatMessage, String>) m -> "");
        check(nickname(pane, font, own, 10, 20) == null, "缺少昵称时不画空占位文字");
        var privatePane = pane(font, src, false);
        check(nickname(privatePane, font, direct.get(1), 10, 20) == null, "私聊不额外显示群昵称");

        var message = ChatMessage.text(SELF, "你好", 1); var one = List.of(message);
        var selected = pane(font, one, true);
        var listBefore = field(selected, "blocks");
        call(selected, "relayout", new Class<?>[]{Font.class, int.class, List.class, boolean.class}, font, 112, one, true);
        check(field(selected, "blocks") == listBefore, "同群连续帧沿用缓存");
        call(selected, "relayout", new Class<?>[]{Font.class, int.class, List.class, boolean.class}, font, 112, one, false);
        check(field(selected, "blocks") != listBefore, "同消息切换会话类型也必须重排");
        call(selected, "relayout", new Class<?>[]{Font.class, int.class, List.class, boolean.class}, font, 112, one, true);
        hits(selected, 20, font);
        var block = ChatMessageLayout.build(font, one, 112, SELF, true).get(0);
        int bx = 10 + ChatLayout.messageContentX(112, block.w(), true), by = 20 + block.placement().contentY();
        check(selected.messageAt(bx + 1, by + 1).equals(message), "右键命中使用移动后的群气泡位置");
        var label = nickname(selected, font, block, 10, 20);
        check(selected.messageAt((float) field(label, "x") + 1, (float) field(label, "y") + 1) == null
                && !selected.mouseClicked((float) field(label, "x") + 1, (float) field(label, "y") + 1), "昵称不进入正文右键或选字区域");
        double textX = bx + block.textOrigin().x(), textY = by + block.textOrigin().y();
        check(selected.mouseClicked(textX + 0.01, textY + 0.01), "移位气泡仍能开始拖选");
        selected.mouseDragged(textX + 9 * ChatLayout.TEXT_SCALE, textY + 0.01, 0);
        selected.mouseReleased(textX + 9 * ChatLayout.TEXT_SCALE, textY + 0.01, 0);
        check(selected.selectedText().equals("你"), "实际命中与正文原点一致，只复制所选正文");
        hits(selected, 50, font);
        check(selected.messageAt(bx + 1, by + 1) == null && selected.messageAt(bx + 1, by + 31).equals(message), "滚动后丢弃旧命中区而非叠加");
        selected.reset();
        check(selected.selectedText().isEmpty() && selected.messageAt(bx + 1, by + 31) == null, "切换会话清理选区和消息命中");
        System.out.println("全部通过：" + checks + " 条断言");
        if (args.length == 1) preview(Path.of(args[0]), font);
    }

    /** 仅导出生产排版坐标的示意图。字体、皮肤为示意，不能据此宣称游戏视觉验收。 */
    private static void preview(Path output, Font font) throws Exception {
        StringBuilder svg = new StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' width='840' height='1232' viewBox='0 0 120 176'>");
        svg.append("<rect width='120' height='176' rx='3' fill='#0f1642'/><g font-family='Microsoft YaHei, sans-serif' fill='#dce2ee'>");
        svg.append("<text x='4' y='8' font-size='4.5'>群聊排版示意 · 实际代码坐标</text>");
        for (int count = 1; count <= 4; count++) {
            int x = 8 + (count - 1) * 28;
            svg.append("<rect x='").append(x - 1).append("' y='15' width='14' height='14' fill='#39304d' stroke='#73618b' stroke-width='.25'/>");
            int i = 0;
            for (var tile : ChatGroupAvatar.layout(count, 12)) face(svg, x + tile.x(), 16 + tile.y(), tile.size(), i++);
            svg.append("<text x='").append(x + 6).append("' y='35' text-anchor='middle' font-size='4'>").append(count).append(" 人</text>");
        }
        svg.append("<path d='M4 40H116' stroke='#49506b' stroke-width='.25'/>");
        var src = List.of(ChatMessage.text(PEER, "你好", 1), ChatMessage.text(SELF, "你好", 2),
                ChatMessage.text(PEER, "黄金叶还好，整一堆让他自己慢慢扩散就行", 3),
                ChatMessage.text(SELF, "好的，明天一起玩", 4));
        var pane = pane(font, src, true); int y = 46;
        for (var block : ChatMessageLayout.build(font, src, 112, SELF, true)) {
            int bx = 4 + ChatLayout.messageContentX(112, block.w(), block.self()), by = y + block.placement().contentY();
            face(svg, block.self() ? 104 : 4, y, 12, block.self() ? 1 : 0);
            var name = nickname(pane, font, block, 4, y);
            // SVG 渲染器未必支持 textLength；己方以右锚点绘制，避免替代字体造成假性错位。
            float nameX = block.self() ? bx + block.w() : (float) field(name, "x");
            svg.append("<text x='").append(nameX).append("' text-anchor='").append(block.self() ? "end" : "start")
                    .append("' y='").append((float) field(name, "y") + 4.5f)
                    .append("' font-size='4.5' fill='#aeb7c9' textLength='").append(font.width((String) field(name, "text")) * ChatLayout.GROUP_NAME_SCALE)
                    .append("' lengthAdjust='spacingAndGlyphs'>").append(field(name, "text")).append("</text>");
            svg.append("<rect x='").append(bx).append("' y='").append(by).append("' width='").append(block.w())
                    .append("' height='").append(block.h()).append("' rx='2.8' fill='").append(block.self() ? "#865276" : "#50637e")
                    .append("' fill-opacity='.85' stroke='").append(block.self() ? "#cb9fbd" : "#b0c6dc").append("' stroke-width='.3'/>");
            int line = 0;
            for (var text : block.lines()) {
                var value = new StringBuilder(); text.accept((index, style, cp) -> { value.appendCodePoint(cp); return true; });
                svg.append("<text x='").append(bx + block.textOrigin().x()).append("' y='")
                        .append(by + block.textOrigin().y() + 6.75f + line++ * ChatUi.lineHeight(font, ChatLayout.TEXT_SCALE))
                        .append("' font-size='6.75' textLength='").append(font.width(text) * ChatLayout.TEXT_SCALE)
                        .append("' lengthAdjust='spacingAndGlyphs'>").append(value).append("</text>");
            }
            y += block.rowHeight() + ChatMessageLayout.BLOCK_GAP;
        }
        svg.append("<text x='4' y='157' font-size='4' fill='#aeb7c9'>字号、间距已按代码生成</text>")
                .append("<text x='4' y='166' font-size='4' fill='#aeb7c9'>字体/皮肤为示意，游戏内效果待实测</text></g></svg>");
        Files.createDirectories(output.toAbsolutePath().getParent()); Files.writeString(output, svg, StandardCharsets.UTF_8);
    }
    private static void face(StringBuilder svg, float x, float y, int size, int i) {
        svg.append("<g transform='translate(").append(x).append(' ').append(y).append(") scale(").append(size / 8f).append(")'>")
                .append("<rect width='8' height='8' fill='").append(i % 2 == 0 ? "#d5c0ee" : "#ac7652").append("'/>")
                .append("<path d='M0 0H8V2H6V1H3V3H0Z' fill='").append(i % 2 == 0 ? "#eee2fa" : "#462918").append("'/>")
                .append("<path d='M1 4H3V5H1ZM5 4H7V5H5Z' fill='#efeff5'/><path d='M2 4H3V5H2ZM5 4H6V5H5Z' fill='#3c3a60'/>")
                .append("<path d='M3 6H5V7H3Z' fill='#8b565b'/></g>");
    }
}
