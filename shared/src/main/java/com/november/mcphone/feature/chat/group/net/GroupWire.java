package com.november.mcphone.feature.chat.group.net;

import com.november.mcphone.core.net.Wire;
import com.november.mcphone.feature.chat.ChatMessage;
import com.november.mcphone.feature.chat.ChatImage;
import com.november.mcphone.feature.chat.ChatDeletionResult;
import com.november.mcphone.feature.chat.TextBody;
import com.november.mcphone.feature.chat.group.*;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** 群协议的公共有界部分；消息正文 codec 由版本层注入，不把 StreamCodec 带进 shared。 */
public final class GroupWire {
    private GroupWire() {}
    public static void writeCommand(GroupCommand value, FriendlyByteBuf b) {
        b.writeUUID(value.request());
        int kind = value instanceof GroupCommand.ListRooms ? 0 : value instanceof GroupCommand.Create ? 1
                : value instanceof GroupCommand.History ? 2 : value instanceof GroupCommand.SendText ? 3
                : value instanceof GroupCommand.Delete ? 4 : value instanceof GroupCommand.MarkRead ? 5
                : value instanceof GroupCommand.Details ? 6 : value instanceof GroupCommand.Leave ? 7
                : value instanceof GroupCommand.Dissolve ? 8 : value instanceof GroupCommand.Rename ? 9
                : value instanceof GroupCommand.Images ? 10 : value instanceof GroupCommand.Upload ? 11
                : value instanceof GroupCommand.Invite ? 12 : value instanceof GroupCommand.Notices ? 13
                : value instanceof GroupCommand.RespondInvitation ? 14 : -1;
        if (kind < 0) throw new IllegalArgumentException("群请求尚未登记编码");
        b.writeVarInt(kind);
        if (value.group() != null) b.writeUUID(value.group());
        if (value instanceof GroupCommand.Create v) {
            b.writeUtf(v.name(), GroupLimits.NAME_LENGTH); Wire.writeList(b, v.members(), GroupLimits.MEMBERS - 1, (id, buf) -> buf.writeUUID(id));
        } else if (value instanceof GroupCommand.SendText v) { b.writeUtf(v.text(), TextBody.MAX_LENGTH); com.november.mcphone.feature.chat.net.ChatReplyWire.writeId(b,v.reply()); }
        else if (value instanceof GroupCommand.Delete v) b.writeUUID(v.message());
        else if (value instanceof GroupCommand.MarkRead v) b.writeVarLong(v.sequence());
        else if (value instanceof GroupCommand.Rename v) b.writeUtf(v.name(), GroupLimits.NAME_LENGTH);
        else if (value instanceof GroupCommand.Invite v) Wire.writeList(b, v.members(), GroupLimits.MEMBERS - 1, (id, buf) -> buf.writeUUID(id));
        else if (value instanceof GroupCommand.RespondInvitation v) { b.writeUUID(v.invitation()); b.writeBoolean(v.accept()); }
        else if (value instanceof GroupCommand.Images v) Wire.writeList(b, v.images(), GroupLimits.IMAGE_REQUEST_IDS, (id, buf) -> buf.writeUUID(id));
        else if (value instanceof GroupCommand.Upload v) {
            b.writeVarInt(v.width()); b.writeVarInt(v.height()); b.writeVarInt(v.frames()); b.writeVarInt(v.frameMs());
            b.writeVarInt(v.index()); b.writeVarInt(v.count()); Wire.writeBytes(b, v.data(), ChatImage.CHUNK_BYTES);
        }
    }
    public static GroupCommand readCommand(FriendlyByteBuf b) {
        var request = b.readUUID(); int kind = b.readVarInt();
        if (kind == 0) return new GroupCommand.ListRooms(request);
        if (kind == 1) return new GroupCommand.Create(request, b.readUtf(GroupLimits.NAME_LENGTH), Wire.readList(b, GroupLimits.MEMBERS - 1, buf -> buf.readUUID()));
        if (kind == 13) return new GroupCommand.Notices(request);
        if (kind < 2 || kind > 14) throw new DecoderException("未知群请求 " + kind);
        var group = b.readUUID();
        return switch (kind) {
            case 2 -> new GroupCommand.History(request, group);
            case 3 -> new GroupCommand.SendText(request, group, b.readUtf(TextBody.MAX_LENGTH), com.november.mcphone.feature.chat.net.ChatReplyWire.readId(b));
            case 4 -> new GroupCommand.Delete(request, group, b.readUUID());
            case 5 -> new GroupCommand.MarkRead(request, group, b.readVarLong());
            case 6 -> new GroupCommand.Details(request, group);
            case 7 -> new GroupCommand.Leave(request, group);
            case 8 -> new GroupCommand.Dissolve(request, group);
            case 9 -> new GroupCommand.Rename(request, group, b.readUtf(GroupLimits.NAME_LENGTH));
            case 10 -> new GroupCommand.Images(request, group, Wire.readList(b, GroupLimits.IMAGE_REQUEST_IDS, buf -> buf.readUUID()));
            case 11 -> new GroupCommand.Upload(request, group, b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), Wire.readBytes(b, ChatImage.CHUNK_BYTES));
            case 12 -> new GroupCommand.Invite(request, group, Wire.readList(b, GroupLimits.MEMBERS - 1, buf -> buf.readUUID()));
            default -> new GroupCommand.RespondInvitation(request, group, b.readUUID(), b.readBoolean());
        };
    }
    public static void writeReply(GroupReply value, FriendlyByteBuf b, BiConsumer<ChatMessage, FriendlyByteBuf> message) {
        int kind = value instanceof GroupReply.Directory ? 0 : value instanceof GroupReply.History ? 1
                : value instanceof GroupReply.Message ? 2 : value instanceof GroupReply.Result ? 3
                : value instanceof GroupReply.Details ? 4 : value instanceof GroupReply.Deleted ? 5
                : value instanceof GroupReply.Updated ? 6 : value instanceof GroupReply.Notices ? 7 : -1;
        if (kind < 0) throw new IllegalArgumentException("群响应尚未登记编码");
        b.writeVarInt(kind);
        if (value instanceof GroupReply.Directory v) Wire.writeList(b, v.groups(), GroupLimits.GROUPS_PER_PLAYER, (s, buf) -> summary(s, buf, message));
        else if (value instanceof GroupReply.History v) {
            b.writeUUID(v.request()); b.writeUUID(v.group()); b.writeVarLong(v.revision()); b.writeVarLong(v.sequence()); info(v.info(), b);
            Wire.writeList(b, v.messages(), GroupLimits.MESSAGES, (m, buf) -> entry(m, buf, message));
        } else if (value instanceof GroupReply.Message v) {
            b.writeUUID(v.request()); b.writeVarLong(v.revision()); summary(v.summary(), b, message);
        } else if (value instanceof GroupReply.Result v) {
            b.writeUUID(v.request()); b.writeBoolean(v.group() != null); if (v.group() != null) b.writeUUID(v.group()); b.writeVarInt(v.status().wireId());
        } else if (value instanceof GroupReply.Details v) { b.writeUUID(v.request()); b.writeVarLong(v.revision()); info(v.info(), b); }
        else if (value instanceof GroupReply.Updated v) { b.writeVarLong(v.revision()); info(v.info(), b); }
        else if (value instanceof GroupReply.Notices v) Wire.writeList(b, v.invitations(), GroupLimits.INVITATIONS_PER_PLAYER, GroupWire::notice);
        else if (value instanceof GroupReply.Deleted v) {
            b.writeUUID(v.request()); b.writeUUID(v.group()); b.writeUUID(v.message()); b.writeVarInt(v.status().wireId()); b.writeVarLong(v.revision());
            Wire.writeList(b, v.messages(), GroupLimits.MESSAGES, (m, buf) -> entry(m, buf, message));
        }
    }
    public static GroupReply readReply(FriendlyByteBuf b, Function<FriendlyByteBuf, ChatMessage> message) {
        return switch (b.readVarInt()) {
            case 0 -> new GroupReply.Directory(Wire.readList(b, GroupLimits.GROUPS_PER_PLAYER, buf -> summary(buf, message)));
            case 1 -> new GroupReply.History(b.readUUID(), b.readUUID(), nonnegative(b.readVarLong()), nonnegative(b.readVarLong()), info(b), Wire.readList(b, GroupLimits.MESSAGES, buf -> entry(buf, message)));
            case 2 -> new GroupReply.Message(b.readUUID(), nonnegative(b.readVarLong()), summary(b, message));
            case 3 -> new GroupReply.Result(b.readUUID(), b.readBoolean() ? b.readUUID() : null, GroupStatus.fromWire(b.readVarInt()));
            case 4 -> new GroupReply.Details(b.readUUID(), nonnegative(b.readVarLong()), info(b));
            case 6 -> new GroupReply.Updated(nonnegative(b.readVarLong()), info(b));
            case 7 -> new GroupReply.Notices(Wire.readList(b, GroupLimits.INVITATIONS_PER_PLAYER, GroupWire::notice));
            case 5 -> new GroupReply.Deleted(b.readUUID(), b.readUUID(), b.readUUID(), ChatDeletionResult.fromWire(b.readVarInt()), nonnegative(b.readVarLong()), Wire.readList(b, GroupLimits.MESSAGES, buf -> entry(buf, message)));
            default -> throw new DecoderException("未知群响应");
        };
    }
    private static void notice(GroupInviteNotice v, FriendlyByteBuf b) {
        b.writeUUID(v.id()); b.writeUUID(v.group()); b.writeUtf(v.name(), GroupLimits.NAME_LENGTH);
        b.writeUUID(v.inviter().id()); b.writeUtf(v.inviter().name(), GroupLimits.NAME_LENGTH);
        b.writeVarLong(v.createdAt()); b.writeVarLong(v.expiresAt());
    }
    private static GroupInviteNotice notice(FriendlyByteBuf b) {
        return new GroupInviteNotice(b.readUUID(), b.readUUID(), b.readUtf(GroupLimits.NAME_LENGTH),
                new GroupMember(b.readUUID(), b.readUtf(GroupLimits.NAME_LENGTH)), nonnegative(b.readVarLong()), nonnegative(b.readVarLong()));
    }
    private static void entry(GroupMessage m, FriendlyByteBuf b, BiConsumer<ChatMessage, FriendlyByteBuf> codec) {
        b.writeVarLong(m.sequence()); b.writeUtf(m.senderName(), GroupLimits.NAME_LENGTH); codec.accept(m.message(), b);
    }
    private static GroupMessage entry(FriendlyByteBuf b, Function<FriendlyByteBuf, ChatMessage> codec) {
        return new GroupMessage(b.readVarLong(), b.readUtf(GroupLimits.NAME_LENGTH), codec.apply(b));
    }
    private static void info(GroupInfo v, FriendlyByteBuf b) {
        b.writeUUID(v.id()); b.writeUtf(v.name(), GroupLimits.NAME_LENGTH); b.writeUUID(v.owner());
        Wire.writeList(b, v.members(), GroupLimits.MEMBERS, (m, buf) -> { buf.writeUUID(m.id()); buf.writeUtf(m.name(), GroupLimits.NAME_LENGTH); });
    }
    private static GroupInfo info(FriendlyByteBuf b) {
        return new GroupInfo(b.readUUID(), b.readUtf(GroupLimits.NAME_LENGTH), b.readUUID(),
                Wire.readList(b, GroupLimits.MEMBERS, buf -> new GroupMember(buf.readUUID(), buf.readUtf(GroupLimits.NAME_LENGTH))));
    }
    private static void summary(GroupSummary v, FriendlyByteBuf b, BiConsumer<ChatMessage, FriendlyByteBuf> codec) {
        b.writeUUID(v.id()); b.writeUtf(v.name(), GroupLimits.NAME_LENGTH); b.writeUUID(v.owner()); b.writeVarInt(v.memberCount());
        Wire.writeList(b, v.portraits(), 4, (id, buf) -> buf.writeUUID(id)); b.writeBoolean(v.last().isPresent());
        v.last().ifPresent(m -> entry(m, b, codec)); b.writeVarInt(v.unread());
    }
    private static GroupSummary summary(FriendlyByteBuf b, Function<FriendlyByteBuf, ChatMessage> codec) {
        return new GroupSummary(b.readUUID(), b.readUtf(GroupLimits.NAME_LENGTH), b.readUUID(), b.readVarInt(),
                Wire.readList(b, 4, buf -> buf.readUUID()), b.readBoolean() ? Optional.of(entry(b, codec)) : Optional.empty(), b.readVarInt());
    }
    private static long nonnegative(long value) { if (value < 0) throw new DecoderException("负的群版本/序号"); return value; }
}
