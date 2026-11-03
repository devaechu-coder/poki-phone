package com.november.mcphone.feature.chat.client;

/** 两种设备、奇数宽及移动原点的生产布局；卡片文字组与方形头像居中。 */
public final class ChatProfileLayoutTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }

    public static void main(String[] args) {
        for (int width : new int[]{111, 112, 214, 215, 232}) {
            for (int height : new int[]{122, 140}) for (int origin : new int[]{0, 3, 81}) {
                var area = new ChatLayout.Rect(origin, origin + 28, width, height);
                var layout = ChatProfileLayout.home(area);
                var avatar = layout.avatar();
                check(avatar.width() == avatar.height() && avatar.width() == 24, "保持 MC 方形头像");
                check((layout.nameCenterY() + layout.introCenterY()) / 2f == avatar.y() + avatar.height() / 2f,
                        "名字与备注的组合中心对齐头像中心");
                check(layout.textX() - avatar.x() - avatar.width() == 6, "头像与文字固定间距");
                check(layout.rows().size() == 4, "只有四个获授权入口");
                int previous = layout.card().y() + layout.card().height();
                for (var row : layout.rows()) {
                    check(row.y() > previous && row.y() + row.height() <= area.y() + area.height(), "入口不重叠、不遮住底栏");
                    check(row.x() == area.x() && row.width() == width, "四行左右边界对齐");
                    check(row.contains(row.x() + width / 2f, row.y() + 9), "图文中心位于实际命中区");
                    previous = row.y() + row.height();
                }
            }
        }
        System.out.println("全部通过：" + checks + " 条断言");
    }
}
