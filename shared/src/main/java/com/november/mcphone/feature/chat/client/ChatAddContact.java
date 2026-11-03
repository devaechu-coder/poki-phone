package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.PhoneTheme;
import com.november.mcphone.core.client.PlayerAvatar;
import com.november.mcphone.core.net.MCphoneNetwork;
import com.november.mcphone.feature.chat.net.ChatClientCache;
import com.november.mcphone.feature.chat.net.FriendRequestPacket;
import com.november.mcphone.feature.chat.net.OnlinePlayer;
import com.november.mcphone.feature.chat.net.Relation;
import com.november.mcphone.feature.chat.net.RemoveFriendPacket;
import com.november.mcphone.feature.chat.net.RequestOnlinePlayersPacket;
import com.november.mcphone.feature.chat.net.RespondFriendRequestPacket;
import com.november.mcphone.core.client.GuiUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 加联系人界面：列出在线玩家，点一下加为联系人。
 * 所有操作只发包、不改本地状态，按钮以服务端回发的列表为准。
 * 悬停记的是整条 OnlinePlayer 而不是行号：列表每 3 秒被整份换掉，按下标重查会点到别人。
 */
public final class ChatAddContact {

    private static final int PAD = 4;

    private static final long REFRESH_INTERVAL_MS = 3000L;

    private static final int AVATAR_SIZE = ChatLayout.AVATAR_SIZE;

    private static final int AVATAR_GAP = 3;

    private static int colorName() { return FontPalette.title(); }
    private static int colorAdd() { return FontPalette.confirm(); }
    private static int colorAccept() { return FontPalette.armed(); }
    private static int colorPending() { return FontPalette.subtle(); }
    private static int colorRemove() { return FontPalette.danger(); }
    private static final int COLOR_ROW_HOVER = PhoneTheme.COLOR_ROW_HOVER;

    private long lastRequestMs;
    private int scrollOffset;

    /** 鼠标停在哪一条上（连同它当时的关系），null 表示没有。理由见类注释 */
    private OnlinePlayer hovered;
    private final boolean requestsOnly;

    public ChatAddContact() { this(false); }
    private ChatAddContact(boolean requestsOnly) { this.requestsOnly = requestsOnly; }
    /** 独立申请页复用同一套行与发包逻辑，避免同意申请出现两个实现。 */
    public static ChatAddContact newFriends() { return new ChatAddContact(true); }
    static List<OnlinePlayer> requestPlayers(List<OnlinePlayer> players) {
        return players.stream().filter(p -> p.relation() == Relation.REQUEST_RECEIVED
                || p.relation() == Relation.REQUEST_SENT).toList();
    }
    private List<OnlinePlayer> players() {
        var list = ChatClientCache.getOnlinePlayers();
        return requestsOnly ? requestPlayers(list) : list;
    }

    public void open() {
        scrollOffset = 0;
        hovered = null;
        lastRequestMs = 0L;   // 置零＝下一帧立即请求
    }

    public void close() {
        hovered = null;
    }

    public void render(GuiGraphics g, int phoneLeft, int phoneTop,
                       int screenW, int screenH, int statusH, int navH,
                       int mouseX, int mouseY, Font font) {

        maybeRefresh();

        final int x = phoneLeft + PAD;
        final int w = screenW - PAD * 2;
        final int bottom = phoneTop + screenH - navH;
        int y = phoneTop + statusH + ChatLayout.TOP_GAP;

        String title = ChatUi.truncate(font, Component.translatable(requestsOnly
                ? "mcphone.chat.contacts.new_friends" : "mcphone.chat.add_contact").getString(),
                w, ChatLayout.TEXT_SCALE);
        ChatUi.buttonText(g, font, title, x + w / 2f,
                y + ChatLayout.HEADER_HEIGHT / 2f,
                ChatLayout.TEXT_SCALE, FontPalette.title());
        y += ChatLayout.HEADER_HEIGHT;
        g.fill(x, y, x + w, y + 1, PhoneTheme.COLOR_DIVIDER);
        y += ChatLayout.SECTION_GAP;

        List<OnlinePlayer> players = players();

        if (requestsOnly) {
            for (var line : font.split(Component.translatable("mcphone.chat.contacts.requests_online_hint"),
                    ChatLayout.unscaledWidth(w, ChatLayout.META_SCALE))) {
                ChatUi.text(g, font, line, x, y, ChatLayout.META_SCALE, FontPalette.dim());
                y += ChatUi.lineHeight(font, ChatLayout.META_SCALE);
            }
            y += 2;
        }

        if (ChatClientCache.isOnlineListTruncated()) {
            for (var line : font.split(Component.translatable("mcphone.chat.truncated",
                    players.size(), ChatClientCache.getTotalOnline()),
                    ChatLayout.unscaledWidth(w, ChatLayout.TEXT_SCALE))) {
                ChatUi.text(g, font, line, x, y, ChatLayout.TEXT_SCALE, FontPalette.notice());
                y += ChatUi.lineHeight(font, ChatLayout.TEXT_SCALE);
            }
            y += 2;
        }

        if (players.isEmpty()) {
            for (var line : font.split(Component.translatable(requestsOnly
                    ? "mcphone.chat.contacts.requests_empty" : "mcphone.chat.online_empty"),
                    ChatLayout.unscaledWidth(w, ChatLayout.TEXT_SCALE))) {
                ChatUi.text(g, font, line, x, y, ChatLayout.TEXT_SCALE, FontPalette.subtle());
                y += ChatUi.lineHeight(font, ChatLayout.TEXT_SCALE);
            }
            hovered = null;
            return;
        }

        clampScroll(players.size(), bottom - y);
        renderRows(g, font, players, x, y, w, bottom, mouseX, mouseY);
    }

    private void renderRows(GuiGraphics g, Font font, List<OnlinePlayer> players,
                            int x, int y, int w, int bottom, int mouseX, int mouseY) {
        final int rowH = rowHeight();
        hovered = null;

        for (int i = scrollOffset; i < players.size(); i++) {
            if (y + rowH > bottom) break;

            OnlinePlayer p = players.get(i);
            boolean isHovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + rowH;
            if (isHovered) {
                hovered = p;
                g.fill(x, y, x + w, y + rowH, COLOR_ROW_HOVER);
            }

            String action = Component.translatable(actionKey(p.relation())).getString();
            int actionW = ChatUi.width(font, action, ChatLayout.TEXT_SCALE);

            // 全是在线玩家，不画状态点
            int avatarY = y + (rowH - AVATAR_SIZE) / 2;
            PlayerAvatar.draw(g, p.id(), x, avatarY, AVATAR_SIZE);

            int nameX = x + AVATAR_SIZE + AVATAR_GAP;
            float centerY = y + rowH / 2f;

            String name = ChatUi.truncate(font, p.name(), w - (nameX - x) - actionW - 6, ChatLayout.TEXT_SCALE);
            ChatUi.label(g, font, name, nameX, centerY, ChatLayout.TEXT_SCALE, colorName());
            ChatUi.label(g, font, action, x + w - actionW - 2, centerY,
                    ChatLayout.TEXT_SCALE, actionColor(p.relation()));

            y += rowH;
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;

        OnlinePlayer p = hovered;
        if (p == null) return false;

        switch (p.relation()) {
            case NONE -> MCphoneNetwork.sendToServer(new FriendRequestPacket(p.id()));
            case REQUEST_RECEIVED ->
                    MCphoneNetwork.sendToServer(new RespondFriendRequestPacket(p.id(), true));
            case FRIEND -> MCphoneNetwork.sendToServer(new RemoveFriendPacket(p.id()));
            // 已发出的申请只能等对方处理，重复发包没用
            case REQUEST_SENT -> { }
        }
        return true;
    }

    private static String actionKey(Relation relation) {
        return switch (relation) {
            case NONE -> "mcphone.chat.add_action";
            case REQUEST_SENT -> "mcphone.chat.request_pending";
            case REQUEST_RECEIVED -> "mcphone.chat.accept_action";
            case FRIEND -> "mcphone.chat.remove_action";
        };
    }

    private static int actionColor(Relation relation) {
        return switch (relation) {
            case NONE -> colorAdd();
            case REQUEST_SENT -> colorPending();
            case REQUEST_RECEIVED -> colorAccept();
            case FRIEND -> colorRemove();
        };
    }

    public boolean mouseScrolled(double scrollY) {
        if (scrollY > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        }
        if (scrollY < 0 && scrollOffset < players().size() - 1) {
            scrollOffset++;
            return true;
        }
        return false;
    }

    private void maybeRefresh() {
        long now = System.currentTimeMillis();
        if (now - lastRequestMs < REFRESH_INTERVAL_MS) return;

        lastRequestMs = now;
        MCphoneNetwork.sendToServer(new RequestOnlinePlayersPacket());
    }

    private static int rowHeight() {
        return AVATAR_SIZE + 2;
    }

    private void clampScroll(int total, int availableHeight) {
        int visible = Math.max(1, availableHeight / rowHeight());
        int maxOffset = Math.max(0, total - visible);
        if (scrollOffset > maxOffset) scrollOffset = maxOffset;
        if (scrollOffset < 0) scrollOffset = 0;
    }

}
