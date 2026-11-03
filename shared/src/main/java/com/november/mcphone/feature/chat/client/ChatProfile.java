package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.PlayerAvatar;
import com.november.mcphone.feature.chat.client.favorite.ChatFavorites;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 「我」页只协调四个入口和内部返回；资料同步与收藏存储独立维护。 */
public final class ChatProfile {
    private enum Page { HOME, DETAILS, FAVORITES, SETTINGS }
    public enum Action { CHATS, CONTACTS, BACK }
    private final ChatNavigationBar navigation=new ChatNavigationBar();
    private final ChatProfileDetails details=new ChatProfileDetails();
    private final ChatFavoritePane favorites=new ChatFavoritePane(ChatFavorites::current,
            text->Minecraft.getInstance().keyboardHandler.setClipboard(text));
    private Page page=Page.HOME,detailsReturn=Page.HOME,favoritesReturn=Page.HOME;
    private UUID player;
    private boolean external;
    private ChatLayout.Rect card,back;
    private List<ChatLayout.Rect> rows=List.of();
    private Action pending;
    public void open() {
        var current=Minecraft.getInstance().player;
        open(current==null?null:current.getUUID(),current==null?"":current.getName().getString(),false);
    }
    public void openPlayer(UUID id) {
        openPlayer(id,"");
    }
    public void openPlayer(UUID id,String knownName) {
        open(id,knownName.isBlank()?id.toString().substring(0,8):knownName,true);
    }
    private void open(UUID id,String name,boolean external) {
        close();player=id;this.external=external;page=external?Page.DETAILS:Page.HOME;detailsReturn=Page.HOME;
        var self=Minecraft.getInstance().player;
        if (id!=null) details.open(id,name,self!=null && self.getUUID().equals(id));
    }
    public void close() {
        details.close();favorites.close();navigation.clear();card=back=null;rows=List.of();pending=null;player=null;
        page=detailsReturn=favoritesReturn=Page.HOME;external=false;
    }
    public Action consumeAction() { var out=pending;pending=null;return out; }
    public boolean back() {
        if (page==Page.DETAILS && details.back() || page==Page.FAVORITES && favorites.back()) return true;
        if (external || page==Page.HOME) return false;
        change(page==Page.DETAILS?detailsReturn:page==Page.FAVORITES?favoritesReturn:Page.HOME);return true;
    }
    private void change(Page next) {
        if(page==Page.FAVORITES && next!=Page.FAVORITES) favorites.close();
        page=next;rows=List.of();card=back=null;navigation.clear();details.clearHits();
    }
    public void render(GuiGraphics g,int left,int top,int sw,int sh,int statusH,int navH,int mx,int my,Font font,float tick) {
        if (player==null) return;
        details.poll(System.currentTimeMillis());
        int x=left+4,w=sw-8,y=top+statusH+ChatLayout.TOP_GAP;
        int bottom=top+sh-navH-(page==Page.HOME?ChatLayout.TAB_HEIGHT:2);
        back=page==Page.HOME?null:new ChatLayout.Rect(x,y,12,ChatLayout.HEADER_HEIGHT);
        if (back!=null) ChatUi.sizedIcon(g,ChatUi.Icon.BACK,x+2.5f,y+2.5f,7,back.contains(mx,my)?ChatUi.accent():FontPalette.body());
        String title=ChatProfileUi.tr(switch(page) { case HOME->"title";case DETAILS->"details";case FAVORITES->"favorites";case SETTINGS->"settings"; });
        ChatUi.buttonText(g,font,title,x+w/2f,y+ChatLayout.HEADER_HEIGHT/2f,ChatLayout.TEXT_SCALE,FontPalette.title());
        y+=ChatLayout.HEADER_HEIGHT+4;
        var body=new ChatLayout.Rect(x,y,w,Math.max(0,bottom-y));
        switch(page) {
            case HOME -> home(g,font,body,mx,my);
            case DETAILS -> details.render(g,font,body,mx,my,tick);
            case FAVORITES -> favorites.render(g,font,body,mx,my);
            case SETTINGS -> settings(g,font,body,mx,my);
        }
        if (page==Page.HOME) navigation.render(g,font,x,bottom,w,ChatNavigationBar.Page.PROFILE);
    }
    private void home(GuiGraphics g,Font font,ChatLayout.Rect b,int mx,int my) {
        var layout=ChatProfileLayout.home(b);
        card=layout.card();
        ChatUi.glass(g,ChatGlass.Surface.CARD,card.x(),card.y(),card.width(),card.height(),card.contains(mx,my));
        var avatar=layout.avatar();
        PlayerAvatar.draw(g,player,avatar.x(),avatar.y(),avatar.width());
        ChatUi.label(g,font,ChatUi.truncate(font,details.name(),b.width()-48,ChatLayout.TEXT_SCALE),layout.textX(),layout.nameCenterY(),ChatLayout.TEXT_SCALE,FontPalette.title());
        String intro=!details.loaded()?ChatProfileUi.tr(details.status().isEmpty()?"loading":details.status()):details.introduction().isEmpty()?ChatProfileUi.tr("intro_empty"):details.introduction();
        ChatUi.label(g,font,ChatUi.truncate(font,ChatProfileUi.tr("intro")+"："+intro,b.width()-48,ChatLayout.META_SCALE),layout.textX(),layout.introCenterY(),ChatLayout.META_SCALE,FontPalette.subtle());
        ChatUi.forwardIcon(g,b.x()+b.width()-7,b.y()+19,5,FontPalette.subtle());
        var icons=ChatProfileUi.Icon.values();
        for (int i=0;i<icons.length;i++) {
            var row=layout.rows().get(i);
            ChatProfileUi.row(g,font,row,ChatProfileUi.tr(icons[i].label),icons[i],mx,my);
        }
        rows=layout.rows();
    }
    private void settings(GuiGraphics g,Font font,ChatLayout.Rect b,int mx,int my) {
        rows=List.of(new ChatLayout.Rect(b.x(),b.y(),b.width(),18),new ChatLayout.Rect(b.x(),b.y()+22,b.width(),18));
        ChatProfileUi.row(g,font,rows.get(0),ChatProfileUi.tr("edit_intro"),null,mx,my);
        ChatProfileUi.row(g,font,rows.get(1),ChatProfileUi.tr("manage_favorites"),null,mx,my);
        int y=b.y()+48;
        for (var line:font.split(Component.literal(ChatProfileUi.tr("settings_info")),ChatLayout.unscaledWidth(b.width()-6,ChatLayout.META_SCALE))) {
            ChatUi.text(g,font,line,b.x()+3,y,ChatLayout.META_SCALE,FontPalette.subtle());y+=ChatUi.lineHeight(font,ChatLayout.META_SCALE)+2;
        }
    }
    public boolean mouseClicked(double mx,double my,int button) {
        if (button!=0) return false;
        if (back!=null && back.contains(mx,my)) { if (!back()) pending=Action.BACK;return true; }
        if (page==Page.DETAILS) return details.click(mx,my,button);
        if (page==Page.FAVORITES) return favorites.click(mx,my,button);
        if (page==Page.HOME) {
            var nav=navigation.click(mx,my,button);
            if (nav==ChatNavigationBar.Page.CHATS) { pending=Action.CHATS;return true; }
            if (nav==ChatNavigationBar.Page.CONTACTS) { pending=Action.CONTACTS;return true; }
            if (card!=null && card.contains(mx,my)) { detailsReturn=Page.HOME;change(Page.DETAILS);return true; }
        }
        for (int i=0;i<rows.size();i++) if (rows.get(i).contains(mx,my)) {
            if (page==Page.SETTINGS) {
                if (i==0) { detailsReturn=Page.SETTINGS;change(Page.DETAILS); }
                else { favoritesReturn=Page.SETTINGS;favorites.close();change(Page.FAVORITES); }
            } else switch(ChatProfileUi.Icon.values()[i]) {
                case FAVORITE -> { favoritesReturn=Page.HOME;favorites.close();change(Page.FAVORITES); }
                case SETTINGS -> change(Page.SETTINGS);
                case GITHUB -> net.minecraft.Util.getPlatform().openUri("https://github.com/november521/mcphone");
                case SPONSOR -> { var current=Minecraft.getInstance().player;
                    if (current!=null) com.november.mcphone.platform.PlayerAccess.message(current,Component.translatable("mcphone.chat.profile.sponsor_pending"),true); }
            }
            return true;
        }
        return true;
    }
    public boolean keyPressed(int key,int scan,int modifiers) { return page==Page.DETAILS && details.key(key,scan,modifiers); }
    public boolean charTyped(char c,int modifiers) { return page==Page.DETAILS && details.character(c,modifiers); }
    public boolean mouseScrolled(double mx,double my,double amount) { return page==Page.FAVORITES && favorites.scroll(mx,my,amount); }
}
