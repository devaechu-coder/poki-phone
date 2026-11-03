package com.november.mcphone.feature.chat;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import com.november.mcphone.feature.chat.net.*;
import com.november.mcphone.feature.chat.group.net.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.util.*;

/** 同时运行于两代 codec：旧记录、新引用、单层快照、有界请求及缓冲区消费。 */
public final class ChatReplyCodecTest {
    private static int checks;
    private static void check(boolean ok,String why) { checks++;if(!ok)throw new AssertionError(why); }
    public static void main(String[] args) {
        UUID a=new UUID(0,1),b=new UUID(0,2);
        var original=ChatMessage.text(a,"你好\n世界😀",100);
        var quote=ChatQuote.from(original,"Alice");
        check(quote.text().equals("你好 世界😀"),"摘要换行变空格，不能把词语粘连");
        var reply=new ChatMessage(b,200,new TextBody("回复",Optional.of(quote)));
        var encoded=ChatMessage.CODEC.encodeStart(JsonOps.INSTANCE,reply).result().orElseThrow();
        check(encoded.getAsJsonObject().has("body"),"引用必须写现代格式，不能丢失快照");
        check(ChatMessage.CODEC.parse(JsonOps.INSTANCE,encoded).result().orElseThrow().equals(reply),"引用持久化往返");
        var ordinary=ChatMessage.CODEC.encodeStart(JsonOps.INSTANCE,original).result().orElseThrow();
        check(ordinary.getAsJsonObject().has("text"),"普通文字继续沿用旧格式");
        var old=TextBody.MAP_CODEC.codec().parse(JsonOps.INSTANCE,JsonParser.parseString("{\"text\":\"old\"}")).result().orElseThrow();
        check(old.quote().isEmpty()&&old.text().equals("old"),"缺引用的旧正文可读");
        var nested=ChatQuote.from(reply,"Bob");
        check(nested.text().equals("回复")&&!nested.text().contains("你好"),"引用回复只取正文，不递归复制引用");
        var image=ChatQuote.from(new ChatMessage(a,300,new ImageBody(b,32,32,1,0)),"Alice");
        check(image.image()&&image.text().isEmpty(),"图片只保存类型摘要，不依赖像素文件");
        for(var value:List.of(Optional.<ChatQuote>empty(),Optional.of(quote),Optional.of(image))) {
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try { ChatReplyWire.write(buffer,value);check(ChatReplyWire.read(buffer).equals(value),"引用网络往返");check(!buffer.isReadable(),"引用读取完整消费字段"); }
            finally { buffer.release(); }
        }
        for(var id:List.of(Optional.<UUID>empty(),Optional.of(original.id()))) {
            var command=new GroupCommand.SendText(b,a,"hello",id);
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try { GroupWire.writeCommand(command,buffer);check(GroupWire.readCommand(buffer).equals(command),"群引用请求仅传消息 ID");check(!buffer.isReadable(),"群请求无残留字节"); }
            finally { buffer.release(); }
        }
        try { new ChatQuote(a,b,"n","x".repeat(257),false);throw new AssertionError("拒绝超限快照"); }
        catch(IllegalArgumentException expected){checks++;}
        var invalid=ChatQuote.CODEC.encodeStart(JsonOps.INSTANCE,quote).result().orElseThrow().getAsJsonObject();
        invalid.addProperty("name","x".repeat(ChatQuote.NAME_LIMIT+1));
        check(ChatQuote.CODEC.parse(JsonOps.INSTANCE,invalid).error().isPresent(),"损坏存档的超长引用返回解码错误而非构造异常");
        var invalidBody=JsonParser.parseString("{\"text\":\"reply\"}").getAsJsonObject();invalidBody.add("quote",invalid);
        check(TextBody.MAP_CODEC.codec().parse(JsonOps.INSTANCE,invalidBody).error().isPresent(),"已存在但损坏的引用不能被当作旧消息静默丢弃");
        try { new SendChatMessagePacket(a,"reply",Optional.of(b),Optional.empty());throw new AssertionError("引用缺少票据应拒绝"); }
        catch(IllegalArgumentException expected){checks++;}
        try { new SendChatMessagePacket(a,"plain",Optional.empty(),Optional.of(b));throw new AssertionError("普通消息不能夹带引用票据"); }
        catch(IllegalArgumentException expected){checks++;}
        var truncated=new FriendlyByteBuf(Unpooled.buffer());
        try { truncated.writeBoolean(true);try {ChatReplyWire.read(truncated);throw new AssertionError("拒绝截断引用");}catch(IndexOutOfBoundsException expected){checks++;} }
        finally {truncated.release();}
        System.out.println("全部通过："+checks+" 条断言");
    }
}
