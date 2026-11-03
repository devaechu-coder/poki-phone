package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.feature.chat.group.GroupInfo;
import com.november.mcphone.feature.chat.group.GroupLimits;
import com.november.mcphone.feature.chat.net.ChatClientCache;
import com.november.mcphone.feature.chat.net.ConversationSummary;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 邀请选人视图：复用联系人搜索与 A–Z 列表，只交出好友 ID，不发送包或写入群缓存。 */
final class ChatGroupInvite {
    private final ChatContactSearch search = new ChatContactSearch();
    private final ChatContactPane pane = new ChatContactPane();
    private final Set<UUID> selected = new LinkedHashSet<>();
    private ChatContactDirectory directory;
    private List<ConversationSummary> source;
    private GroupInfo info;
    private String query = "";
    private List<ChatContactDirectory.Section> sections = List.of();
    private ChatLayout.Rect confirm;
    void open() { close(); search.reset(); }
    void close() { search.close(); pane.close(); selected.clear(); source = null; info = null; confirm = null; }
    private void refresh(GroupInfo next) {
        var friends = ChatClientCache.getConversations(); String nextQuery = search.query();
        if (source == friends && info == next && query.equals(nextQuery)) return;
        if (directory == null) directory = new ChatContactDirectory();
        var eligible = friends.stream().filter(c -> next.members().stream().noneMatch(m -> m.id().equals(c.id()))).toList();
        selected.removeIf(id -> eligible.stream().noneMatch(c -> c.id().equals(id)));
        int limit = Math.max(0, GroupLimits.MEMBERS - next.members().size());
        while (selected.size() > limit) selected.remove(selected.stream().reduce((a, b) -> b).orElseThrow());
        if (!query.equals(nextQuery)) pane.reset(); query = nextQuery; source = friends; info = next;
        sections = directory.sections(eligible.stream().map(c -> new ChatContactDirectory.Contact(c.id(), c.name(), c.online())).toList(), query);
    }
    void render(GuiGraphics g, Font font, GroupInfo info, ChatLayout.Rect area, int mx, int my, String status, boolean pending) {
        refresh(info);
        search.render(g, font, new ChatLayout.Rect(area.x(), area.y(), area.width(), ChatLayout.SEARCH_HEIGHT), mx, my, 0);
        int bottom = area.y() + area.height(); confirm = new ChatLayout.Rect(area.x(), bottom - 14, area.width(), 14);
        pane.render(g, font, sections, new ChatLayout.Rect(area.x(), area.y() + 15, area.width(), Math.max(0, area.height() - 44)), mx, my,
                query.isBlank() ? "mcphone.chat.group.invite_empty" : "mcphone.chat.contacts.search_empty", this::row);
        String hint = status.isEmpty() ? Component.translatable(selected.isEmpty() ? "mcphone.chat.group.select_members" : "mcphone.chat.group.selected", selected.size()).getString()
                : Component.translatable(status).getString();
        ChatUi.text(g, font, ChatUi.truncate(font, hint, area.width(), ChatLayout.META_SCALE), area.x(), bottom - 25, ChatLayout.META_SCALE, FontPalette.subtle());
        ChatUi.glass(g, ChatGlass.Surface.BUTTON, confirm.x(), confirm.y(), confirm.width(), confirm.height(), !pending && !selected.isEmpty() && confirm.contains(mx, my));
        ChatUi.buttonText(g, font, Component.translatable(pending ? "mcphone.chat.group.pending" : "mcphone.chat.group.invite_confirm").getString(),
                confirm.x() + confirm.width() / 2f, confirm.y() + 7, ChatLayout.TEXT_SCALE, selected.isEmpty() || pending ? FontPalette.dim() : FontPalette.title());
    }
    private void row(GuiGraphics g, Font font, ChatContactDirectory.Contact c, int x, int y, int w, int h, boolean hover) {
        ChatContactRow.render(g, font, c, x, y, w, h, hover, true, selected.contains(c.id()));
    }
    List<UUID> click(double mx, double my, int button) {
        if (search.mouseClicked(mx, my, button) || button != 0) return null;
        if (confirm != null && confirm.contains(mx, my)) return List.copyOf(selected);
        UUID id = pane.click(mx, my, button);
        if (id != null && !selected.remove(id) && info != null && selected.size() < GroupLimits.MEMBERS - info.members().size()) selected.add(id);
        return null;
    }
    boolean keyPressed(int key, int scan, int modifiers) {
        String before = search.query(); boolean handled = search.keyPressed(key, scan, modifiers);
        if (!before.equals(search.query())) pane.close(); return handled;
    }
    boolean charTyped(char c, int modifiers) {
        boolean handled = search.charTyped(c, modifiers); if (handled) pane.close(); return handled;
    }
    boolean scroll(double mx, double my, double amount) { return pane.mouseScrolled(mx, my, amount); }
}
