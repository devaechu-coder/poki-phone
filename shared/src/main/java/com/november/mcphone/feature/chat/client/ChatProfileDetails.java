package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.PlayerAvatar;
import com.november.mcphone.core.net.MCphoneNetwork;
import com.november.mcphone.feature.chat.client.profile.ProfileClientCache;
import com.november.mcphone.feature.chat.profile.ProfileBook;
import com.november.mcphone.feature.chat.profile.net.ProfileWire;
import com.november.mcphone.feature.chat.profile.net.ProfileRequestPacket;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 公开资料读取与自己的介绍草稿；仅匹配本页票据，成功回声才更新显示值。 */
final class ChatProfileDetails {
    private final Consumer<ProfileWire.Query> send;
    private final ChatContactSearch editor=new ChatContactSearch("mcphone.chat.profile.intro_hint",ProfileBook.MAX_INTRO);
    private UUID target,request;
    private boolean own,editing,writing,loaded;
    private long deadline;
    private String name="",introduction="",status="";
    private ChatLayout.Rect action;
    ChatProfileDetails() { this(q->MCphoneNetwork.sendToServer(new ProfileRequestPacket(q))); }
    ChatProfileDetails(Consumer<ProfileWire.Query> send) { this.send=send; }
    void open(UUID id,String fallback,boolean own) {
        close();target=id;name=fallback;this.own=own;introduction="";loaded=false;status="";editing=false;
        submit(false,"");
    }
    void close() { if (request!=null) ProfileClientCache.cancel(request);request=null;target=null;action=null;editor.reset(); }
    void clearHits() { action=null;editor.close(); }
    void submit(boolean write,String text) {
        if (target==null || request!=null) return;
        writing=write;long now=System.currentTimeMillis();deadline=now+ProfileClientCache.REQUEST_TIMEOUT_MS;
        status="loading";
        try {
            request=ProfileClientCache.begin(target,now);
            send.accept(new ProfileWire.Query(request,target,write,text));
        } catch (RuntimeException e) {
            if (request!=null) ProfileClientCache.cancel(request);
            request=null;status="unavailable";
        }
    }
    void poll(long now) {
        if (request==null) return;
        var reply=ProfileClientCache.consume(request);
        if (reply!=null) {
            request=null;
            if (reply.status()==ProfileWire.Status.OK) {
                name=reply.name();introduction=reply.introduction();loaded=true;status=writing?"saved":"";
                if (writing) { editing=false;editor.close(); }
            } else status=reply.status().name().toLowerCase(java.util.Locale.ROOT);
        } else if (now>=deadline) { ProfileClientCache.cancel(request);request=null;status="timeout"; }
    }
    boolean loaded() { return loaded; }
    String introduction() { return introduction; }
    String name() { return name; }
    String status() { return status; }
    void render(GuiGraphics g,Font font,ChatLayout.Rect area,int mx,int my,float tick) {
        int x=area.x(),y=area.y(),w=area.width();
        ChatUi.glass(g,ChatGlass.Surface.CARD,x,y,w,30,false);
        PlayerAvatar.draw(g,target,x+4,y+6,18);
        ChatUi.label(g,font,ChatUi.truncate(font,name,w-32,ChatLayout.TEXT_SCALE),x+27,y+15,ChatLayout.TEXT_SCALE,FontPalette.title());
        y+=34;
        ChatUi.label(g,font,ChatProfileUi.tr("intro"),x+3,y+4,ChatLayout.TEXT_SCALE,FontPalette.body());y+=12;
        int bottom=area.y()+area.height();
        action=new ChatLayout.Rect(x,Math.max(y+18,bottom-16),w,14);
        if (editing) editor.render(g,font,new ChatLayout.Rect(x,y,w,14),mx,my,tick);
        else {
            String text=loaded?(introduction.isEmpty()?ChatProfileUi.tr("intro_empty"):introduction):ChatProfileUi.tr(status.isEmpty()?"loading":status);
            com.november.mcphone.core.client.GuiUtil.enableScissor(g,x,y,x+w,Math.max(y,action.y()-16));
            try {
                for (var line:font.split(Component.literal(text),ChatLayout.unscaledWidth(w-6,ChatLayout.TEXT_SCALE))) {
                    ChatUi.text(g,font,line,x+3,y,ChatLayout.TEXT_SCALE,FontPalette.subtle());y+=ChatUi.lineHeight(font,ChatLayout.TEXT_SCALE)+2;
                }
            } finally { com.november.mcphone.core.client.GuiUtil.disableScissor(g); }
        }
        if (!status.isEmpty() && loaded) ChatUi.label(g,font,ChatUi.truncate(font,ChatProfileUi.tr(status),w-6,ChatLayout.META_SCALE),x+3,action.y()-8,ChatLayout.META_SCALE,FontPalette.subtle());
        if (own || !loaded) ChatProfileUi.button(g,font,action,ChatProfileUi.tr(request!=null?"loading":!loaded?"retry":editing?"save":"edit_intro"),mx,my,request==null?ChatUi.accent():FontPalette.dim());
        else action=null;
    }
    boolean click(double mx,double my,int button) {
        if (request!=null) return true;
        if (editing && editor.mouseClicked(mx,my,button)) return true;
        if (button==0 && action!=null && action.contains(mx,my)) {
            action=null;
            if (!loaded) submit(false,"");
            else if (editing) submit(true,editor.query());
            else if (own) { editing=true;editor.value(introduction);status=""; }
        }
        return true;
    }
    boolean back() {
        if (request!=null && writing) return true;
        if (!editing) return false;
        if (request!=null) ProfileClientCache.cancel(request);
        request=null;editing=false;editor.reset();status="";action=null;return true;
    }
    boolean key(int key,int scan,int modifiers) { return request!=null || editing && editor.keyPressed(key,scan,modifiers); }
    boolean character(char c,int modifiers) { return request!=null || editing && editor.charTyped(c,modifiers); }
}
