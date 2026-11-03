package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.GuiUtil;
import com.november.mcphone.core.client.ImageCodec;
import com.november.mcphone.feature.chat.ChatImage;
import com.november.mcphone.feature.chat.client.favorite.*;
import com.november.mcphone.feature.gallery.client.PhotoLibrary;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 混合收藏列表；只读取本机独立文件，不打开会话或向服务端取图片。 */
final class ChatFavoritePane {
    private record Hit(ChatLayout.Rect bounds,FavoriteEntry entry) {}
    private final Supplier<FavoriteStore> source;
    private final Consumer<String> copy;
    private final Executor io,ui;
    private final FavoriteMedia media;
    private List<Hit> hits=List.of();
    private FavoriteEntry selected;
    private FavoriteStore store;
    private String selectedText;
    private ChatLayout.Rect area,copyButton,removeButton;
    private int offset,maximum,textOffset,textMaximum,generation;
    private boolean removing,saving;
    private String status="";
    ChatFavoritePane(Supplier<FavoriteStore> source,Consumer<String> copy) {
        this(source,copy,net.minecraft.Util.backgroundExecutor(),task->Minecraft.getInstance().execute(task));
    }
    ChatFavoritePane(Supplier<FavoriteStore> source,Consumer<String> copy,Executor io,Executor ui) {
        this.source=source;this.copy=copy;this.io=io;this.ui=ui;this.media=new FavoriteMedia(io,ui);
    }
    void close() {
        media.clear();store=null;generation++;removing=saving=false;
        hits=List.of();selected=null;selectedText=null;area=copyButton=removeButton=null;
        offset=maximum=textOffset=textMaximum=0;status="";
    }
    boolean back() {
        if(selected==null) return false;
        selected=null;selectedText=null;copyButton=removeButton=null;hits=List.of();status="";return true;
    }
    void render(GuiGraphics g,Font font,ChatLayout.Rect bounds,int mx,int my) {
        area=bounds;hits=List.of();copyButton=removeButton=null;
        FavoriteStore current;
        try { current=source.get(); } catch(RuntimeException e) { empty(g,font,"storage_error");return; }
        if(current==null) { empty(g,font,"loading");return; }
        if(!current.available()) { empty(g,font,"storage_error");return; }
        if(store!=current) {
            media.clear();selected=null;selectedText=null;store=current;
            offset=textOffset=0;generation++;removing=saving=false;
        }
        if(selected!=null) { renderContent(g,font,mx,my);return; }
        var values=store.snapshot();
        int total=0;for(var value:values) total+=rowHeight(value);
        maximum=Math.max(0,total-bounds.height());offset=Math.max(0,Math.min(offset,maximum));
        if(values.isEmpty()) { empty(g,font,"empty");return; }
        var next=new ArrayList<Hit>();
        GuiUtil.enableScissor(g,bounds.x(),bounds.y(),bounds.x()+bounds.width(),bounds.y()+bounds.height());
        try {
            int y=bounds.y()-offset;
            for(var item:values) {
                int step=rowHeight(item);int rowY=y;y+=step;
                if(y<=bounds.y()) continue;if(rowY>=bounds.y()+bounds.height()) break;
                var row=new ChatLayout.Rect(bounds.x(),rowY,bounds.width(),step-2);next.add(new Hit(row,item));
                ChatUi.glass(g,ChatGlass.Surface.CARD,row.x(),row.y(),row.width(),row.height(),row.contains(mx,my));
                ChatUi.label(g,font,ChatUi.truncate(font,item.senderName(),row.width()-10,ChatLayout.META_SCALE),
                        row.x()+4,rowY+7,ChatLayout.META_SCALE,FontPalette.subtle());
                var loaded=media.get(store,item);
                if(item.kind()==FavoriteEntry.Kind.TEXT) {
                    String text=loaded!=null && loaded.text!=null?loaded.text.replace('\n',' '):hint(loaded);
                    ChatUi.label(g,font,ChatUi.truncate(font,text,row.width()-10,ChatLayout.TEXT_SCALE),
                            row.x()+4,rowY+17,ChatLayout.TEXT_SCALE,FontPalette.body());
                } else if(loaded!=null && loaded.texture!=null)
                    ChatImageViewer.drawImage(g,loaded.texture,item.frames(),0,row.x()+4,rowY+14,row.width()-8,step-20);
                else ChatUi.label(g,font,ChatUi.truncate(font,hint(loaded),row.width()-10,ChatLayout.META_SCALE),
                            row.x()+4,rowY+28,ChatLayout.META_SCALE,FontPalette.subtle());
            }
        } finally { GuiUtil.disableScissor(g); }
        hits=List.copyOf(next);
    }
    private static int rowHeight(FavoriteEntry entry) { return entry.kind()==FavoriteEntry.Kind.TEXT?27:76; }
    private static String hint(FavoriteMedia.Loaded loaded) {
        return Component.translatable("mcphone.chat.favorite."+(loaded!=null && loaded.failed?"content_missing":"loading")).getString();
    }
    private void empty(GuiGraphics g,Font font,String key) {
        ChatUi.label(g,font,ChatUi.truncate(font,Component.translatable("mcphone.chat.favorite."+key).getString(),area.width()-6,
                ChatLayout.TEXT_SCALE),area.x()+3,area.y()+8,ChatLayout.TEXT_SCALE,FontPalette.subtle());
    }
    private void renderContent(GuiGraphics g,Font font,int mx,int my) {
        int x=area.x(),y=area.y(),w=area.width();
        ChatUi.label(g,font,ChatUi.truncate(font,selected.senderName(),w-6,ChatLayout.META_SCALE),
                x+3,y+5,ChatLayout.META_SCALE,FontPalette.subtle());
        var loaded=media.get(store,selected);
        int top=y+15,bottom=y+area.height()-28;
        selectedText=loaded==null?null:loaded.text;
        GuiUtil.enableScissor(g,x,top,x+w,Math.max(top,bottom));
        try {
            if(selected.kind()==FavoriteEntry.Kind.TEXT && selectedText!=null) {
                var lines=font.split(Component.literal(selectedText),ChatLayout.unscaledWidth(w-8,ChatLayout.TEXT_SCALE));
                int step=ChatUi.lineHeight(font,ChatLayout.TEXT_SCALE)+2;
                textMaximum=Math.max(0,lines.size()*step-Math.max(0,bottom-top));textOffset=Math.max(0,Math.min(textOffset,textMaximum));
                int lineY=top-textOffset;
                for(var line:lines) { ChatUi.text(g,font,line,x+4,lineY,ChatLayout.TEXT_SCALE,FontPalette.body());lineY+=step; }
            } else if(selected.kind()==FavoriteEntry.Kind.IMAGE && loaded!=null && loaded.texture!=null)
                ChatImageViewer.drawImage(g,loaded.texture,selected.frames(),selected.frameMs(),x+2,top,w-4,Math.max(1,bottom-top));
            else ChatUi.label(g,font,ChatUi.truncate(font,hint(loaded),w-6,ChatLayout.META_SCALE),
                        x+3,top+8,ChatLayout.META_SCALE,FontPalette.subtle());
        } finally { GuiUtil.disableScissor(g); }
        int buttonY=y+area.height()-14,half=(w-3)/2;
        copyButton=new ChatLayout.Rect(x,buttonY,half,14);removeButton=new ChatLayout.Rect(x+half+3,buttonY,w-half-3,14);
        String action=selected.kind()==FavoriteEntry.Kind.TEXT?"mcphone.chat.message_action.copy_message":"mcphone.chat.image_save";
        ChatProfileUi.button(g,font,copyButton,Component.translatable(action).getString(),mx,my,FontPalette.body());
        ChatProfileUi.button(g,font,removeButton,ChatProfileUi.tr("remove_favorite"),mx,my,FontPalette.danger());
        if(!status.isEmpty()) ChatUi.label(g,font,ChatUi.truncate(font,Component.translatable(status).getString(),w-6,
                ChatLayout.META_SCALE),x+3,buttonY-7,ChatLayout.META_SCALE,FontPalette.subtle());
    }
    boolean click(double mx,double my,int button) {
        if(button!=0 || area==null || !area.contains(mx,my)) return false;
        if(selected==null) {
            for(var hit:hits) if(hit.bounds().contains(mx,my)) { selected=hit.entry();selectedText=null;textOffset=textMaximum=0;hits=List.of();return true; }
        } else if(copyButton!=null && copyButton.contains(mx,my)) {
            if(selected.kind()==FavoriteEntry.Kind.TEXT) {
                // 正文自身显示读取/损坏状态，不把瞬时加载提示保留为操作结果。
                if(selectedText!=null) copy.accept(selectedText);
            } else saveImage();
        } else if(removeButton!=null && removeButton.contains(mx,my) && !removing) {
            FavoriteStore captured=store;
            if(captured==null) { try { captured=source.get(); } catch(RuntimeException ignored) {} }
            if(captured==null) { status="mcphone.chat.favorite.loading";return true; }
            FavoriteStore owner=captured;FavoriteEntry entry=selected;int gen=generation;
            removing=true;status="mcphone.chat.favorite.removing";
            owner.submit(io,()->owner.remove(entry.key())).whenComplete((result,error)->ui.execute(()->{
                if(gen!=generation) return;
                removing=false;
                if(error==null && result==FavoriteStore.Result.OK) {
                    media.remove(entry.key());
                    if(selected!=null && selected.key().equals(entry.key())) back();
                } else if(selected!=null && selected.key().equals(entry.key())) status="mcphone.chat.favorite.storage_error";
            }));
        }
        return true;
    }
    private void saveImage() {
        if(saving || store==null) return;
        var loaded=media.get(store,selected);
        if(loaded==null || loaded.texture==null) return;
        FavoriteStore owner=store;FavoriteEntry entry=selected;int gen=generation;
        saving=true;status="mcphone.chat.favorite.saving";
        java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try {
                byte[] png=owner.read(entry);
                if(entry.frames()>1) png=ImageCodec.cropCell(png,ChatImage.cols(entry.frames()),ChatImage.rows(entry.frames()));
                return png==null?null:PhotoLibrary.save(png,"mcphone-favorite-");
            } catch(Exception e) { return null; }
        },io).whenComplete((name,error)->ui.execute(()->{
            if(gen!=generation) return;saving=false;
            if(selected!=null && selected.key().equals(entry.key()))
                status="mcphone.chat.favorite."+(error==null && name!=null?(entry.frames()>1?"exported_frame":"exported"):"storage_error");
        }));
    }
    boolean scroll(double mx,double my,double amount) {
        if(area==null || !area.contains(mx,my) || amount==0) return false;
        int delta=amount>0?-12:12;
        if(selected==null) offset=Math.max(0,Math.min(maximum,offset+delta));
        else if(selected.kind()==FavoriteEntry.Kind.TEXT) textOffset=Math.max(0,Math.min(textMaximum,textOffset+delta));
        hits=List.of();return true;
    }
}
