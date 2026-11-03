package com.november.mcphone.feature.chat.profile.net;
import com.november.mcphone.feature.chat.client.profile.ProfileClientCache;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;
public final class ProfileProtocolTest {
    private static int checks;
    private static void check(boolean ok,String why) { checks++;if (!ok) throw new AssertionError(why); }
    public static void main(String[] args) {
        UUID player=new UUID(0,1),other=new UUID(0,2),id=new UUID(0,3);
        for (boolean write:new boolean[]{false,true}) for (String text:write?new String[]{"","你好😀","x".repeat(160)}:new String[]{""}) {
            var q=new ProfileWire.Query(id,player,write,text);var b=new FriendlyByteBuf(Unpooled.buffer());
            try { ProfileWire.writeQuery(q,b);check(ProfileWire.readQuery(b).equals(q),"请求往返与空介绍更新");check(b.readableBytes()==0,"请求没有尾部遗漏"); } finally { b.release(); }
        }
        for (var status:ProfileWire.Status.values()) {
            var reply=new ProfileWire.Reply(id,player,status,"November","公开介绍😀");var b=new FriendlyByteBuf(Unpooled.buffer());
            try { ProfileWire.writeReply(reply,b);check(ProfileWire.readReply(b).equals(reply),"响应往返"); } finally { b.release(); }
        }
        ProfileClientCache.clear();UUID ticket=ProfileClientCache.begin(player,100);
        ProfileClientCache.accept(new ProfileWire.Reply(ticket,other,ProfileWire.Status.OK,"错误玩家","不应显示"),101);
        check(ProfileClientCache.consume(ticket)==null,"相同票据不同目标拒收");
        var good=new ProfileWire.Reply(ticket,player,ProfileWire.Status.OK,"玩家","介绍");ProfileClientCache.accept(good,101);
        check(ProfileClientCache.consume(ticket).equals(good),"仅当前目标确认有效");check(ProfileClientCache.consume(ticket)==null,"回声只消费一次");
        ticket=ProfileClientCache.begin(player,100);ProfileClientCache.cancel(ticket);
        ProfileClientCache.accept(new ProfileWire.Reply(ticket,player,ProfileWire.Status.OK,"晚到","介绍"),102);check(ProfileClientCache.consume(ticket)==null,"关闭页后不接收晚到回声");
        ticket=ProfileClientCache.begin(player,100);ProfileClientCache.accept(new ProfileWire.Reply(ticket,player,ProfileWire.Status.OK,"超时","介绍"),6100);
        check(ProfileClientCache.consume(ticket)==null,"到期回声拒收");
        ticket=ProfileClientCache.begin(player,100);ProfileClientCache.clear();ProfileClientCache.accept(new ProfileWire.Reply(ticket,player,ProfileWire.Status.OK,"旧服务器","介绍"),101);
        check(ProfileClientCache.consume(ticket)==null,"断线后旧回声拒收");
        for (int i=0;i<8;i++) ProfileClientCache.begin(player,100);
        boolean full=false;try { ProfileClientCache.begin(player,100); } catch (IllegalStateException e) { full=true; }check(full,"请求数量有界");
        check(ProfileClientCache.begin(player,6100)!=null,"过期票据被清理");ProfileClientCache.clear();
        System.out.println("全部通过："+checks+" 条断言");
    }
}
