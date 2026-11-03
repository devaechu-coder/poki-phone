package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.net.MCphoneNetwork;
import com.november.mcphone.feature.chat.net.RequestConversationsPacket;
import com.november.mcphone.feature.chat.group.*;
import com.november.mcphone.feature.chat.group.net.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 群详情协调：成员网格、邀请和改名各自管理视图；服务器确认统一用本页票据匹配。 */
public final class ChatGroupDetails {
    private enum Page { DETAILS, MEMBERS, INVITE, RENAME }
    private enum Operation { EXIT, INVITE, RENAME }
    private final ChatGroupMemberGrid grid = new ChatGroupMemberGrid();
    private final ChatGroupInvite invite = new ChatGroupInvite();
    private final ChatGroupRename rename = new ChatGroupRename();
    private final Consumer<GroupCommand> send;
    private final Supplier<UUID> player;
    private final Runnable requestFriends;
    private UUID group, request, details, pendingProfile;
    private ChatLayout.Rect action, nameRow, more;
    private boolean confirming, finished, known;
    private long started, lastDetails;
    private String status = "";
    private Page page = Page.DETAILS;
    private Operation operation;
    public ChatGroupDetails() {
        this(GroupTransport::send, () -> {
            var current = Minecraft.getInstance().player; return current == null ? null : current.getUUID();
        }, () -> MCphoneNetwork.sendToServer(new RequestConversationsPacket()));
    }
    ChatGroupDetails(Consumer<GroupCommand> send, Supplier<UUID> player) { this(send, player, () -> {}); }
    private ChatGroupDetails(Consumer<GroupCommand> send, Supplier<UUID> player, Runnable requestFriends) {
        this.send = send; this.player = player; this.requestFriends = requestFriends;
    }
    public void open(UUID group) {
        close(); this.group = group; confirming = finished = false; status = ""; page = Page.DETAILS;
        known = GroupClientCache.summary(group) != null; refreshDetails();
        started = System.currentTimeMillis();
    }
    private void refreshDetails() {
        details = UUID.randomUUID(); GroupClientCache.requestDetails(group, details);
        send.accept(new GroupCommand.Details(details, group)); lastDetails = System.currentTimeMillis();
    }
    public void close() {
        if (request != null) GroupClientCache.cancel(request);
        if (group != null) GroupClientCache.cancelDetails(group);
        group = request = details = pendingProfile = null; action = nameRow = more = null; operation = null;
        grid.close(); invite.close(); rename.close();
    }
    public UUID consumeProfileRequest() { var out=pendingProfile;pendingProfile=null;return out; }
    public boolean consumeFinished() { boolean out = finished; finished = false; return out; }
    public boolean back() {
        if (request != null) return true;
        if (page != Page.DETAILS) { returnToDetails(); return true; }
        if (!confirming) return false; confirming = false; return true;
    }
    private void returnToDetails() {
        page = Page.DETAILS; invite.close(); rename.close(); grid.close(); status = ""; confirming = false;
        action = nameRow = more = null;
    }
    private boolean owner(GroupInfo info) { return info.owner().equals(player.get()); }
    private void submit(Operation operation, GroupCommand command) {
        this.operation = operation; request = command.request(); started = System.currentTimeMillis();
        status = ""; GroupClientCache.track(request); send.accept(command);
    }
    private void pollResult() {
        if (request == null) return;
        var result = GroupClientCache.consume(request);
        if (result != null && group.equals(result.group())) {
            if (result.status() == GroupStatus.OK) {
                if (operation == Operation.EXIT) finished = true;
                else {
                    boolean invitation = operation == Operation.INVITE; returnToDetails();
                    if (invitation) status = "mcphone.chat.group.invite_sent";
                }
            } else status = result.status().key();
            request = null; operation = null; confirming = false;
        } else if (System.currentTimeMillis() - started > GroupClientPolicy.OPERATION_TIMEOUT_MS) {
            GroupClientCache.cancel(request); request = null; operation = null; status = "mcphone.chat.group.timeout"; confirming = false;
        }
    }
    public void render(GuiGraphics g, int left, int top, int width, int height, int statusH, int navH,
                       int mx, int my, Font font) {
        pollResult(); GroupTransport.directory();
        if (known && GroupClientCache.summary(group) == null && request == null) { finished = true; return; }
        var info = GroupClientCache.info(); long now = System.currentTimeMillis();
        int x = left + 4, w = width - 8, y = top + statusH + ChatLayout.TOP_GAP, bottom = top + height - navH - 3;
        String title = switch (page) {
            case DETAILS -> "mcphone.chat.group.details_count";
            case MEMBERS -> "mcphone.chat.group.members";
            case INVITE -> "mcphone.chat.group.invite_title";
            case RENAME -> "mcphone.chat.group.rename";
        };
        ChatUi.buttonText(g, font, Component.translatable(title, info == null ? 0 : info.members().size()).getString(),
                x + w / 2f, y + 6, ChatLayout.TEXT_SCALE, FontPalette.title()); y += 16;
        if (info == null || !info.id().equals(group)) {
            var error = GroupClientCache.detailsError();
            if (error == GroupStatus.NOT_MEMBER) { finished = true; return; }
            if (now - lastDetails >= GroupClientPolicy.REFRESH_INTERVAL_MS) refreshDetails();
            ChatUi.text(g, font, ChatUi.truncate(font, Component.translatable(error == null ? now - started > GroupClientPolicy.OPERATION_TIMEOUT_MS ? "mcphone.chat.group.timeout" : "mcphone.chat.group.loading" : error.key()).getString(),
                    w, ChatLayout.TEXT_SCALE), x, y, ChatLayout.TEXT_SCALE, FontPalette.subtle()); return;
        }
        var area = new ChatLayout.Rect(x, y, w, Math.max(0, bottom - y));
        if (page == Page.INVITE) { invite.render(g, font, info, area, mx, my, status, request != null); return; }
        if (page == Page.RENAME) { rename.render(g, font, area, mx, my, status, request != null); return; }
        if (page == Page.MEMBERS) { grid.render(g, font, info.members(), area, true, mx, my); return; }
        renderDetails(g, font, info, area, mx, my);
    }
    private void renderDetails(GuiGraphics g, Font font, GroupInfo info, ChatLayout.Rect area, int mx, int my) {
        int x = area.x(), y = area.y(), w = area.width(), bottom = y + area.height();
        int gridH = Math.min(2, Math.max(1, (bottom - y - 77) / 28)) * 28;
        grid.render(g, font, info.members(), new ChatLayout.Rect(x, y, w, gridH), false, mx, my); y += gridH + 2;
        more = new ChatLayout.Rect(x, y, w, 12);
        ChatUi.buttonText(g, font, Component.translatable("mcphone.chat.group.all_members").getString(), x + w / 2f,
                y + 6, ChatLayout.META_SCALE, more.contains(mx, my) ? ChatUi.accent() : FontPalette.body()); y += 15;
        nameRow = new ChatLayout.Rect(x, y, w, 20);
        ChatUi.glass(g, ChatGlass.Surface.CARD, x, y, w, 20, request == null && nameRow.contains(mx, my));
        String label = Component.translatable("mcphone.chat.group.name").getString();
        ChatUi.label(g, font, label, x + 3, y + 10, ChatLayout.META_SCALE, FontPalette.title());
        int labelW = ChatUi.width(font, label, ChatLayout.META_SCALE);
        String value = ChatUi.truncate(font, info.name(), Math.max(0, w - labelW - 18), ChatLayout.META_SCALE);
        ChatUi.label(g, font, value, x + w - 10 - ChatUi.width(font, value, ChatLayout.META_SCALE),
                y + 10, ChatLayout.META_SCALE, FontPalette.subtle());
        ChatUi.forwardIcon(g, x + w - 5, y + 10, 6, FontPalette.dim());
        action = new ChatLayout.Rect(x, bottom - 14, w, 14);
        String hint = !status.isEmpty() ? status : confirming ? (owner(info) ? "mcphone.chat.group.confirm_dissolve" : "mcphone.chat.group.confirm_leave")
                : "mcphone.chat.group.leave_hint";
        int hintY = Math.max(y + 23, action.y() - 22);
        for (var line : font.split(Component.translatable(hint), ChatLayout.unscaledWidth(w, ChatLayout.META_SCALE))) {
            if (hintY + ChatUi.lineHeight(font, ChatLayout.META_SCALE) > action.y() - 1) break;
            ChatUi.text(g, font, line, x, hintY, ChatLayout.META_SCALE, FontPalette.subtle());
            hintY += ChatUi.lineHeight(font, ChatLayout.META_SCALE) + 1;
        }
        ChatUi.glass(g, ChatGlass.Surface.BUTTON, x, action.y(), w, 14, request == null && action.contains(mx, my));
        ChatUi.buttonText(g, font, Component.translatable(request != null ? "mcphone.chat.group.pending" : confirming ? "mcphone.chat.group.confirm"
                : owner(info) ? "mcphone.chat.group.dissolve" : "mcphone.chat.group.leave").getString(),
                x + w / 2f, action.y() + 7, ChatLayout.TEXT_SCALE, request == null ? FontPalette.danger() : FontPalette.dim());
    }
    public boolean mouseClicked(double mx, double my, int button) {
        var info = GroupClientCache.info(); if (button != 0 || request != null || info == null || !info.id().equals(group)) return true;
        if (page == Page.INVITE) {
            status = "";
            var members = invite.click(mx, my, button);
            if (members != null) {
                if (members.isEmpty()) status = "mcphone.chat.group.select_members";
                else submit(Operation.INVITE, new GroupCommand.Invite(UUID.randomUUID(), group, members));
            }
        } else if (page == Page.RENAME) {
            status = "";
            var value = rename.click(mx, my, button);
            if (value != null) {
                if (!owner(info)) status = GroupStatus.OWNER_REQUIRED.key();
                else if (value.isEmpty()) status = GroupStatus.INVALID.key();
                else submit(Operation.RENAME, new GroupCommand.Rename(UUID.randomUUID(), group, value));
            }
        } else if (grid.memberClicked(mx,my,button)!=null) { pendingProfile=grid.memberClicked(mx,my,button);
        } else if (grid.inviteClicked(mx, my, button)) {
            if (info.members().size() >= GroupLimits.MEMBERS) { status = GroupStatus.LIMIT.key(); return true; }
            confirming = false; status = ""; grid.close(); action = nameRow = more = null;
            page = Page.INVITE; invite.open(); requestFriends.run();
        } else if (page == Page.DETAILS && more != null && more.contains(mx, my)) {
            page = Page.MEMBERS; grid.close(); action = nameRow = more = null;
        } else if (page == Page.DETAILS && nameRow != null && nameRow.contains(mx, my)) {
            if (!owner(info)) status = GroupStatus.OWNER_REQUIRED.key();
            else { confirming = false; status = ""; page = Page.RENAME; rename.open(info.name()); grid.close(); action = nameRow = more = null; }
        } else if (page == Page.DETAILS && action != null && action.contains(mx, my)) {
            if (!confirming) { confirming = true; status = ""; }
            else submit(Operation.EXIT, owner(info) ? new GroupCommand.Dissolve(UUID.randomUUID(), group) : new GroupCommand.Leave(UUID.randomUUID(), group));
        }
        return true;
    }
    public boolean keyPressed(int key, int scan, int modifiers) {
        return request != null || page == Page.INVITE && invite.keyPressed(key, scan, modifiers)
                || page == Page.RENAME && rename.keyPressed(key, scan, modifiers);
    }
    public boolean charTyped(char c, int modifiers) {
        return request != null || page == Page.INVITE && invite.charTyped(c, modifiers)
                || page == Page.RENAME && rename.charTyped(c, modifiers);
    }
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (request != null) return true;
        return page == Page.INVITE ? invite.scroll(mx, my, amount) : grid.scroll(mx, my, amount);
    }
}
