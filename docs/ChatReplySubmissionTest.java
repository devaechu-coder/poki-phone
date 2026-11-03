package com.november.mcphone.feature.chat.client;

import com.november.mcphone.feature.chat.*;
import java.util.*;

/** 相同正文、迟到回声、重试和新草稿之间必须按提交身份隔离。 */
public final class ChatReplySubmissionTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args) {
        UUID a=new UUID(0,1),b=new UUID(0,2);
        var original=ChatMessage.text(b,"原文",100);
        var quote=ChatQuote.from(original,"Bob");
        var raw=new ChatComposer.Draft("回复",4);
        var submit=new ChatReplySubmission(original.id(),raw,a,b,false,1000);
        var body=new TextBody("回复",Optional.of(quote));
        var old=new ChatMessage(a,200,body);
        check(submit.poll(List.of(old),a,1001)==ChatReplySubmission.Result.WAITING,"相同正文但不同 ID 不是本次回声");
        check(submit.poll(List.of(new ChatMessage(submit.message(),b,201,body)),a,1002)==ChatReplySubmission.Result.WAITING,"其他作者不能确认己方发送");
        var accepted=new ChatMessage(submit.message(),a,202,body);
        check(submit.poll(List.of(accepted),a,1003)==ChatReplySubmission.Result.ACCEPTED,"精确提交身份确认回声");
        check(submit.poll(List.of(old),a,7000)==ChatReplySubmission.Result.TIMEOUT,"超时允许手动重试");
        check(submit.timedOut(),"超时状态保存");
        check(submit.poll(List.of(old),a,7001)==ChatReplySubmission.Result.WAITING,"超时提示只触发一次");
        UUID ticket=submit.ticket(),message=submit.message();submit.retry(8000);
        check(!submit.timedOut()&&submit.ticket().equals(ticket)&&submit.message().equals(message),"重试复用同一票据和消息 ID");
        check(submit.poll(List.of(old),a,8001)==ChatReplySubmission.Result.WAITING,"重试重新计时");
        check(submit.poll(List.of(accepted),a,14000)==ChatReplySubmission.Result.ACCEPTED,"超时边界优先确认已到回声");
        var next=new ChatReplySubmission(original.id(),raw,a,b,false,8000);
        check(!next.ticket().equals(ticket)&&!next.message().equals(message),"新提交即使正文相同也有独立身份");
        check(next.poll(List.of(accepted),a,8001)==ChatReplySubmission.Result.WAITING,"旧提交的迟到回声不能确认新提交");
        check(submit.matches(original.id(),raw),"未改写草稿可重试");
        check(!submit.matches(original.id(),new ChatComposer.Draft("回复 ",5)),"空格编辑是新草稿");
        check(!submit.matches(original.id(),new ChatComposer.Draft("回复",6)),"编辑后撤销仍是新草稿");
        var draft=new ChatReplyDraft();check(draft.id().isEmpty(),"初始无引用");draft.select(quote);
        check(draft.id().equals(Optional.of(original.id())),"引用保存消息身份");draft.clear();
        check(submit.complete(draft,text->text.equals(raw)),"取消预览后确认仍清除未经修改的已发正文");
        draft.select(quote);check(!submit.complete(draft,text->false)&&draft.quote()!=null,"改写正文不能清掉新草稿或引用");
        draft.select(ChatQuote.from(ChatMessage.text(b,"另一条",300),"Bob"));
        check(!submit.complete(draft,text->{throw new AssertionError("不应清理");}),"不触碰不同引用草稿");
        draft.select(quote);check(submit.complete(draft,text->true)&&draft.quote()==null,"清理对应引用");
        submit.rejected();check(submit.timedOut(),"服务端拒绝后立即允许操作，不再等超时");
        System.out.println("全部通过："+checks+" 条断言");
    }
}
