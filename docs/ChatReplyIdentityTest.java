package com.november.mcphone.feature.chat;

import java.util.*;
import net.minecraft.nbt.CompoundTag;

/** 冻结已发布的旧 ID 算法，并验证提交票据的作者、会话与内容约束。 */
public final class ChatReplyIdentityTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args) {
        UUID a=new UUID(0,1),b=new UUID(0,2),ticket=new UUID(0,3);
        var legacy=List.of(new ChatMessage(ChatMessageIdentity.MISSING_ID,a,100,new TextBody("旧消息")),
                new ChatMessage(ChatMessageIdentity.MISSING_ID,b,101,new ImageBody(b,32,16)));
        var key=ConversationKey.of(a,b);var migrated=ChatMessageIdentity.normalize(key,legacy).messages();
        check(migrated.get(0).id().equals(UUID.fromString("f5b8529b-6db8-3539-b67d-72811eb5f208")),"旧文本 ID 与 1.11.2 金样一致");
        check(migrated.get(1).id().equals(UUID.fromString("d4b70a57-c1d6-3d5b-a311-00ed61366bd9")),"旧图片 ID 与 1.11.2 金样一致");
        check(!ChatMessageIdentity.normalize(key,migrated).changed(),"迁移可重复执行且不再改 ID");
        var deletion=new ChatDeletionData();deletion.hide(a,b,migrated.get(0).id());
        var restored=ChatDeletionData.load(deletion.write(new CompoundTag()));
        check(restored.visible(a,b,ChatMessageIdentity.normalize(key,legacy).messages()).size()==1,"旧聊天未回存但删除数据先回存，重载仍隐藏原消息");
        UUID id=ChatReplyIdentity.messageId(a,b,false,ticket);
        check(id.equals(ChatReplyIdentity.messageId(a,b,false,ticket)),"相同票据可重试且跨重启稳定");
        check(!id.equals(ChatReplyIdentity.messageId(b,b,false,ticket)),"票据绑定真实作者");
        check(!id.equals(ChatReplyIdentity.messageId(a,a,false,ticket)),"票据绑定会话");
        check(!id.equals(ChatReplyIdentity.messageId(a,b,true,ticket)),"群聊和私聊使用不同命名域");
        var quote=ChatQuote.from(migrated.get(0),"A");
        var sent=new ChatMessage(id,a,200,new TextBody("回复",Optional.of(quote)));
        check(ChatReplyIdentity.matches(sent,a,quote.message(),"回复"),"重试相同内容可复用已保存消息");
        check(!ChatReplyIdentity.matches(sent,b,quote.message(),"回复"),"其他作者不能复用");
        check(!ChatReplyIdentity.matches(sent,a,b,"回复"),"重试不能修改引用目标");
        check(!ChatReplyIdentity.matches(sent,a,quote.message(),"改写"),"重试不能修改已提交正文");
        check(!ChatReplyIdentity.matches(ChatMessage.text(a,"回复",200),a,quote.message(),"回复"),"普通正文不能冒充引用提交");
        System.out.println("全部通过："+checks+" 条断言");
    }
}
