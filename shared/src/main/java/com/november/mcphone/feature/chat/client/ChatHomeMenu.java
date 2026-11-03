package com.november.mcphone.feature.chat.client;

import com.november.mcphone.feature.chat.client.contextmenu.ChatContextMenuView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.util.List;

/** 首页加号菜单：只交出操作意图，好友页面和建群流程由页面协调。 */
final class ChatHomeMenu {
    enum Action { CREATE_GROUP, ADD_CONTACT }
    private static final List<ChatContextMenuView.Entry> ENTRIES = List.of(
            new ChatContextMenuView.Entry("mcphone.chat.home_menu.create_group", ChatContextMenuView.Icon.CREATE_GROUP, true),
            new ChatContextMenuView.Entry("mcphone.chat.home_menu.add_contact", ChatContextMenuView.Icon.ADD_CONTACT, true));
    private final ChatContextMenuView view = new ChatContextMenuView();
    private int anchorX, anchorY;

    boolean isOpen() { return view.isOpen(); }
    boolean dismiss() { return view.dismiss(); }
    void open(int right, int bottom) { anchorX = right; anchorY = bottom; view.open(right, bottom); }

    /** 点菜单外或非左键只关闭；调用方必须消费整次点击，不能再交给底下会话。 */
    Action choose(double x, double y, int button) {
        int row = button == 0 ? view.hitIndex(x, y) : -1;
        dismiss();
        return switch (row) { case 0 -> Action.CREATE_GROUP; case 1 -> Action.ADD_CONTACT; default -> null; };
    }

    void render(GuiGraphics g, Font font, int x, int y, int width, int height, int right, int bottom, int mx, int my) {
        if (!isOpen()) return;
        // 窗口尺寸改变后仍跟着加号；原位置的命中布局同时失效。
        if (anchorX != right || anchorY != bottom) open(right, bottom);
        view.render(g, font, x, y, width, height, mx, my, ENTRIES, (graphics, px, py, pw, ph) -> {
            ChatUi.glass(graphics, ChatGlass.Surface.MENU, px, py, pw, ph, false);
            // 菜单从加号下方展开，小尖角只在能指向加号且留在内容区时绘制。
            int tip = Math.max(px + 5, Math.min(right - 5, px + pw - 5));
            if (py == bottom && py - 3 >= y) {
                int color = ChatGlass.palette(ChatGlass.Surface.MENU,
                        com.november.mcphone.core.client.FontPalette.current().darkText(), false).top();
                for (int i = 0; i < 3; i++) graphics.fill(tip - i, py - 3 + i, tip + i + 1, py - 2 + i, color);
            }
        });
    }
}
