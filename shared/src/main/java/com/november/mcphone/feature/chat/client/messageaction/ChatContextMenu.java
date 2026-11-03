package com.november.mcphone.feature.chat.client.messageaction;

import com.november.mcphone.feature.chat.client.contextmenu.ChatContextMenuView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.util.List;

/** 消息菜单目标与操作集合；呈现交给共用视图，不碰剪贴板、网络或输入框。 */
final class ChatContextMenu {
    private final ChatContextMenuView view = new ChatContextMenuView();
    private ChatMessageTarget target;
    private List<ChatMessageAction> items = List.of();
    boolean isOpen() { return view.isOpen(); }
    ChatMessageTarget target() { return target; }
    void open(ChatMessageTarget target,double x,double y) { open(target,x,y,false); }
    void open(ChatMessageTarget target,double x,double y,boolean favorite) {
        open(target,x,y,favorite,false);
    }
    void open(ChatMessageTarget target,double x,double y,boolean favorite,boolean reply) {
        this.target=target;
        var expanded=new java.util.ArrayList<ChatMessageAction>();
        if(target.copyable()) expanded.add(ChatMessageAction.COPY);
        if(favorite) expanded.add(ChatMessageAction.FAVORITE);
        if(reply) expanded.add(ChatMessageAction.REPLY);
        expanded.add(ChatMessageAction.DELETE);items=List.copyOf(expanded);
        view.open(x,y);
    }
    boolean dismiss() { boolean wasOpen=view.dismiss(); target=null; items=List.of(); return wasOpen; }
    ChatMessageAction actionAt(double x,double y) {
        int row=view.hitIndex(x,y); return row<0?null:items.get(row);
    }
    List<ChatContextMenuView.Entry> entries(boolean pending) {
        return items.stream().map(action -> new ChatContextMenuView.Entry(
                "mcphone.chat.message_action."+switch(action) {
                    case COPY -> target.selectedText().isEmpty()?"copy_message":"copy_selection";
                    case FAVORITE -> "favorite";
                    case REPLY -> "reply";
                    case DELETE -> pending?"deleting":"delete_message";
                },switch(action) { case REPLY -> ChatContextMenuView.Icon.REPLY; case COPY -> ChatContextMenuView.Icon.COPY; case FAVORITE -> ChatContextMenuView.Icon.FAVORITE; case DELETE -> ChatContextMenuView.Icon.DELETE; },
                !pending || action!=ChatMessageAction.DELETE)).toList();
    }
    void render(GuiGraphics g,Font font,int x,int y,int width,int height,int mx,int my,
                boolean pending,ChatContextMenuView.PanelPainter painter) {
        if (!isOpen()) return;
        view.render(g,font,x,y,width,height,mx,my,entries(pending),painter);
    }
}
