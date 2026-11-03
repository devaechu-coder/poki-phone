package com.november.mcphone.feature.chat.group.net;

import com.november.mcphone.feature.chat.group.GroupStatus;
import com.november.mcphone.feature.chat.profile.net.ProfileWire;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;

/** 固定已使用的字节契约，枚举重排后往返测试也会通过，因此另检验真实编号。 */
public final class ChatWireCompatibilityTest {
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    public static void main(String[] args) {
        UUID request = new UUID(0, 1), target = new UUID(0, 2);
        String[] groups = {"OK", "INVALID", "NOT_MEMBER", "OWNER_REQUIRED", "OWNER_MUST_DISSOLVE", "NOT_FRIEND",
                "LIMIT", "NO_PHONE", "BUSY", "UNAVAILABLE", "INVITATION_GONE", "NOT_INVITED"};
        for (int code = 0; code < groups.length; code++) {
            var status = GroupStatus.valueOf(groups[code]);
            var bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                GroupWire.writeReply(new GroupReply.Result(request, target, status), bytes, (m, b) -> {});
                check(bytes.readVarInt() == 3 && bytes.readUUID().equals(request) && bytes.readBoolean()
                        && bytes.readUUID().equals(target) && bytes.readVarInt() == code && bytes.readableBytes() == 0,
                        "群状态保持原字节编号 " + groups[code]);
                bytes.clear(); bytes.writeVarInt(3); bytes.writeUUID(request); bytes.writeBoolean(false); bytes.writeVarInt(code);
                check(((GroupReply.Result) GroupWire.readReply(bytes, b -> null)).status() == status, "固定字节解码 " + groups[code]);
            } finally { bytes.release(); }
        }
        String[] profiles = {"OK", "FORBIDDEN", "INVALID", "BUSY", "UNAVAILABLE"};
        for (int code = 0; code < profiles.length; code++) {
            var status = ProfileWire.Status.valueOf(profiles[code]);
            var bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ProfileWire.writeReply(new ProfileWire.Reply(request, target, status, "A", "B"), bytes);
                check(bytes.readUUID().equals(request) && bytes.readUUID().equals(target) && bytes.readVarInt() == code,
                        "资料状态保持原字节编号 " + profiles[code]);
                bytes.clear(); bytes.writeUUID(request); bytes.writeUUID(target); bytes.writeVarInt(code); bytes.writeUtf("A"); bytes.writeUtf("B");
                check(ProfileWire.readReply(bytes).status() == status && bytes.readableBytes() == 0, "固定资料字节解码");
            } finally { bytes.release(); }
        }
        for (int bad : new int[]{-1, 12, 99}) {
            var bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                bytes.writeVarInt(3); bytes.writeUUID(request); bytes.writeBoolean(false); bytes.writeVarInt(bad);
                rejects(() -> GroupWire.readReply(bytes, b -> null), "未知群状态");
            } finally { bytes.release(); }
        }
        for (int bad : new int[]{-1, 5, 99}) {
            var bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                bytes.writeUUID(request); bytes.writeUUID(target); bytes.writeVarInt(bad); bytes.writeUtf(""); bytes.writeUtf("");
                rejects(() -> ProfileWire.readReply(bytes), "未知资料状态");
            } finally { bytes.release(); }
        }
        System.out.println("全部通过：" + checks + " 条断言");
    }
    private static void rejects(Runnable action, String why) {
        try { action.run(); throw new AssertionError(why); }
        catch (io.netty.handler.codec.DecoderException expected) { checks++; }
    }
}
