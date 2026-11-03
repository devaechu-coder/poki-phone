package com.november.mcphone.feature.chat.client;

import com.november.mcphone.feature.chat.group.GroupInfo;
import com.november.mcphone.feature.chat.group.GroupMember;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 生产排版的对齐和留白，以及卡片内勾选区域的真实点击；仍需游戏截图验收。 */
public final class ChatUiAlignmentTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static boolean near(float a, float b) { return Math.abs(a - b) < .0001f; }
    private static boolean inside(ChatLayout.Rect inner, ChatLayout.Rect outer) {
        return inner.x() >= outer.x() && inner.y() >= outer.y()
                && inner.x() + inner.width() <= outer.x() + outer.width()
                && inner.y() + inner.height() <= outer.y() + outer.height();
    }
    private static Object field(Object owner, String name) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    private static void field(Object owner, String name, Object value) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); f.set(owner, value);
    }
    public static void main(String[] args) throws Exception {
        for (int y : new int[]{0, 17, 153}) for (int width : new int[]{48, 112, 232}) {
            var area = new ChatLayout.Rect(11, y, width, ChatLayout.SEARCH_HEIGHT);
            var search = ChatContactSearch.layout(area, 9, 6, true);
            float center = y + area.height() / 2f;
            check(near(search.iconY() + ChatLayout.SEARCH_ICON_SIZE / 2f, center), "搜索图标不因奇数原点偏移");
            check(near(search.hintY() + .5f * ChatLayout.META_SCALE + 8 * ChatLayout.META_SCALE / 2f, center), "中文提示与图标共用校准后的字形中心");
            check(near(search.inputY() + .5f * ChatLayout.TEXT_SCALE + 8 * ChatLayout.TEXT_SCALE / 2f, center), "输入和提示字号不同仍对齐中心");
            check(search.textX() - area.x() - ChatLayout.SEARCH_PAD_X - ChatLayout.SEARCH_ICON_SIZE == 3,
                    "搜索图标和文字保留三像素间距");
            check(search.inputY() >= y && search.inputY() + 9 * ChatLayout.TEXT_SCALE <= y + area.height(), "输入行与光标留在输入框内");
            check(search.textX() + (search.inputWidth() + 6) * ChatLayout.TEXT_SCALE <= area.x() + width - 3,
                    "可编辑文本和光标不跨越右侧内边距");
            var name = ChatContactSearch.layout(area, 9, 6, false);
            check(name.textX() == area.x() + ChatLayout.SEARCH_PAD_X && near(name.inputY(), search.inputY()),
                    "不带图标的群名输入复用相同纵向排版");
        }
        for (float scale : new float[]{ChatLayout.TEXT_SCALE, ChatLayout.META_SCALE, ChatLayout.TAB_LABEL_SCALE}) {
            for (int advance : new int[]{6, 19, 54, 97}) {
                check(near(ChatLayout.labelLeft(advance, scale, 63.5f) + (advance - 1) * scale / 2f, 63.5f),
                        "英文和中文标签不先取整，保持水平中心");
            }
            check(near(ChatLayout.labelTop(9, scale, 37) + .5f * scale + 8 * scale / 2f, 37), "标题、入口与标签使用相同纵向算法");
        }
        check(near(ChatLayout.labelTop(9, ChatLayout.TEXT_SCALE, 37), 37 + ChatLayout.bubbleText(0, 0, 0, 9, 1).y()),
                "中文标签与已校准的正文使用相同字形指标，不另起七像素估算");
        for (int width : new int[]{24, 25, 27, 29}) {
            var cell = new ChatLayout.Rect(13, 20, width, ChatGroupMemberGrid.ROW_HEIGHT);
            check(near(ChatGroupMemberGrid.avatarX(cell) + 8, cell.x() + width / 2f),
                    "奇数列宽的成员头像、邀请方框及标签共用水平中心");
        }
        for (int x : new int[]{0, 13}) for (int y : new int[]{0, 19, 157}) for (int width : new int[]{48, 103, 112, 232}) {
            var row = ChatContactRow.layout(x, y, width, ChatContactScroll.CONTACT_HEIGHT, true);
            var normal = ChatContactRow.layout(x, y, width, ChatContactScroll.CONTACT_HEIGHT, false);
            check(row.card().width() == width && row.card().height() == ChatContactScroll.CONTACT_HEIGHT - 1,
                    "选人卡片不缩短，不将勾选框挤到气泡外");
            check(inside(row.choice(), row.card()) && row.choice().x() + row.choice().width() <= x + width - 3,
                    "勾选框完整放在卡片内并留右侧边距");
            check(near(row.choice().y() + 4, row.card().y() + row.card().height() / 2f)
                    && near(row.avatar().y() + row.avatar().height() / 2f, row.choice().y() + 4), "勾选框、头像与卡片中心对齐");
            check(inside(row.avatar(), row.card()) && inside(row.statusDot(), row.card()), "头像和状态点保留卡片内边距");
            check(row.textX() >= row.avatar().x() + row.avatar().width() + 3
                    && row.textX() + row.textWidth() <= row.choice().x() - 4, "长名字的截断范围避让头像与勾选框");
            check(near(row.statusDot().y() + 1, row.statusCenterY()), "在线状态点与标签纵向对齐");
            check(normal.choice() == null && normal.textWidth() > row.textWidth()
                    && normal.avatar().equals(row.avatar()) && near(normal.nameCenterY(), row.nameCenterY()), "普通好友与选人共用姓名位置，仅选择列占额外宽度");
        }
        for (var icon : new ChatUi.Icon[]{ChatUi.Icon.ADD, ChatUi.Icon.CHECK}) {
            boolean[][] pixels = new boolean[24][24];
            ChatUi.mark((x1, y1, x2, y2, color) -> {
                for (int y = y1; y < y2; y++) for (int x = x1; x < x2; x++) pixels[y][x] = true;
            }, icon, 0xFFFFFFFF);
            int left = 24, right = 0, top = 24, bottom = 0;
            for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) if (pixels[y][x]) {
                left = Math.min(left, x); right = Math.max(right, x + 1);
                top = Math.min(top, y); bottom = Math.max(bottom, y + 1);
            }
            check((left + right) == 24 && (top + bottom) == 24, "加号与勾号的可见轮廓居中，不依赖字符字形");
            check(left > 0 && top > 0 && right < 24 && bottom < 24, "细线图标周围保留透明留白");
            if (icon == ChatUi.Icon.ADD) check(pixels[12][4] && pixels[4][12] && !pixels[4][4] && !pixels[12][2], "邀请为简单加号，没有外围圆圈");
            else check(pixels[14][4] && pixels[17][8] && pixels[6][20] && !pixels[6][4], "勾号保留短左臂和长右臂");
        }
        var contact = new ChatContactDirectory.Contact(new UUID(0, 9), "长名字的好友", true);
        var area = new ChatLayout.Rect(10, 20, 112, 70);
        var sections = List.of(new ChatContactDirectory.Section("C", List.of(contact)));
        var create = new ChatGroupCreate(); prepare((ChatContactPane) field(create, "pane"), area, sections);
        var row = ChatContactRow.layout(area.x(), area.y() + ChatContactScroll.HEADER_HEIGHT,
                area.width() - 9, ChatContactScroll.CONTACT_HEIGHT, true);
        double mx = row.choice().x() + 4, my = row.choice().y() + 4;
        create.mouseClicked(mx, my, 0);
        check(((Set<?>) field(create, "selected")).contains(contact.id()), "点击卡片内勾选框走实际建群选人逻辑");
        create.mouseClicked(mx, my, 0); check(((Set<?>) field(create, "selected")).isEmpty(), "重复点击同一勾选框取消选择");
        create.mouseClicked(mx, my, 1); check(((Set<?>) field(create, "selected")).isEmpty(), "右键仍不选人");
        create.close(); create.mouseClicked(mx, my, 0);
        check(((Set<?>) field(create, "selected")).isEmpty(), "离页后旧选择区域失效");
        var invite = new ChatGroupInvite(); prepare((ChatContactPane) field(invite, "pane"), area, sections);
        field(invite, "info", new GroupInfo(new UUID(1, 1), "群", new UUID(0, 1), List.of(new GroupMember(new UUID(0, 1), "群主"))));
        invite.click(mx, my, 0); check(((Set<?>) field(invite, "selected")).contains(contact.id()), "邀请页卡片内的勾选框走实际选择逻辑");
        invite.click(mx, my, 0); check(((Set<?>) field(invite, "selected")).isEmpty(), "邀请页重复点击取消选择");
        invite.close(); invite.click(mx, my, 0); check(((Set<?>) field(invite, "selected")).isEmpty(), "邀请页关闭也清除旧命中");
        System.out.println("全部通过：" + checks + " 条断言");
    }
    private static void prepare(ChatContactPane pane, ChatLayout.Rect area, List<ChatContactDirectory.Section> sections) throws Exception {
        field(pane, "bounds", area); ((ChatContactScroll) field(pane, "scroll")).update(sections, area.height());
    }
}
