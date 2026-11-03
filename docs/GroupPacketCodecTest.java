package com.november.mcphone.feature.chat.group.net;

import com.november.mcphone.feature.chat.*;
import com.november.mcphone.feature.chat.group.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/** 四平台实际包包装往返；反射只适配两种 codec 入口，不替换生产编解码。 */
public final class GroupPacketCodecTest {
    private static int checks;
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), G = new UUID(1, 1), R = new UUID(2, 1);
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static Object codec(Class<?> type) throws Exception {
        try { return type.getField("STREAM_CODEC").get(null); } catch (NoSuchFieldException legacy) { return null; }
    }
    private static void encode(Object packet, FriendlyByteBuf buf) throws Exception {
        var type = packet.getClass(); var codec = codec(type);
        if (codec == null) type.getMethod("encode", type, FriendlyByteBuf.class).invoke(null, packet, buf);
        else Class.forName("net.minecraft.network.codec.StreamCodec").getMethod("encode", Object.class, Object.class).invoke(codec, buf, packet);
    }
    private static Object decode(Class<?> type, FriendlyByteBuf buf) throws Exception {
        var codec = codec(type);
        return codec == null ? type.getMethod("decode", FriendlyByteBuf.class).invoke(null, buf)
                : Class.forName("net.minecraft.network.codec.StreamCodec").getMethod("decode", Object.class).invoke(codec, buf);
    }
    private static void round(Object packet) throws Exception {
        var b = new FriendlyByteBuf(Unpooled.buffer());
        try { encode(packet, b); var decoded = decode(packet.getClass(), b); check(packet.equals(decoded) && b.readableBytes() == 0, "真实包装完整往返：" + packet); }
        finally { b.release(); }
    }
    private static void rejects(Runnable action, String why) {
        try { action.run(); throw new AssertionError("应拒绝：" + why); } catch (RuntimeException expected) { checks++; }
    }
    public static void main(String[] args) throws Exception {
        List<GroupCommand> commands = List.of(new GroupCommand.ListRooms(R), new GroupCommand.Create(R, "中文群", List.of(B)),
                new GroupCommand.History(R, G), new GroupCommand.SendText(R, G, "中文\nEnglish"), new GroupCommand.Delete(R, G, A),
                new GroupCommand.MarkRead(R, G, 888), new GroupCommand.Details(R, G), new GroupCommand.Leave(R, G),
                new GroupCommand.Dissolve(R, G), new GroupCommand.Rename(R, G, "新群名"), new GroupCommand.Images(R, G, List.of(A, B)),
                new GroupCommand.Invite(R, G, List.of(A, B)), new GroupCommand.Notices(R),
                new GroupCommand.RespondInvitation(R, G, B, true), new GroupCommand.RespondInvitation(R, G, B, false));
        for (var c : commands) round(new GroupRequestPacket(c));
        byte[] data = new byte[ChatImage.CHUNK_BYTES]; Arrays.fill(data, (byte) 91);
        var upload = new GroupRequestPacket(new GroupCommand.Upload(R, G, 512, 256, 2, 200, 0, 1, data));
        var bytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            encode(upload, bytes); check(bytes.readableBytes() < 32767, "最大分片包装仍低于 C2S 包长限制");
            var decoded = (GroupRequestPacket) decode(GroupRequestPacket.class, bytes);
            var chunk = (GroupCommand.Upload) decoded.value();
            check(Arrays.equals(chunk.data(), data) && chunk.group().equals(G) && chunk.request().equals(R), "分片正文与作用域真实往返");
        } finally { bytes.release(); }
        var ceiling = new GroupRequestPacket(new GroupCommand.Upload(R, G, 1, 1, 1, 0, 0,
                ChatImage.MAX_BYTES_CEILING / ChatImage.CHUNK_BYTES + 1, new byte[]{1}));
        var ceilingBytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            encode(ceiling, ceilingBytes); var decoded = (GroupRequestPacket) decode(GroupRequestPacket.class, ceilingBytes);
            check(((GroupCommand.Upload) decoded.value()).count() == ChatImage.MAX_BYTES_CEILING / ChatImage.CHUNK_BYTES + 1,
                    "真实解码按固定协议容量，运行配置在服务端首片检查");
        } finally { ceilingBytes.release(); }
        var info = new GroupInfo(G, "中文群", A, List.of(new GroupMember(A, "Alice"), new GroupMember(B, "Bob")));
        var text = new GroupMessage(1, "Bob", new ChatMessage(A, B, 1000, new TextBody("你好\nEnglish")));
        var image = new GroupMessage(2, "Alice", new ChatMessage(B, A, 1000, new ImageBody(G, 32, 16, 2, 100)));
        var summary = new GroupSummary(G, "中文群", A, 2, List.of(A, B), Optional.of(image), 1);
        List<GroupReply> replies = List.of(new GroupReply.Directory(List.of(summary)), new GroupReply.History(R, G, 3, 2, info, List.of(text, image)),
                new GroupReply.Message(R, 3, summary), new GroupReply.Result(R, G, GroupStatus.NOT_MEMBER),
                new GroupReply.Result(R, null, GroupStatus.LIMIT), new GroupReply.Details(R, 42, info), new GroupReply.Updated(43, info),
                new GroupReply.Notices(List.of(new GroupInviteNotice(B, G, "中文群", new GroupMember(A, "Alice"), 1234, 5678))),
                new GroupReply.Deleted(R, G, text.message().id(), ChatDeletionResult.OK, 4, List.of(image)),
                new GroupReply.Deleted(R, G, text.message().id(), ChatDeletionResult.FORBIDDEN, 0, List.of()));
        for (var reply : replies) round(new GroupResponsePacket(reply));
        rejects(() -> new GroupCommand.SendText(R, G, "x".repeat(TextBody.MAX_LENGTH + 1)), "正文上限");
        rejects(() -> new GroupCommand.Create(R, "x".repeat(33), List.of(B)), "群名上限");
        rejects(() -> new GroupCommand.Images(R, G, Collections.nCopies(21, A)), "请求图片上限");
        rejects(() -> new GroupCommand.Invite(R, G, List.of()), "不能空邀请");
        rejects(() -> new GroupCommand.Invite(R, G, Collections.nCopies(128, A)), "邀请人数上限");
        rejects(() -> new GroupReply.Updated(-1, info), "资料版本不能为负");
        rejects(() -> new GroupReply.Notices(Collections.nCopies(65, new GroupInviteNotice(B, G, "群", new GroupMember(A, "A"), 1, 2))), "群通知人数上限");
        rejects(() -> new GroupInviteNotice(B, G, "群", new GroupMember(A, "A"), 2, 1), "邀请到期时间不能早于创建时间");
        rejects(() -> new GroupCommand.Upload(R, G, 1, 1, 1, 0, 0, 1, new byte[ChatImage.CHUNK_BYTES + 1]), "分片上限");
        var malformed = new FriendlyByteBuf(Unpooled.buffer());
        try { malformed.writeUUID(R); malformed.writeVarInt(99); final var bad = malformed; rejects(() -> GroupWire.readCommand(bad), "未知命令种类"); }
        finally { malformed.release(); }
        var excessiveNotices = new FriendlyByteBuf(Unpooled.buffer());
        try {
            excessiveNotices.writeVarInt(7); excessiveNotices.writeVarInt(65);
            rejects(() -> GroupWire.readReply(excessiveNotices, b -> null), "群通知声明数量先校验再分配");
        } finally { excessiveNotices.release(); }
        malformed = new FriendlyByteBuf(Unpooled.buffer());
        try {
            malformed.writeUUID(R); malformed.writeVarInt(1); malformed.writeUtf("群"); malformed.writeVarInt(128);
            final var bad = malformed; rejects(() -> GroupWire.readCommand(bad), "先检查列表声明数量再分配");
        } finally { malformed.release(); }
        rejects(() -> new GroupReply.History(R, new UUID(9, 9), 3, 2, info, List.of(text)), "历史资料必须属于目标群");
        rejects(() -> new GroupReply.History(R, G, 3, 1, info, List.of(image)), "历史不能超过服务器序号");
        rejects(() -> new GroupReply.History(R, G, 3, 2, info, List.of(image, text)), "历史必须按序号升序");
        System.out.println("全部通过：" + checks + " 条断言");
    }
}
