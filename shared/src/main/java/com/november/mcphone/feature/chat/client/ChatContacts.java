package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.net.MCphoneNetwork;
import com.november.mcphone.feature.chat.net.ChatClientCache;
import com.november.mcphone.feature.chat.net.ConversationSummary;
import com.november.mcphone.feature.chat.net.RequestConversationsPacket;
import com.november.mcphone.feature.chat.group.GroupSummary;
import com.november.mcphone.feature.chat.group.net.GroupClientCache;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 联系人页协调器：数据投影、页面状态和导航意图；输入与列表绘制各自独立。 */
public final class ChatContacts {
    public enum Action { CHATS, ADD_CONTACT, NEW_FRIENDS, PROFILE }
    private ChatContactDirectory directory;
    private final ChatContactSearch search = new ChatContactSearch();
    private final ChatContactPane pane = new ChatContactPane();
    private final ChatNavigationBar navigation = new ChatNavigationBar();
    private final ChatGroupNotices notices = new ChatGroupNotices();
    private List<ConversationSummary> source;
    private List<GroupSummary> groupSource;
    private List<ChatContactDirectory.Section> sections = List.of();
    private String query = "";
    private boolean groups, notifications;
    private long lastRequest;
    private ChatLayout.Rect add, newFriends, groupNotices, friendsTab, groupsTab;
    private Action pendingAction;
    private UUID pendingPeer, pendingGroup, pendingProfile;

    public void open() {
        close(); search.reset(); groups = false; lastRequest = 0;
        // 只有进入联系人时才构建拼音索引，避免每次打开其他 App 都初始化 ICU。
        if (directory == null) directory = new ChatContactDirectory();
    }
    public void close() {
        search.close(); pane.close(); navigation.clear(); source = null; query = ""; sections = List.of();
        add = newFriends = groupNotices = friendsTab = groupsTab = null;
        pendingAction = null; pendingPeer = pendingGroup = pendingProfile = null; groupSource = null;
        notifications = false;
        notices.close();
    }
    public Action consumeAction() { var out = pendingAction; pendingAction = null; return out; }
    public UUID consumeProfileRequest() { var out=pendingProfile;pendingProfile=null;return out; }
    public UUID consumeOpenRequest() { var out = pendingPeer; pendingPeer = null; return out; }
    public UUID consumeGroupOpenRequest() { var out = pendingGroup; pendingGroup = null; return out; }
    public boolean back() {
        if (!notifications) return false;
        notifications = false; add = null; notices.close(); return true;
    }
    public boolean keyPressed(int key, int scan, int modifiers) { return search.keyPressed(key, scan, modifiers); }
    public boolean charTyped(char c, int modifiers) { return search.charTyped(c, modifiers); }
    public boolean mouseScrolled(double mx, double my, double amount) {
        return notifications ? notices.mouseScrolled(mx, my, amount) : pane.mouseScrolled(mx, my, amount);
    }

    public void render(GuiGraphics g, int phoneLeft, int phoneTop, int screenW, int screenH,
                       int statusH, int navH, int mx, int my, Font font, float tick) {
        long now = System.currentTimeMillis();
        GroupTransport.notices();
        if (now - lastRequest >= 3000) {
            lastRequest = now; MCphoneNetwork.sendToServer(new RequestConversationsPacket());
            GroupTransport.directory();
        }
        int x = phoneLeft + 4, w = screenW - 8;
        int y = phoneTop + statusH + ChatLayout.TOP_GAP;
        int tabsY = phoneTop + screenH - navH - ChatLayout.TAB_HEIGHT;
        title(g, font, notifications ? "mcphone.chat.contacts.group_notices" : "mcphone.chat.layout.contacts",
                x, y, w);
        if (notifications) {
            add = null;
            int listY = y + ChatLayout.HEADER_HEIGHT + ChatLayout.SECTION_GAP;
            notices.render(g, font, new ChatLayout.Rect(x, listY, w, Math.max(0, phoneTop + screenH - navH - listY - 3)), mx, my);
            return;
        }
        add = new ChatLayout.Rect(x + w - 12, y, 12, ChatLayout.HEADER_HEIGHT);
        ChatUi.sizedIcon(g, ChatUi.Icon.ADD_CONTACT, add.x() + (add.width() - ChatLayout.ICON_SIZE) / 2f,
                y + (ChatLayout.HEADER_HEIGHT - ChatLayout.ICON_SIZE) / 2f, ChatLayout.ICON_SIZE,
                add.contains(mx, my) ? ChatUi.accent() : FontPalette.body());
        y += ChatLayout.HEADER_HEIGHT + 2;
        search.render(g, font, new ChatLayout.Rect(x, y, w, ChatLayout.SEARCH_HEIGHT), mx, my, tick);
        y += ChatLayout.SEARCH_HEIGHT + ChatLayout.SECTION_GAP;
        newFriends = new ChatLayout.Rect(x, y, w, 14);
        shortcut(g, font, newFriends, "mcphone.chat.contacts.new_friends", mx, my);
        y += 15;
        groupNotices = new ChatLayout.Rect(x, y, w, 14);
        shortcut(g, font, groupNotices, "mcphone.chat.contacts.group_notices", mx, my);
        y += 17;
        friendsTab = new ChatLayout.Rect(x, y, w / 2, 12);
        groupsTab = new ChatLayout.Rect(x + w / 2, y, w - w / 2, 12);
        category(g, font, friendsTab, "mcphone.chat.contacts.friends", !groups);
        category(g, font, groupsTab, "mcphone.chat.contacts.groups", groups);
        y += 14;
        var next = ChatClientCache.getConversations();
        var nextGroups = GroupClientCache.groups();
        String nextQuery = search.query();
        if (source != next || groupSource != nextGroups || !query.equals(nextQuery)) {
            if (!query.equals(nextQuery)) pane.reset();
            source = next; groupSource = nextGroups; query = nextQuery;
            sections = directory.sections(groups ? nextGroups.stream()
                    .map(c -> new ChatContactDirectory.Contact(c.id(), c.name(), false)).toList()
                    : next.stream().map(c -> new ChatContactDirectory.Contact(c.id(), c.name(), c.online())).toList(), query);
        }
        pane.render(g, font, sections,
                new ChatLayout.Rect(x, y, w, Math.max(0, tabsY - y - 2)), mx, my,
                groups ? (query.isBlank() ? "mcphone.chat.contacts.groups_empty" : "mcphone.chat.contacts.search_empty")
                        : (query.strip().isEmpty() ? "mcphone.chat.contacts.friends_empty" : "mcphone.chat.contacts.search_empty"),
                groups ? ChatContacts::groupRow : ChatContactPane::renderContact);
        navigation.render(g, font, x, tabsY, w, ChatNavigationBar.Page.CONTACTS);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (notifications) return notices.click(mx, my, button);
        if (search.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        if (add != null && add.contains(mx, my)) pendingAction = Action.ADD_CONTACT;
        else if (newFriends != null && newFriends.contains(mx, my)) pendingAction = Action.NEW_FRIENDS;
        else if (groupNotices != null && groupNotices.contains(mx, my)) {
            notifications = true; add = null; search.close(); navigation.clear(); notices.open();
        } else if (friendsTab != null && friendsTab.contains(mx, my)) setGroups(false);
        else if (groupsTab != null && groupsTab.contains(mx, my)) setGroups(true);
        else if (navigation.click(mx, my, button) == ChatNavigationBar.Page.CHATS) pendingAction = Action.CHATS;
        else if (navigation.click(mx, my, button) == ChatNavigationBar.Page.PROFILE) pendingAction = Action.PROFILE;
        else if (!groups && pane.avatarAt(mx,my,button)!=null) pendingProfile=pane.avatarAt(mx,my,button);
        else if (groups) pendingGroup = pane.click(mx, my, button);
        else pendingPeer = pane.click(mx, my, button);
        return true;
    }
    private void setGroups(boolean value) { if (groups != value) { groups = value; source = null; pane.reset(); } }
    private static void groupRow(GuiGraphics g, Font font, ChatContactDirectory.Contact c, int x, int y, int w, int h, boolean hover) {
        var room = GroupClientCache.summary(c.id()); if (room == null) return;
        ChatUi.glass(g, ChatGlass.Surface.CARD, x, y, w, h - 1, hover);
        var row = ChatContactRow.layout(x, y, w, h, false);
        var avatar = row.avatar();
        ChatGroupAvatar.draw(g, room, avatar.x(), avatar.y(), avatar.width());
        ChatUi.label(g, font, ChatUi.truncate(font, c.name(), row.textWidth(), ChatLayout.TEXT_SCALE),
                row.textX(), row.nameCenterY(), ChatLayout.TEXT_SCALE, FontPalette.title());
        ChatUi.label(g, font, ChatUi.truncate(font, Component.translatable("mcphone.chat.group.members", room.memberCount()).getString(),
                row.textWidth(), ChatLayout.META_SCALE), row.textX(), row.statusCenterY(), ChatLayout.META_SCALE, FontPalette.subtle());
    }
    private static void title(GuiGraphics g, Font font, String key, int x, int y, int w) {
        // 两侧对称预留图标空间，标题中心始终对齐整块内容区，不跟随右侧按钮偏移。
        ChatUi.buttonText(g, font, ChatUi.truncate(font, Component.translatable(key).getString(), w - 28,
                ChatLayout.TEXT_SCALE), x + w / 2f, y + ChatLayout.HEADER_HEIGHT / 2f,
                ChatLayout.TEXT_SCALE, FontPalette.title());
    }
    private static void shortcut(GuiGraphics g, Font font, ChatLayout.Rect r, String key, int mx, int my) {
        ChatUi.glass(g, ChatGlass.Surface.CARD, r.x(), r.y(), r.width(), r.height(), r.contains(mx, my));
        String label = Component.translatable(key).getString();
        if (key.equals("mcphone.chat.contacts.group_notices") && !GroupClientCache.invitations().isEmpty()) label += " (" + GroupClientCache.invitations().size() + ")";
        ChatUi.label(g, font, ChatUi.truncate(font, label, r.width() - 15,
                ChatLayout.TEXT_SCALE), r.x() + 3, r.y() + r.height() / 2f, ChatLayout.TEXT_SCALE, FontPalette.body());
        ChatUi.forwardIcon(g, r.x() + r.width() - 5, r.y() + r.height() / 2f, 6, FontPalette.dim());
    }
    private static void category(GuiGraphics g, Font font, ChatLayout.Rect r, String key, boolean active) {
        int color = active ? ChatUi.accent() : FontPalette.subtle();
        String label = ChatUi.truncate(font, Component.translatable(key).getString(), r.width() - 4, ChatLayout.TEXT_SCALE);
        ChatUi.buttonText(g, font, label, r.x() + r.width() / 2f, r.y() + 5,
                ChatLayout.TEXT_SCALE, color);
        if (active) g.fill(r.x() + 3, r.y() + 11, r.x() + r.width() - 3, r.y() + 12, color);
    }
}
