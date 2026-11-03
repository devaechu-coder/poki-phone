package com.november.mcphone.feature.chat.group.net;

import com.november.mcphone.feature.chat.ChatImage;
import com.november.mcphone.feature.chat.TextBody;
import com.november.mcphone.feature.chat.group.GroupLimits;
import java.util.List;
import java.util.UUID;

/** 类型明确的群请求；唯一请求票据用于匹配创建、删除和异步上传结果。 */
public sealed interface GroupCommand {
    UUID request();
    default UUID group() { return null; }
    record ListRooms(UUID request) implements GroupCommand { public ListRooms { required(request); } }
    record Notices(UUID request) implements GroupCommand { public Notices { required(request); } }
    record RespondInvitation(UUID request, UUID group, UUID invitation, boolean accept) implements GroupCommand {
        public RespondInvitation { required(request, group, invitation); }
    }
    record Create(UUID request, String name, List<UUID> members) implements GroupCommand {
        public Create { required(request); bounded(name, GroupLimits.NAME_LENGTH); members = List.copyOf(members);
            if (members.isEmpty() || members.size() >= GroupLimits.MEMBERS) throw new IllegalArgumentException("建群人数超限"); }
    }
    record History(UUID request, UUID group) implements GroupCommand { public History { required(request, group); } }
    record SendText(UUID request, UUID group, String text, java.util.Optional<UUID> reply) implements GroupCommand {
        public SendText(UUID request, UUID group, String text) { this(request,group,text,java.util.Optional.empty()); }
        public SendText { java.util.Objects.requireNonNull(reply); required(request, group); bounded(text, TextBody.MAX_LENGTH); }
    }
    record Delete(UUID request, UUID group, UUID message) implements GroupCommand { public Delete { required(request, group, message); } }
    record MarkRead(UUID request, UUID group, long sequence) implements GroupCommand { public MarkRead { required(request, group); } }
    record Details(UUID request, UUID group) implements GroupCommand { public Details { required(request, group); } }
    record Invite(UUID request, UUID group, List<UUID> members) implements GroupCommand {
        public Invite { required(request, group); members = List.copyOf(members);
            if (members.isEmpty() || members.size() >= GroupLimits.MEMBERS) throw new IllegalArgumentException("邀请人数超限"); }
    }
    record Leave(UUID request, UUID group) implements GroupCommand { public Leave { required(request, group); } }
    record Dissolve(UUID request, UUID group) implements GroupCommand { public Dissolve { required(request, group); } }
    record Rename(UUID request, UUID group, String name) implements GroupCommand {
        public Rename { required(request, group); bounded(name, GroupLimits.NAME_LENGTH); }
    }
    record Images(UUID request, UUID group, List<UUID> images) implements GroupCommand {
        public Images { required(request, group); images = List.copyOf(images); if (images.size() > GroupLimits.IMAGE_REQUEST_IDS) throw new IllegalArgumentException("图片批量超限"); }
    }
    record Upload(UUID request, UUID group, int width, int height, int frames, int frameMs,
                  int index, int count, byte[] data) implements GroupCommand {
        public Upload { required(request, group); data = data.clone();
            // 解码上限是固定协议容量；实际配置在服务端接收首片时检查，不能因热改配置踢掉玩家。
            if (data.length > ChatImage.CHUNK_BYTES || count < 1 || count > ChatImage.MAX_BYTES_CEILING / ChatImage.CHUNK_BYTES + 1
                    || index < 0 || index >= count) throw new IllegalArgumentException("图片分片超限"); }
        @Override public byte[] data() { return data.clone(); }
    }
    private static void required(UUID... ids) { for (var id : ids) java.util.Objects.requireNonNull(id); }
    private static void bounded(String value, int max) {
        if (value == null || value.length() > max) throw new IllegalArgumentException("文本超过协议上限");
    }
}
