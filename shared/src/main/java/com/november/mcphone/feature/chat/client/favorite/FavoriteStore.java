package com.november.mcphone.feature.chat.client.favorite;

import com.november.mcphone.core.client.AtomicWrite;
import com.november.mcphone.feature.chat.ChatImage;
import com.november.mcphone.feature.chat.TextBody;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.imageio.ImageIO;

/** 统一索引、独立正文。变更在后台串行提交，渲染只读取已提交的不可变快照。 */
public final class FavoriteStore {
    public static final int MAX_ENTRIES=512, MAX_BYTES=2*1024*1024;
    public static final long MAX_IMAGE_BYTES=128L*1024*1024;
    public enum Result { OK, EXISTS, LIMIT, STORAGE_ERROR, BUSY }
    @FunctionalInterface public interface Writer { void write(Path path,String json) throws IOException; }
    private final Path legacy, directory, index;
    private final Writer writer;
    private volatile Map<String,FavoriteEntry> values=Map.of();
    private volatile boolean available=true;
    private CompletableFuture<?> tail=CompletableFuture.completedFuture(null);
    private final Object queueLock=new Object();
    private volatile int queued;
    public FavoriteStore(Path legacy) { this(legacy,(file,json)->AtomicWrite.replace(file,w->w.write(json))); }
    public FavoriteStore(Path legacy,Writer writer) {
        this.legacy=legacy;this.writer=writer;
        String name=legacy.getFileName().toString();
        directory=legacy.resolveSibling(name.endsWith(".json")?name.substring(0,name.length()-5):name+"-contents");
        index=directory.resolve("index.json"); load();
    }
    public boolean available() { return available; }
    public Path index() { return index; }
    public Path directory() { return directory; }
    public Path contentPath(FavoriteEntry entry) { return directory.resolve(entry.kind().folder).resolve(entry.fileName()); }
    public List<FavoriteEntry> snapshot() {
        var all=new ArrayList<>(values.values());Collections.reverse(all);return List.copyOf(all);
    }
    public byte[] read(FavoriteEntry entry) throws IOException {
        byte[] data=readBounded(contentPath(entry),entry.kind()==FavoriteEntry.Kind.TEXT?TextBody.MAX_LENGTH*4:ChatImage.MAX_BYTES_CEILING);
        if (data.length!=entry.bytes()) throw new IOException("收藏正文大小与索引不一致");
        return data;
    }
    public String text(FavoriteEntry entry) throws IOException {
        if (entry.kind()!=FavoriteEntry.Kind.TEXT) throw new IOException("不是文字收藏");
        String text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(read(entry))).toString();
        if (text.isBlank() || text.length()>TextBody.MAX_LENGTH) throw new IOException("无效收藏文字");
        return text;
    }
    /** 有界串行队列；后台任务绑定本 store，绝不重新查询当前账号或服务器。 */
    public boolean busy() { return queued>0; }
    public CompletableFuture<Result> submit(Executor executor,Supplier<Result> action) {
        synchronized(queueLock) {
            if (queued>=16) return CompletableFuture.completedFuture(Result.BUSY);
            queued++;
            CompletableFuture<Result> result=tail.handle((v,e)->null).thenApplyAsync(ignored->action.get(),executor);
            tail=result.handle((v,e)->{ synchronized(queueLock) { queued--; } return null; });
            return result;
        }
    }
    /** 文字入口保留完整快照参数，落盘采用新版独立文件。 */
    public Result add(FavoriteText text) { return add(FavoriteEntry.text(text),text.text().getBytes(StandardCharsets.UTF_8)); }
    public synchronized Result add(FavoriteEntry entry,byte[] data) {
        if (!available) return Result.STORAGE_ERROR;
        if (values.containsKey(entry.key())) return Result.EXISTS;
        if (values.size()>=MAX_ENTRIES) return Result.LIMIT;
        Path payload=contentPath(entry);
        try {
            validate(entry,data);
            if (entry.kind()==FavoriteEntry.Kind.IMAGE && imageBytes()+data.length>MAX_IMAGE_BYTES) return Result.LIMIT;
            var next=new LinkedHashMap<>(values);next.put(entry.key(),entry);
            String json=encode(next); if (json==null) return Result.LIMIT;
            writePayload(payload,data);
            try { writer.write(index,json); }
            catch (IOException|RuntimeException failure) { deletePayload(payload);throw failure; }
            values=Collections.unmodifiableMap(next);return Result.OK;
        } catch (IOException|RuntimeException e) { warn("保存",e);return Result.STORAGE_ERROR; }
    }
    public synchronized Result remove(String key) {
        if (!available) return Result.STORAGE_ERROR;
        FavoriteEntry old=values.get(key);if (old==null) return Result.OK;
        var next=new LinkedHashMap<>(values);next.remove(key);
        try {
            writer.write(index,FavoriteCodec.encode(next));
            values=Collections.unmodifiableMap(next);
            deletePayload(contentPath(old));return Result.OK;
        } catch (IOException|RuntimeException e) { warn("移除",e);return Result.STORAGE_ERROR; }
    }
    private String encode(Map<String,FavoriteEntry> next) {
        String json=FavoriteCodec.encode(next);
        return json.getBytes(StandardCharsets.UTF_8).length<=MAX_BYTES?json:null;
    }
    private void load() {
        try {
            if (Files.exists(index)) values=Collections.unmodifiableMap(FavoriteCodec.decode(utf8(index)));
            else if (Files.exists(legacy)) {
                // 原 JSON 始终保留为迁移备份；新索引坏了也不回退旧快照。
                var texts=FavoriteCodec.legacy(utf8(legacy));var next=new LinkedHashMap<String,FavoriteEntry>();
                for (FavoriteText text:texts.values()) {
                    var entry=FavoriteEntry.text(text);writePayload(contentPath(entry),text.text().getBytes(StandardCharsets.UTF_8));
                    next.put(entry.key(),entry);
                }
                writer.write(index,FavoriteCodec.encode(next));values=Collections.unmodifiableMap(next);
            }
            for(String folder:List.of("text","image","audio","file")) Files.createDirectories(directory.resolve(folder));
            cleanup();
        } catch (IOException|RuntimeException e) { available=false;warn("读取/迁移",e); }
    }
    private String utf8(Path file) throws IOException {
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(readBounded(file,MAX_BYTES))).toString();
    }
    private long imageBytes() throws IOException {
        Path folder=directory.resolve("image");if (!Files.isDirectory(folder)) return 0;
        long bytes=0;
        try(var files=Files.newDirectoryStream(folder)) {
            for(Path file:files) if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) bytes=Math.addExact(bytes,Files.size(file));
        }
        return bytes;
    }
    private void cleanup() throws IOException {
        var referenced=new HashSet<Path>();for(var entry:values.values()) referenced.add(contentPath(entry));
        for(var kind:FavoriteEntry.Kind.values()) {
            Path folder=directory.resolve(kind.folder);if(!Files.isDirectory(folder)) continue;
            try(var files=Files.newDirectoryStream(folder)) {
                for(Path file:files) if(file.getFileName().toString().matches("[0-9a-f]{64}\\.(txt|png)(\\.tmp)?")
                        && !referenced.contains(file)) deletePayload(file);
            }
        }
    }
    private void deletePayload(Path file) {
        try { Files.deleteIfExists(file); } catch(IOException|RuntimeException e) { warn("清理文件",e); }
    }
    private static void writePayload(Path file,byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());Path tmp=file.resolveSibling(file.getFileName()+".tmp");
        Files.write(tmp,bytes);
        try { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
        catch(AtomicMoveNotSupportedException e) { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); }
    }
    private static byte[] readBounded(Path file,int maximum) throws IOException {
        try(var input=Files.newInputStream(file)) {
            byte[] data=input.readNBytes(maximum+1);if(data.length>maximum) throw new IOException("收藏文件超限");return data;
        }
    }
    private static void validate(FavoriteEntry entry,byte[] data) throws IOException {
        if(data==null || data.length!=entry.bytes()) throw new IOException("收藏正文大小错误");
        if(entry.kind()==FavoriteEntry.Kind.TEXT) {
            String text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data)).toString();
            if(text.isBlank() || text.length()>TextBody.MAX_LENGTH) throw new IOException("无效文字");return;
        }
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext()) throw new IOException("无效图片");
            var reader=readers.next();
            try {
                reader.setInput(input,true,true);
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(!reader.getFormatName().equalsIgnoreCase("PNG") || w>ChatImage.SHEET_MAX_SIDE || h>ChatImage.SHEET_MAX_SIDE
                        || w!=entry.width()*ChatImage.cols(entry.frames()) || h!=entry.height()*ChatImage.rows(entry.frames()))
                    throw new IOException("图片帧表与元信息不一致");
                var decoded=reader.read(0);if(decoded==null) throw new IOException("图片损坏");decoded.flush();
            } finally { reader.dispose(); }
        }
    }
    private static void warn(String action,Exception e) {
        com.november.mcphone.MCphone.LOGGER.warn("[MCphone] 收藏{}失败，保留已提交记录",action,e);
    }
}
