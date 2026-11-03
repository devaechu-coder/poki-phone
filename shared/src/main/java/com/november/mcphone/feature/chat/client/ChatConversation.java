package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.GuiUtil;
import com.november.mcphone.core.client.PhoneTheme;
import com.november.mcphone.feature.chat.ChatMessage;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import com.november.mcphone.feature.chat.client.messageaction.ChatMessageActions;
import com.november.mcphone.feature.chat.TextBody;
import com.november.mcphone.feature.chat.ImageBody;
import com.november.mcphone.feature.chat.client.favorite.*;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/** 单个会话的协调者：生命周期、标题、已读/摘要请求和输入分发。组件各自持有页面内状态。 */
public final class ChatConversation {
    private static final int PAD = 4;
    private static final int INPUT_GAP = 2;
    private static final long REFRESH_INTERVAL_MS = 3000L;
    private ChatConversationChannel channel;
    private final ChatReplyDraft reply = new ChatReplyDraft();
    private ChatLayout.Rect cancelReply;
    public ChatConversation() { composer.sendGuard(this::canSendReply); composer.clearAfterSend(() -> submission == null); }
    private ChatReplySubmission submission;
    private final ChatMessagePane messages = new ChatMessagePane();
    private final ChatImageViewer viewer = new ChatImageViewer();
    private final ChatComposer composer = new ChatComposer(this::send,
            () -> Minecraft.getInstance().keyboardHandler.getClipboard(),
            (g,x,y,w,h) -> ChatUi.glass(g, ChatGlass.Surface.MENU, x,y,w,h,false));
    private final ChatMessageActions actions = new ChatMessageActions(
            (g, x, y, w, h) -> ChatUi.glass(g, ChatGlass.Surface.MENU, x, y, w, h, false),
            text -> Minecraft.getInstance().keyboardHandler.setClipboard(text),
            (target, message, request) -> {
                if (channel != null) channel.delete(message, request);
            }, request -> { if (channel != null) channel.cancelDelete(request); },
            key -> { var player = Minecraft.getInstance().player;
                if (player != null) com.november.mcphone.platform.PlayerAccess.message(player, Component.translatable(key), true); }, this::favorite, this::replyTo);
    private UUID peer;
    private long lastRequestMs;
    private List<ChatMessage> markedFrom;
    private boolean backHovered, pendingBack, moreHovered, pendingDetails, profileSuspended;

    public void open(UUID peer) {
        open(ChatConversationChannel.direct(peer));
    }
    public void openGroup(UUID group) { open(ChatConversationChannel.room(group)); }
    private void open(ChatConversationChannel channel) {
        close();
        this.channel = channel;
        UUID peer = channel.target();
        this.peer = peer;
        lastRequestMs = System.currentTimeMillis();
        resetComponents(true);
        // 先绑定对端再拉历史，否则抢先到达的推送会被缓存丢弃。
        channel.open();
        markedFrom = channel.messages();
    }

    public void close() {
        cancelSubmission();
        if (channel != null) channel.close();
        channel = null;
        peer = null;
        markedFrom = null;
        resetComponents(false);
        ChatImageCache.beginFrame(null);
    }

    private void resetComponents(boolean focused) {
        reply.clear(); cancelReply=null; submission=null;
        actions.reset();
        messages.reset();
        viewer.dismiss();
        composer.reset(focused);
        backHovered = pendingBack = moreHovered = pendingDetails = profileSuspended = false;
    }

    public UUID peer() { return peer; }
    public String title() { return channel == null ? "" : channel.title(); }
    public boolean isGroup() { return channel != null && channel.group(); }
    public boolean profileSuspended() { return profileSuspended; }
    /** 私聊暂离到对方资料：保留同一输入框、引用、回声票据及消息滚动状态。 */
    public boolean suspendForProfile(UUID target) {
        if (channel == null || isGroup() || !isViewing(target)) return false;
        profileSuspended = true;
        actions.dismiss();
        composer.dismissContextMenu(); composer.dismissAttachmentMenu(); composer.blur();
        messages.clearSelection(); viewer.dismiss(); cancelReply = null;
        backHovered = pendingBack = moreHovered = pendingDetails = false;
        return true;
    }
    public boolean resumeFromProfile(UUID target) {
        if (!profileSuspended || !isViewing(target)) return false;
        profileSuspended = false;
        composer.focus();
        return true;
    }
    public boolean consumeDetailsRequest() { boolean out = pendingDetails; pendingDetails = false; return out; }
    public boolean isViewing(UUID other) { return peer != null && peer.equals(other); }
    public boolean dismissViewer() { return viewer.dismiss(); }
    public boolean hasContextMenu() { return actions.isOpen() || composer.hasContextMenu(); }
    public boolean dismissContextMenu() {
        boolean closed = actions.dismiss();
        return composer.dismissContextMenu() || closed;
    }
    public ChatAttachment consumeAttachRequest() { return composer.consumeAttachRequest(); }
    public boolean consumeBackRequest() {
        boolean out = pendingBack;
        pendingBack = false;
        return out;
    }

    public void render(GuiGraphics g, int phoneLeft, int phoneTop,
                       int screenW, int screenH, int statusH, int navH,
                       int mouseX, int mouseY, float partialTick, Font font) {
        if (peer == null) return;
        if (channel.activate()) lastRequestMs = System.currentTimeMillis();
        String error = channel.consumeError();
        if (error != null && Minecraft.getInstance().player != null)
            com.november.mcphone.platform.PlayerAccess.message(Minecraft.getInstance().player, Component.translatable(error), true);
        tick();
        maybeRefresh();
        maybeMarkRead();
        int x = phoneLeft + PAD, w = screenW - PAD * 2;
        int inputTop = phoneTop + screenH - navH - ChatLayout.INPUT_HEIGHT - INPUT_GAP;
        int replyTop = inputTop - (reply.quote() == null ? 0 : ChatReplyView.DRAFT_HEIGHT);
        int top = renderHeader(g, font, x, phoneTop + statusH + ChatLayout.TOP_GAP, w, mouseX, mouseY);
        ChatImageCache.beginFrame(peer);
        messages.render(g, font, x, top, w, replyTop - INPUT_GAP, peer, selfId(), channel.group(), channel.messages(), channel::senderName);
        cancelReply=null;
        if (reply.quote() != null) {
            ChatUi.glass(g,ChatGlass.Surface.CARD,x,replyTop,w,ChatReplyView.DRAFT_HEIGHT-2,false);
            ChatReplyView.draw(g,font,reply.quote(),x+3,replyTop,w-17);
            cancelReply=new ChatLayout.Rect(x+w-12,replyTop,12,ChatReplyView.DRAFT_HEIGHT-2);
            ChatUi.buttonText(g,font,"×",x+w-6,replyTop+9,ChatLayout.TEXT_SCALE,FontPalette.body());
        }
        composer.render(g, font, x, inputTop, w, mouseX, mouseY, partialTick);
        if (viewer.isOpen()) ChatImageCache.beginFrame(peer);
        viewer.render(g, font, phoneLeft, phoneTop + statusH, screenW,
                screenH - statusH - navH, mouseX, mouseY);
        // 查看器遮住消息区时只取放大图；最后统一按当前会话发请求。
        ChatImageCache.flushRequests(peer, isGroup());
        int menuX = phoneLeft + PAD, menuY = phoneTop + statusH + ChatLayout.TOP_GAP;
        int menuW = screenW - PAD * 2, menuH = screenH - statusH - navH - ChatLayout.TOP_GAP * 2;
        actions.render(g, font, menuX, menuY, menuW, menuH, mouseX, mouseY);
        composer.renderContextMenu(g, font, menuX, menuY, menuW, menuH, mouseX, mouseY);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (peer == null) return false;
        if (viewer.mouseClicked(button)) return true;
        if (composer.contextMouseClicked(mx, my, button)) return true;
        if (actions.mouseClicked(mx, my, button, System.currentTimeMillis())) return true;
        if (button == 0 && cancelReply != null && cancelReply.contains(mx,my)) { reply.clear(); cancelReply=null; return true; }
        if (button == 1) {
            if (composer.openContextMenu(mx, my)) { messages.clearSelection(); return true; }
            ChatMessage target = messages.messageAt(mx, my);
            if (target != null) {
                messages.prepareMenu(target.id());
                composer.dismissAttachmentMenu();
                composer.blur();
                actions.open(peer, target.id(), target.body() instanceof TextBody text ? text.text() : null,
                        messages.selectedText(target.id()), mx, my);
            }
            return true;
        }
        if (button == 0 && backHovered) { pendingBack = true; return true; }
        if (button == 0 && moreHovered) { pendingDetails = true; return true; }
        if (composer.mouseClicked(button)) { messages.clearSelection(); return true; }
        UUID image = button == 0 ? messages.imageAt(mx, my) : null;
        if (image != null) {
            messages.clearSelection();
            composer.blur();
            viewer.open(image);
            return true;
        }
        if (button == 0 && messages.mouseClicked(mx, my)) { composer.blur(); return true; }
        composer.focusInput(mx, my, button);
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button) {
        return peer != null && (viewer.isOpen() || hasContextMenu() || messages.mouseDragged(mx, my, button));
    }
    public boolean mouseReleased(double mx, double my, int button) {
        return peer != null && (viewer.isOpen() || hasContextMenu() || messages.mouseReleased(mx, my, button));
    }

    /** 查看器、消息菜单和粘贴菜单是模态层；菜单未关闭时不能修改或发送草稿。 */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (peer == null) return false;
        if (viewer.isOpen() || hasContextMenu()) return true;
        if (Screen.isCopy(keyCode) && !messages.selectedText().isEmpty()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(messages.selectedText());
            return true;
        }
        return composer.keyPressed(keyCode, scanCode, modifiers);
    }
    public boolean charTyped(char c, int modifiers) {
        return peer != null && (viewer.isOpen() || hasContextMenu() || composer.charTyped(c, modifiers));
    }
    public boolean mouseScrolled(double amount) {
        if (peer == null) return false;
        if (dismissContextMenu()) return true;
        return viewer.isOpen() || messages.mouseScrolled(amount);
    }

    /** 服务端回声才插入消息，避免校验拒收后客户端还留下假消息。 */
    private void send(String text) {
        if (peer == null) return;
        if (reply.id().isEmpty()) {
            cancelSubmission();
            channel.send(text);
        } else {
            UUID original = reply.id().orElseThrow();
            var draft = composer.draft();
            long now = submissionTime();
            if (submission == null || !submission.matches(original,draft)) {
                cancelSubmission();
                submission = new ChatReplySubmission(original,draft,selfId(),peer,channel.group(),now);
            } else submission.retry(now);
            channel.send(text,reply.id(),java.util.Optional.of(submission.ticket()));
        }
        messages.latest();
    }

    /** 位置是独立的普通文字消息，不提交或清理输入框里尚未发送的引用草稿。 */
    public void sendLocation() {
        var player = Minecraft.getInstance().player;
        if (player != null) sendLocation(player.blockPosition());
    }
    void sendLocation(BlockPos position) {
        if (channel == null || profileSuspended) return;
        channel.send("X: " + position.getX() + ", Y: " + position.getY() + ", Z: " + position.getZ());
        messages.latest();
    }

    private void replyTo(UUID target, UUID message) {
        if (channel == null || submission != null && !submission.timedOut() || !channel.target().equals(target)) return;
        var original=channel.messages().stream().filter(m -> m.id().equals(message)).findFirst().orElse(null);
        if (original == null) return;
        cancelSubmission();
        reply.select(com.november.mcphone.feature.chat.ChatQuote.from(original,messageSenderName(original)));
        messages.clearSelection(); composer.dismissAttachmentMenu(); composer.focus(); cancelReply=null;
    }
    /** 资料页期间继续处理确认和超时；不拉历史、不标记隐藏消息已读。 */
    public void tick() { if (channel != null) tick(selfId()); }
    void tick(UUID self) {
        if (channel == null) return;
        var receipt = channel.receipt();
        if (receipt != null) actions.result(receipt.request(), receipt.result());
        pollSubmission(self);
        actions.tick(System.currentTimeMillis());
        actions.reconcile(channel.messages());
    }
    private void pollSubmission(UUID self) {
        if (submission == null) return;
        var status = channel.replyStatus(submission.ticket());
        var result=submission.poll(channel.messages(),self,submissionTime());
        if (status == ChatConversationChannel.ReplyStatus.ACCEPTED || result == ChatReplySubmission.Result.ACCEPTED) {
            if (submission.complete(reply,composer::clearSubmitted)) cancelReply=null;
            cancelSubmission();
            return;
        }
        if (status != null) {
            submission.rejected();
            // GroupTransport 统一提示文字请求的错误；页面只恢复交互，避免重复提示。
        } else if (result == ChatReplySubmission.Result.TIMEOUT && Minecraft.getInstance().player != null)
            com.november.mcphone.platform.PlayerAccess.message(Minecraft.getInstance().player,Component.translatable("mcphone.chat.reply.timeout"),true);
    }
    private void cancelSubmission() {
        if (submission != null && channel != null) channel.cancelReply(submission.ticket());
        submission = null;
    }
    private static long submissionTime() { return System.nanoTime() / 1_000_000L; }
    private boolean canSendReply() {
        if (channel == null || selfId() == null || submission != null && !submission.timedOut()) return false;
        if (submission != null && reply.id().isPresent() && submission.matches(reply.id().get(),composer.draft())) return true;
        if (reply.id().isEmpty() || channel.messages().stream().anyMatch(m -> m.id().equals(reply.id().get()))) return true;
        var player=Minecraft.getInstance().player;
        if (player != null) com.november.mcphone.platform.PlayerAccess.message(player,Component.translatable("mcphone.chat.reply.unavailable"),true);
        reply.clear(); cancelReply=null; return false;
    }

    private int renderHeader(GuiGraphics g, Font font, int x, int y, int w, int mx, int my) {
        backHovered = GuiUtil.hit(mx, my, x, y, 12, ChatLayout.HEADER_HEIGHT);
        int backSize = 7;
        ChatUi.sizedIcon(g, ChatUi.Icon.BACK, x + (12 - backSize) / 2f,
                y + (ChatLayout.HEADER_HEIGHT - backSize) / 2f, backSize,
                backHovered ? ChatUi.accent() : FontPalette.body());
        moreHovered = GuiUtil.hit(mx, my, x + w - 12, y, 12, ChatLayout.HEADER_HEIGHT);
        ChatUi.sizedIcon(g, ChatUi.Icon.MORE, x + w - 12 + (12 - ChatLayout.ICON_SIZE) / 2f,
                y + (ChatLayout.HEADER_HEIGHT - ChatLayout.ICON_SIZE) / 2f, ChatLayout.ICON_SIZE,
                moreHovered ? ChatUi.accent() : FontPalette.body());
        String suffix = channel.titleSuffix();
        String name = ChatUi.truncate(font, channel.title(), w - 30 - ChatUi.width(font, suffix, ChatLayout.TEXT_SCALE), ChatLayout.TEXT_SCALE) + suffix;
        ChatUi.buttonText(g, font, name, x + w / 2f,
                y + ChatLayout.HEADER_HEIGHT / 2f,
                ChatLayout.TEXT_SCALE, FontPalette.title());
        y += ChatLayout.HEADER_HEIGHT;
        g.fill(x, y, x + w, y + 1, PhoneTheme.COLOR_DIVIDER);
        return y + ChatLayout.SECTION_GAP;
    }

    private void maybeRefresh() {
        long now = System.currentTimeMillis();
        if (now - lastRequestMs < REFRESH_INTERVAL_MS) return;

        lastRequestMs = now;
        channel.refresh();
        // 高速连发时已读请求可能被限流；周期性补发，未读不会一直残留。
        if (!channel.messages().isEmpty()) channel.read();
    }

    private void maybeMarkRead() {
        List<ChatMessage> src = channel.messages();
        if (src == markedFrom || peer == null) return;

        markedFrom = src;
        channel.read();
    }

    private void favorite(UUID target, UUID message) {
        if (channel == null || !channel.target().equals(target)) return;
        var source = channel.messages().stream().filter(m -> m.id().equals(message)).findFirst().orElse(null);
        if (source == null) return;
        try {
            FavoriteStore store=ChatFavorites.current();
            if(store==null) { favoriteNotice("loading");return; }
            FavoriteEntry entry;byte[] data;
            if(source.body() instanceof TextBody text) {
                data=text.text().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                entry=new FavoriteEntry(message,target,channel.group(),source.sender(),messageSenderName(source),
                        source.time(),System.currentTimeMillis(),FavoriteEntry.Kind.TEXT,data.length,0,0,1,0);
            } else if(source.body() instanceof ImageBody image) {
                if(ChatImageCache.status(image.image())!=ChatImageCache.Status.READY || ChatImageCache.bytes(image.image())==null) {
                    favoriteNotice("image_unavailable");return;
                }
                data=ChatImageCache.bytes(image.image()).clone();
                entry=new FavoriteEntry(message,target,channel.group(),source.sender(),messageSenderName(source),
                        source.time(),System.currentTimeMillis(),FavoriteEntry.Kind.IMAGE,data.length,
                        image.width(),image.height(),image.frames(),image.frameMs());
            } else return;
            Object connection=Minecraft.getInstance().getConnection();
            var account=selfId();
            favoriteNotice("saving");
            store.submit(net.minecraft.Util.backgroundExecutor(),()->store.add(entry,data)).whenComplete((result,error)->
                Minecraft.getInstance().execute(()->{
                    if(connection!=Minecraft.getInstance().getConnection() || !java.util.Objects.equals(account,selfId())) return;
                    favoriteNotice(error==null?result.name().toLowerCase(java.util.Locale.ROOT):"storage_error");
                }));
        } catch (RuntimeException error) { favoriteNotice("storage_error"); }
    }
    private static void favoriteNotice(String result) {
        var player = Minecraft.getInstance().player;
        if (player != null) com.november.mcphone.platform.PlayerAccess.message(player,Component.translatable("mcphone.chat.favorite."+result),true);
    }

    private String messageSenderName(ChatMessage message) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.getUUID().equals(message.sender())) return player.getName().getString();
        if (channel.group()) return channel.senderName(message);
        return com.november.mcphone.feature.chat.net.ChatClientCache.getConversations().stream()
            .filter(c -> c.id().equals(message.sender())).map(c -> c.name()).findFirst().orElse(message.sender().toString().substring(0,8));
    }

    private static UUID selfId() {
        var player = Minecraft.getInstance().player;
        return player == null ? null : player.getUUID();
    }
}
