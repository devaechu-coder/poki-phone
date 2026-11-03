package com.november.mcphone.feature.chat.group.net;

import com.november.mcphone.feature.chat.ChatMessage;
import com.november.mcphone.feature.chat.ChatDeletionResult;
import com.november.mcphone.feature.chat.group.*;
import java.util.*;
import java.util.function.Consumer;

/** 群会话专用快照。没有客户端类依赖；旧私聊 UUID 和删除票据不能进入这份缓存。 */
public final class GroupClientCache {
    private GroupClientCache() {}
    private static List<GroupSummary> groups = List.of();
    private static List<GroupInviteNotice> invitations = List.of();
    private static UUID noticeRequest;
    private static GroupStatus noticesError;
    private static boolean noticesLoaded;
    private static List<GroupMessage> entries = List.of();
    private static List<ChatMessage> messages = List.of();
    private static UUID open, history, detailRequest, detailGroup, deleting, deleteRequest, infoGroup;
    private static GroupInfo info;
    private static GroupStatus historyError, detailsError;
    private static boolean removed;
    private static long deletionFloor;
    private static long infoRevision;
    private static ChatDeletionResult deleteResult;
    private static final Map<UUID, GroupReply.Result> operations = new LinkedHashMap<>();
    private static Consumer<GroupReply.Message> listener = ignored -> {};
    private static Consumer<GroupReply.Result> resultListener = ignored -> {};
    public static void setMessageListener(Consumer<GroupReply.Message> value) { listener = value; }
    public static void setResultListener(Consumer<GroupReply.Result> value) { resultListener = value; }
    public static List<GroupSummary> groups() { return groups; }
    public static List<GroupInviteNotice> invitations() { return invitations; }
    public static boolean noticesLoaded() { return noticesLoaded; }
    public static GroupStatus noticesError() { return noticesError; }
    public static void requestNotices(UUID request) { noticeRequest = request; noticesError = null; }
    public static List<ChatMessage> messages() { return messages; }
    public static List<GroupMessage> entries() { return entries; }
    public static GroupInfo info() { return info; }
    public static boolean removed() { return removed; }
    public static boolean removed(UUID group) { return Objects.equals(open, group) && removed; }
    public static UUID pendingHistory() { return history; }
    public static GroupStatus consumeHistoryError() { var out = historyError; historyError = null; return out; }
    public static GroupStatus detailsError() { return detailsError; }
    public static GroupSummary summary(UUID id) { return groups.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null); }
    public static String senderName(ChatMessage message) {
        return entries.stream().filter(m -> m.message().id().equals(message.id())).map(GroupMessage::senderName).findFirst().orElse("");
    }
    public static long sequence() { return entries.isEmpty() ? 0 : entries.get(entries.size() - 1).sequence(); }
    public static UUID open(UUID group) { close(); open = group; history = UUID.randomUUID(); return history; }
    /** HUD 在其他界面关闭后恢复绘制，重新绑定当前群；同群连续帧不重拉或清空快照。 */
    public static UUID activate(UUID group) { return Objects.equals(open, group) ? null : open(group); }
    public static void close() {
        open = history = detailRequest = detailGroup = deleting = deleteRequest = infoGroup = null;
        info = null; infoRevision = 0; historyError = detailsError = null; removed = false; deletionFloor = 0; deleteResult = null; setEntries(List.of());
    }
    public static void clear() { close(); groups = List.of(); invitations = List.of(); noticeRequest = null; noticesError = null; noticesLoaded = false; operations.clear(); }
    public static void track(UUID request) {
        if (!operations.containsKey(request) && operations.size() >= GroupClientPolicy.MAX_PENDING_OPERATIONS)
            operations.remove(operations.keySet().iterator().next());
        operations.put(request, null);
    }
    public static void cancel(UUID request) { operations.remove(request); }
    public static GroupReply.Result consume(UUID request) {
        var result = operations.get(request); if (result != null) operations.remove(request); return result;
    }
    public static void requestDetails(UUID group, UUID request) {
        if (!Objects.equals(infoGroup, group)) infoRevision = 0;
        infoGroup = group;
        detailGroup = group; detailRequest = request; info = null; detailsError = null;
    }
    public static void cancelDetails(UUID group) {
        if (Objects.equals(detailGroup, group)) { detailGroup = detailRequest = infoGroup = null; info = null; infoRevision = 0; detailsError = null; }
    }
    public static void beginDeletion(UUID group, UUID message, UUID request) {
        if (!Objects.equals(open, group)) return;
        deleting = message; deleteRequest = request; deleteResult = null;
    }
    public static void cancelDeletion(UUID request) {
        if (Objects.equals(deleteRequest, request)) { deleting = deleteRequest = null; deleteResult = null; }
    }
    public static ChatDeletionResult consumeDeleteResult() {
        var result = deleteResult; if (result != null) { deleting = deleteRequest = null; deleteResult = null; } return result;
    }
    public static UUID deletionRequest() { return deleteRequest; }
    public static void accept(GroupReply reply) {
        if (reply instanceof GroupReply.Notices v) {
            invitations = v.invitations(); noticesLoaded = true; noticeRequest = null; noticesError = null;
        } else if (reply instanceof GroupReply.Directory v) {
            boolean known = summary(open) != null;
            groups = List.copyOf(v.groups());
            if (open != null && known && summary(open) == null) { removed = true; info = null; setEntries(List.of()); }
        } else if (reply instanceof GroupReply.History v) {
            if (!Objects.equals(open, v.group()) || !Objects.equals(history, v.request()) || v.revision() < deletionFloor) return;
            history = null; historyError = null;
            if (detailGroup == null || Objects.equals(detailGroup, v.group())) updateInfo(v.revision(), v.info());
            setEntries(merge(v.messages(), v.sequence()));
        } else if (reply instanceof GroupReply.Message v) {
            if (operations.containsKey(v.request()))
                operations.put(v.request(),new GroupReply.Result(v.request(),v.summary().id(),GroupStatus.OK));
            var next = new ArrayList<>(groups); next.removeIf(g -> g.id().equals(v.summary().id())); next.add(v.summary());
            next.sort(Comparator.comparingLong(GroupSummary::lastTime).reversed().thenComparing(GroupSummary::id));
            groups = List.copyOf(next.subList(0, Math.min(next.size(), GroupLimits.GROUPS_PER_PLAYER)));
            if (Objects.equals(open, v.summary().id()) && !removed && v.revision() >= deletionFloor) {
                var entry = v.summary().last().orElseThrow(); var all = new ArrayList<>(entries);
                if (all.stream().noneMatch(m -> m.message().id().equals(entry.message().id()))) { all.add(entry); setEntries(all); }
            }
            listener.accept(v);
        } else if (reply instanceof GroupReply.Details v) {
            if (Objects.equals(detailRequest, v.request()) && Objects.equals(detailGroup, v.info().id())) { updateInfo(v.revision(), v.info()); detailRequest = null; detailsError = null; }
        } else if (reply instanceof GroupReply.Updated v) {
            UUID target = detailGroup == null ? open : detailGroup;
            if (Objects.equals(target, v.info().id()) && !removed(v.info().id())) updateInfo(v.revision(), v.info());
        } else if (reply instanceof GroupReply.Deleted v) {
            if (!Objects.equals(open, v.group()) || !Objects.equals(deleting, v.message()) || !Objects.equals(deleteRequest, v.request())) return;
            deleteResult = v.status();
            if (v.status() == ChatDeletionResult.OK) {
                deletionFloor = Math.max(deletionFloor, v.revision());
                long last = v.messages().stream().mapToLong(GroupMessage::sequence).max().orElse(0);
                var merged = merge(v.messages(), last).stream().filter(m -> !m.message().id().equals(v.message())).toList();
                setEntries(merged);
            }
        } else if (reply instanceof GroupReply.Result v) {
            if (Objects.equals(noticeRequest, v.request())) { noticesError = v.status(); noticeRequest = null; }
            if (Objects.equals(history, v.request()) && Objects.equals(open, v.group())) historyError = v.status();
            if (Objects.equals(detailRequest, v.request()) && Objects.equals(detailGroup, v.group())) detailsError = v.status();
            if (operations.containsKey(v.request())) operations.put(v.request(), v);
            if (v.status() == GroupStatus.NOT_MEMBER && Objects.equals(open, v.group()) && Objects.equals(history, v.request())) removed = true;
            resultListener.accept(v);
        }
    }
    private static void updateInfo(long revision, GroupInfo next) {
        if (!Objects.equals(infoGroup, next.id())) { infoGroup = next.id(); infoRevision = 0; }
        if (revision >= infoRevision) { info = next; infoRevision = revision; }
    }
    private static List<GroupMessage> merge(List<GroupMessage> snapshot, long through) {
        var byId = new LinkedHashMap<UUID, GroupMessage>(); snapshot.forEach(m -> byId.put(m.message().id(), m));
        entries.stream().filter(m -> m.sequence() > through).forEach(m -> byId.put(m.message().id(), m));
        return List.copyOf(byId.values());
    }
    private static void setEntries(List<GroupMessage> value) {
        var sorted = value.stream().sorted(Comparator.comparingLong(GroupMessage::sequence)).toList();
        entries = List.copyOf(sorted.subList(Math.max(0, sorted.size() - GroupLimits.MESSAGES), sorted.size()));
        messages = entries.stream().map(GroupMessage::message).toList();
    }
}
