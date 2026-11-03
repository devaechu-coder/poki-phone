package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.feature.chat.group.GroupLimits;
import com.november.mcphone.util.TextSanitizer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 群名编辑视图只管理草稿，成功确认之前不会修改显示名或存档。 */
final class ChatGroupRename {
    private final ChatContactSearch name = new ChatContactSearch("mcphone.chat.group.rename_hint", GroupLimits.NAME_LENGTH);
    private ChatLayout.Rect save;
    void open(String current) { close(); name.value(current); }
    void close() { name.close(); save = null; }
    String value() { return TextSanitizer.sanitize(name.query(), GroupLimits.NAME_LENGTH).strip(); }
    void render(GuiGraphics g, Font font, ChatLayout.Rect area, int mx, int my, String status, boolean pending) {
        name.render(g, font, new ChatLayout.Rect(area.x(), area.y() + 2, area.width(), 12), mx, my, 0);
        ChatUi.text(g, font, ChatUi.truncate(font, Component.translatable(status.isEmpty() ? "mcphone.chat.group.rename_hint" : status).getString(), area.width(), ChatLayout.META_SCALE),
                area.x(), area.y() + 18, ChatLayout.META_SCALE, FontPalette.subtle());
        save = new ChatLayout.Rect(area.x(), area.y() + area.height() - 14, area.width(), 14);
        ChatUi.glass(g, ChatGlass.Surface.BUTTON, save.x(), save.y(), save.width(), save.height(), !pending && !value().isEmpty() && save.contains(mx, my));
        ChatUi.buttonText(g, font, Component.translatable(pending ? "mcphone.chat.group.pending" : "mcphone.chat.group.save").getString(), save.x() + save.width() / 2f,
                save.y() + 7, ChatLayout.TEXT_SCALE, pending || value().isEmpty() ? FontPalette.dim() : FontPalette.title());
    }
    String click(double mx, double my, int button) {
        if (name.mouseClicked(mx, my, button) || button != 0 || save == null || !save.contains(mx, my)) return null;
        return value();
    }
    boolean keyPressed(int key, int scan, int modifiers) { return name.keyPressed(key, scan, modifiers); }
    boolean charTyped(char c, int modifiers) { return name.charTyped(c, modifiers); }
}
