package com.november.mcphone.feature.chat.client;
import com.november.mcphone.feature.chat.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;

/** 引用留白、正文选区和输入草稿在真实布局中相互独立。 */
public final class ChatReplyLayoutTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static final class TestFont extends com.november.mcphone.test.TestFontBase {
        TestFont() { super(); }
        @Override public int width(String text) { return (int)Math.ceil(getSplitter().stringWidth(text)); }
        @Override public String plainSubstrByWidth(String text,int width) { return getSplitter().plainHeadByWidth(text,width,Style.EMPTY); }
        @Override public String plainSubstrByWidth(String text,int width,boolean tail) {
            return tail ? getSplitter().plainTailByWidth(text,width,Style.EMPTY) : plainSubstrByWidth(text,width);
        }
        @Override public int width(FormattedCharSequence text) {
            int[] width = {0};
            text.accept((index, style, codePoint) -> { width[0] += 6; return true; });
            return width[0];
        }
        @Override public StringSplitter getSplitter() { return new StringSplitter((cp, style) -> 6); }
        @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
            return getSplitter().splitLines(text, width, Style.EMPTY).stream()
                    .map(line -> FormattedCharSequence.forward(line.getString(), Style.EMPTY)).toList();
        }

    }

    public static void main(String[] args) throws Exception {
        var font=new TestFont();UUID self=new UUID(0,1),other=new UUID(0,2);
        var original=ChatMessage.text(other,"原始内容",100);
        var quote=ChatQuote.from(original,"Bob");
        var reply=new ChatMessage(self,200,new TextBody("回复正文\n第二行",Optional.of(quote)));
        for(int width:new int[]{64,110,170}) for(boolean group:new boolean[]{false,true}) {
            var block=ChatMessageLayout.build(font,List.of(reply),width,self,group).stream().filter(b->b.message()!=null).findFirst().orElseThrow();
            check(block.quoteHeight()==ChatReplyView.HEIGHT,"气泡预留引用高度");
            check(block.textOrigin().y()>=block.quoteHeight(),"正文原点不进入摘要");
            check(block.textOrigin().x()==3,"带引用的正文靠左对齐");
            check(block.placement().contentY()+block.h()<=block.rowHeight(),"引用气泡不越过消息行");
            check(block.w()<=width,"窄屏引用不撑开视口");
        }
        var composer=new ChatComposer(s->{},()->"",(g,x,y,w,h)->{});
        var box=com.november.mcphone.test.TestEditBoxFactory.create(font,0,0,100,9,Component.empty());
        var bind=ChatComposer.class.getDeclaredMethod("bindInput",net.minecraft.client.gui.components.EditBox.class);bind.setAccessible(true);bind.invoke(composer,box);
        box.setValue("草稿");composer.sendGuard(()->false);composer.keyPressed(257,0,0);
        check(box.getValue().equals("草稿"),"不可用引用拦截发送时保留草稿");
        composer.sendGuard(()->true);composer.clearAfterSend(()->false);composer.keyPressed(257,0,0);
        check(box.getValue().equals("草稿"),"等待回声不提前清空");
        var sent=composer.draft();box.setValue("草稿 ");
        check(!composer.clearSubmitted(sent)&&box.getValue().equals("草稿 "),"仅增空格也保护新草稿");
        box.setValue("草稿");check(!composer.clearSubmitted(sent),"编辑后改回原文不清掉草稿");
        check(composer.clearSubmitted(composer.draft())&&box.getValue().isEmpty(),"未发生编辑的精确快照才可清空");
        System.out.println("全部通过："+checks+" 条断言");
    }
}
