package com.november.mcphone.feature.chat.group.net;

import com.november.mcphone.feature.chat.ChatDeletionResult;
import com.november.mcphone.feature.chat.group.*;
import java.util.List;
import java.util.UUID;

/** 服务端群响应；纯数据，可由专用服务器安全加载。 */
public sealed interface GroupReply {
    record Notices(List<GroupInviteNotice> invitations) implements GroupReply {
        public Notices { invitations = List.copyOf(invitations); if (invitations.size() > GroupLimits.INVITATIONS_PER_PLAYER)
            throw new IllegalArgumentException("群通知超限"); }
    }
    record Directory(List<GroupSummary> groups) implements GroupReply {
        public Directory { groups = List.copyOf(groups); if (groups.size() > GroupLimits.GROUPS_PER_PLAYER) throw new IllegalArgumentException("群摘要超限"); }
    }
    record History(UUID request, UUID group, long revision, long sequence, GroupInfo info,
                   List<GroupMessage> messages) implements GroupReply {
        public History {
            java.util.Objects.requireNonNull(request);
            if (!info.id().equals(group) || revision < 0 || sequence < 0) throw new IllegalArgumentException("非法群历史身份/版本");
            messages = bounded(messages);
            if (messages.stream().anyMatch(m -> m.sequence() > sequence)) throw new IllegalArgumentException("群历史越过服务器序号");
        }
    }
    record Message(UUID request, long revision, GroupSummary summary) implements GroupReply {
        public Message { java.util.Objects.requireNonNull(request); if (revision < 0 || summary.last().isEmpty()) throw new IllegalArgumentException("推送缺少消息/版本"); }
    }
    record Result(UUID request, UUID group, GroupStatus status) implements GroupReply {}
    record Details(UUID request, long revision, GroupInfo info) implements GroupReply {
        public Details { java.util.Objects.requireNonNull(request); java.util.Objects.requireNonNull(info); if (revision < 0) throw new IllegalArgumentException("负的成员版本"); }
        public Details(UUID request, GroupInfo info) { this(request, 0, info); }
    }
    record Updated(long revision, GroupInfo info) implements GroupReply {
        public Updated { java.util.Objects.requireNonNull(info); if (revision < 0) throw new IllegalArgumentException("负的成员版本"); }
    }
    record Deleted(UUID request, UUID group, UUID message, ChatDeletionResult status, long revision,
                   List<GroupMessage> messages) implements GroupReply {
        public Deleted {
            java.util.Objects.requireNonNull(request); java.util.Objects.requireNonNull(group); java.util.Objects.requireNonNull(message); java.util.Objects.requireNonNull(status);
            if (revision < 0) throw new IllegalArgumentException("负的删除版本"); messages = bounded(messages);
        }
    }
    private static List<GroupMessage> bounded(List<GroupMessage> messages) {
        if (messages.size() > GroupLimits.MESSAGES) throw new IllegalArgumentException("群历史超限");
        long previous = 0; var ids = new java.util.HashSet<UUID>();
        for (var m : messages) {
            if (m.sequence() <= previous || !ids.add(m.message().id())) throw new IllegalArgumentException("群历史无序/重复");
            previous = m.sequence();
        }
        return List.copyOf(messages);
    }
}
