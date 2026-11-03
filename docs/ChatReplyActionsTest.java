package com.november.mcphone.feature.chat.client.messageaction;

import java.util.*;

/** 引用消息的身份由菜单冻结，始终引用整条消息，与当前选中文字无关。 */
public final class ChatReplyActionsTest {
    public static void main(String[] args) {
        var peer=UUID.randomUUID();var message=UUID.randomUUID();var calls=new ArrayList<UUID>();
        var actions=new ChatMessageActions((g,x,y,w,h)->{},s->{throw new AssertionError("引用不复制");},
            (p,m,r)->{throw new AssertionError("引用不删除");},r->{},s->{},null,(p,m)->{calls.add(p);calls.add(m);});
        actions.execute(new ChatMessageTarget(peer,message,"完整正文","选中"),ChatMessageAction.REPLY,0);
        if(!calls.equals(List.of(peer,message)))throw new AssertionError("引用目标身份错误");
        var menu=new ChatContextMenu();menu.open(new ChatMessageTarget(peer,message,null,""),0,0,false,true);
        if(menu.entries(false).size()!=2)throw new AssertionError("图片消息也提供引用与删除");
        if(menu.entries(false).get(0).icon()!=com.november.mcphone.feature.chat.client.contextmenu.ChatContextMenuView.Icon.REPLY)
            throw new AssertionError("引用必须使用对应的圆环左引号图标");
        actions.reset();if(actions.isOpen())throw new AssertionError("关闭后不可残留菜单");
        System.out.println("全部通过：4 条断言");
    }
}
