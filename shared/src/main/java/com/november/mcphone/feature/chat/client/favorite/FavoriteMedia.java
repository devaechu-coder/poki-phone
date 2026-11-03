package com.november.mcphone.feature.chat.client.favorite;

import com.november.mcphone.core.client.ImageCodec;
import com.november.mcphone.feature.chat.ChatImage;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/** 收藏专用本机内容缓存，不向聊天服务器请求像素。退出/移除后旧解码不能恢复贴图。 */
public final class FavoriteMedia {
    private static final long MAX_BYTES=24L*1024*1024;
    private final Executor io,ui;
    private final Map<String,Loaded> entries=new LinkedHashMap<>(16,0.75f,true);
    private FavoriteStore store;
    private int generation,loading;
    private long bytes;
    public static final class Loaded {
        public String text;
        public ImageCodec.Texture texture;
        public boolean failed;
    }
    /** 调用方提供后台及渲染线程；缓存不定位玩家、连接或全局调度器。 */
    public FavoriteMedia(Executor io,Executor ui) { this.io=io;this.ui=ui; }
    public Loaded get(FavoriteStore source,FavoriteEntry entry) {
        if(store!=source) { clear();store=source; }
        Loaded loaded=entries.get(entry.key());if(loaded!=null) return loaded;
        if(loading>=4) return null;
        loaded=new Loaded();entries.put(entry.key(),loaded);loading++;
        int gen=generation;Loaded owner=loaded;
        io.execute(()->{
            String text=null;NativeImage decoded=null;
            try {
                if(entry.kind()==FavoriteEntry.Kind.TEXT) text=source.text(entry);
                else decoded=ImageCodec.decodeAndScale(source.read(entry),ChatImage.SHEET_MAX_SIDE);
            } catch(Exception e) { com.november.mcphone.MCphone.LOGGER.warn("收藏内容加载失败 [{}]",entry.key(),e); }
            String result=text;NativeImage image=decoded;
            ui.execute(()->{
                if(gen==generation) loading--;
                if(gen!=generation || store!=source || entries.get(entry.key())!=owner) {
                    if(image!=null) image.close();return;
                }
                owner.text=result;
                if(image!=null) {
                    owner.texture=ImageCodec.upload(image,"favorite_image_");
                    bytes+=(long)owner.texture.width()*owner.texture.height()*4;
                }
                owner.failed=result==null && owner.texture==null;
                trim();
            });
        });
        trim();return loaded;
    }
    private void trim() {
        var iterator=entries.entrySet().iterator();
        while((entries.size()>64 || bytes>MAX_BYTES && entries.size()>4) && iterator.hasNext()) {
            release(iterator.next().getValue());iterator.remove();
        }
    }
    private void release(Loaded value) {
        if(value.texture!=null) bytes-=(long)value.texture.width()*value.texture.height()*4;
        ImageCodec.release(value.texture);
    }
    public void remove(String key) { Loaded old=entries.remove(key);if(old!=null) release(old); }
    public void clear() {
        for(Loaded value:entries.values()) ImageCodec.release(value.texture);
        entries.clear();store=null;bytes=0;loading=0;generation++;
    }
}
