package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.net.MCphoneNetwork;
import com.november.mcphone.util.TextSanitizer;
import com.november.mcphone.feature.chat.net.*;
import com.november.mcphone.feature.chat.group.*;
import com.november.mcphone.feature.chat.group.net.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 选好友建群页面。好友投影、选人状态和服务端确认分开，拒收时保留选择。 */
public final class ChatGroupCreate {
    private final Consumer<GroupCommand.Create> send;
    private final Supplier<String> defaultName;
    private final ChatContactSearch name = new ChatContactSearch("mcphone.chat.group.name_hint", GroupLimits.NAME_LENGTH);
    private final ChatContactSearch search = new ChatContactSearch();
    private final ChatContactPane pane = new ChatContactPane();
    private ChatContactDirectory directory;
    private final Set<UUID> selected = new LinkedHashSet<>();
    private List<ConversationSummary> source;
    private String query = "", status = "";
    private List<ChatContactDirectory.Section> sections = List.of();
    private ChatLayout.Rect confirm;
    private UUID request, created;
    private long requestedAt;
    public ChatGroupCreate() {
        this(GroupTransport::send, () -> Component.translatable("mcphone.chat.group.default_name").getString());
    }
    /** 默认名由当前语言提供，页面只交出命令；无窗口验证也不必装配游戏连接与语言管理器。 */
    ChatGroupCreate(Consumer<GroupCommand.Create> send, Supplier<String> defaultName) {
        this.send = Objects.requireNonNull(send); this.defaultName = Objects.requireNonNull(defaultName);
    }
    public void open() {
        close(); name.reset(); search.reset(); selected.clear(); status = ""; query = ""; source = null;
        if (directory == null) directory = new ChatContactDirectory();
        MCphoneNetwork.sendToServer(new RequestConversationsPacket());
    }
    public void close() {
        if (request != null) GroupClientCache.cancel(request);
        request = created = null; confirm = null; selected.clear(); name.close(); search.close(); pane.close();
    }
    public UUID consumeCreated() { var id = created; created = null; return id; }
    private void pollResult() {
        if (request != null) {
            var result = GroupClientCache.consume(request);
            if (result != null) {
                if (result.status() == GroupStatus.OK) created = result.group(); else status = result.status().key();
                request = null;
            } else if (System.currentTimeMillis() - requestedAt > GroupClientPolicy.OPERATION_TIMEOUT_MS) {
                GroupClientCache.cancel(request); request = null; status = "mcphone.chat.group.timeout";
            }
        }
    }
    public void render(GuiGraphics g, int left, int top, int width, int height, int statusH, int navH,
                       int mx, int my, Font font, float tick) {
        pollResult();
        int x = left + 4, w = width - 8, y = top + statusH + ChatLayout.TOP_GAP;
        ChatUi.buttonText(g, font, Component.translatable("mcphone.chat.home_menu.create_group").getString(), x + w / 2f, y + 6, ChatLayout.TEXT_SCALE, FontPalette.title());
        y += 14; name.render(g, font, new ChatLayout.Rect(x, y, w, 12), mx, my, tick);
        y += 14; search.render(g, font, new ChatLayout.Rect(x, y, w, ChatLayout.SEARCH_HEIGHT), mx, my, tick); y += ChatLayout.SEARCH_HEIGHT + ChatLayout.SECTION_GAP;
        var next = ChatClientCache.getConversations(); String nextQuery = search.query();
        if (source != next || !query.equals(nextQuery)) {
            if (!query.equals(nextQuery)) pane.reset(); source = next; query = nextQuery;
            selected.removeIf(id -> next.stream().noneMatch(c -> c.id().equals(id)));
            sections = directory.sections(next.stream().map(c -> new ChatContactDirectory.Contact(c.id(), c.name(), c.online())).toList(), query);
        }
        int bottom = top + height - navH - 2;
        confirm = new ChatLayout.Rect(x, bottom - 14, w, 14);
        pane.render(g, font, sections, new ChatLayout.Rect(x, y, w, Math.max(0, bottom - y - 29)), mx, my,
                query.isBlank() ? "mcphone.chat.contacts.friends_empty" : "mcphone.chat.contacts.search_empty", this::row);
        String hint = status.isEmpty() ? (selected.isEmpty()
                ? Component.translatable("mcphone.chat.group.select_members").getString()
                : Component.translatable("mcphone.chat.group.selected", selected.size()).getString())
                : Component.translatable(status).getString();
        ChatUi.text(g, font, ChatUi.truncate(font, hint, w, ChatLayout.META_SCALE), x, bottom - 25, ChatLayout.META_SCALE, FontPalette.subtle());
        ChatUi.glass(g, ChatGlass.Surface.BUTTON, x, confirm.y(), w, 14, ready() && confirm.contains(mx, my));
        ChatUi.buttonText(g, font, Component.translatable(request == null ? "mcphone.chat.group.create" : "mcphone.chat.group.pending").getString(),
                x + w / 2f, confirm.y() + 7, ChatLayout.TEXT_SCALE, ready() ? FontPalette.title() : FontPalette.dim());
    }
    private boolean ready() { return request == null && !selected.isEmpty(); }
    private String groupName() {
        // 群名选填；先清洗输入，再回退默认名，避免空白/控制字符使创建按钮看似失效。
        String value = TextSanitizer.sanitize(name.query(), GroupLimits.NAME_LENGTH).strip();
        if (value.isEmpty()) value = TextSanitizer.sanitize(defaultName.get(), GroupLimits.NAME_LENGTH).strip();
        return value.isEmpty() ? "Group" : value;
    }
    private void row(GuiGraphics g, Font font, ChatContactDirectory.Contact c, int x, int y, int w, int h, boolean hover) {
        ChatContactRow.render(g, font, c, x, y, w, h, hover, true, selected.contains(c.id()));
    }
    public boolean mouseClicked(double mx, double my, int button) {
        if (request != null) return true;
        boolean input = name.mouseClicked(mx, my, button); input = search.mouseClicked(mx, my, button) || input;
        if (input || button != 0) return input;
        if (confirm != null && confirm.contains(mx, my)) {
            if (!ready()) { status = "mcphone.chat.group.select_members"; return true; }
            request = UUID.randomUUID(); requestedAt = System.currentTimeMillis(); status = ""; GroupClientCache.track(request);
            send.accept(new GroupCommand.Create(request, groupName(), List.copyOf(selected))); return true;
        }
        UUID id = pane.click(mx, my, button);
        if (id != null) { if (!selected.remove(id) && selected.size() < GroupLimits.MEMBERS - 1) selected.add(id); status = ""; }
        return true;
    }
    public boolean keyPressed(int key, int scan, int modifiers) { return request != null || name.keyPressed(key, scan, modifiers) || search.keyPressed(key, scan, modifiers); }
    public boolean charTyped(char c, int modifiers) { return request != null || name.charTyped(c, modifiers) || search.charTyped(c, modifiers); }
    public boolean mouseScrolled(double mx, double my, double amount) { return request == null && pane.mouseScrolled(mx, my, amount); }
}
