package com.november.mcphone.feature.chat.client.favorite;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

/** 真文件、真 PNG：迁移、独立存储、失败回滚、离线读取、配额和异步顺序。 */
public final class FavoriteImageStoreTest {
    private static int checks;
    private static void check(boolean ok,String why) { checks++;if(!ok) throw new AssertionError(why); }
    private static byte[] png(int width,int height) throws IOException {
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,0xffaa33ee);var bytes=new ByteArrayOutputStream();
        ImageIO.write(image,"PNG",bytes);image.flush();return bytes.toByteArray();
    }
    private static FavoriteEntry image(UUID id,boolean group,byte[] data,int frames) {
        return new FavoriteEntry(id,new UUID(0,2),group,new UUID(0,3),"发送者",100,200,
                FavoriteEntry.Kind.IMAGE,data.length,8,6,frames,frames>1?100:0);
    }
    private static FavoriteText text(UUID id,String content) {
        return new FavoriteText(id,new UUID(0,2),false,new UUID(0,3),"发送者","旧会话",content,100,200);
    }
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("mcphone-image-favorites-");
        try {
            Path legacy=root.resolve("environment.json");
            var original=text(new UUID(0,10),"中文\n第二行😀");
            String backup="{\"format\":1,\"entries\":"+new com.google.gson.Gson().toJson(List.of(original))+"}";
            Files.writeString(legacy,backup,StandardCharsets.UTF_8);
            var store=new FavoriteStore(legacy);
            check(store.available() && Files.exists(store.index()),"旧版文字迁移成功");
            check(Files.readString(legacy).equals(backup),"原始 JSON 原样保留为备份");
            FavoriteEntry migrated=store.snapshot().get(0);
            check(store.text(migrated).equals(original.text()),"UTF-8、换行和表情完整迁移");
            check(store.contentPath(migrated).getParent().endsWith("text"),"文字按类型存目录");
            String index=Files.readString(store.index());
            check(!index.contains("第二行") && !index.contains("sourceName"),"索引不重复保存正文及已移除的来源名字");
            for(String folder:List.of("text","image","audio","file"))
                check(Files.isDirectory(store.directory().resolve(folder)),"类型目录存在："+folder);

            byte[] staticPng=png(8,6),animation=png(16,12);
            var still=image(new UUID(0,11),false,staticPng,1);
            var animated=image(new UUID(0,12),true,animation,4);
            check(store.add(still,staticPng)==FavoriteStore.Result.OK,"静态图收藏成功");
            check(store.add(animated,animation)==FavoriteStore.Result.OK,"完整四帧动图收藏成功");
            check(store.contentPath(still).getParent().endsWith("image"),"图片按类型存目录");
            check(Arrays.equals(store.read(animated),animation),"收藏保留完整 PNG 帧表，不裁成第一帧");
            check(store.add(still,staticPng)==FavoriteStore.Result.EXISTS && store.snapshot().size()==3,"重复收藏不增加文件或记录");
            var group=image(still.message(),true,staticPng,1);
            check(store.add(group,staticPng)==FavoriteStore.Result.OK && !group.fileName().equals(still.fileName()),"私聊群聊同 ID 相互隔离");
            var reopened=new FavoriteStore(legacy);
            check(reopened.snapshot().size()==4 && Arrays.equals(reopened.read(animated),animation),"重启后无需服务器或聊天缓存即可读图");
            check(reopened.snapshot().get(0).equals(group),"混合列表保持收藏顺序");
            check(reopened.remove(migrated.key())==FavoriteStore.Result.OK && !Files.exists(reopened.contentPath(migrated)),"移除同时清理正文");
            check(new FavoriteStore(legacy).snapshot().stream().noneMatch(e->e.key().equals(migrated.key())),"原迁移备份不会让已删除收藏复活");

            String before=Files.readString(reopened.index());byte[] imageBefore=reopened.read(still);
            var failing=new FavoriteStore(legacy,(p,j)->{throw new IOException("模拟索引写失败");});
            var failed=image(new UUID(0,13),false,staticPng,1);
            check(failing.add(failed,staticPng)==FavoriteStore.Result.STORAGE_ERROR,"索引失败不报告成功");
            check(!Files.exists(failing.contentPath(failed)) && Files.readString(failing.index()).equals(before),"回收未提交图片且保留旧索引");
            check(failing.remove(still.key())==FavoriteStore.Result.STORAGE_ERROR
                    && Arrays.equals(failing.read(still),imageBefore),"移除失败保留原图片");
            var wrong=image(new UUID(0,14),false,animation,1);
            check(reopened.add(wrong,animation)==FavoriteStore.Result.STORAGE_ERROR,"拒绝尺寸与帧表不符的图片");
            byte[] invalid=new byte[]{1,2,3};
            check(reopened.add(image(new UUID(0,15),false,invalid,1),invalid)==FavoriteStore.Result.STORAGE_ERROR,"拒绝损坏 PNG");
            Files.delete(reopened.contentPath(animated));
            var missing=new FavoriteStore(legacy);
            check(missing.available() && missing.snapshot().size()==3,"单个图片文件缺失不破坏其他收藏");
            try { missing.read(animated);throw new AssertionError("缺失文件必须反馈错误"); } catch(IOException expected) { checks++; }
            check(missing.remove(animated.key())==FavoriteStore.Result.OK,"缺失图片仍能取消收藏");

            Path orphan=missing.directory().resolve("image").resolve("a".repeat(64)+".png");
            Files.write(orphan,staticPng);new FavoriteStore(legacy);
            check(!Files.exists(orphan),"重启回收未被索引引用的受管图片");
            Path userFile=missing.directory().resolve("image/notes.txt");Files.writeString(userFile,"保留用户文件");
            new FavoriteStore(legacy);check(Files.exists(userFile),"清理不删除其他命名文件");

            Path quotaFile=missing.directory().resolve("image/quota-test.bin");
            try(var sparse=new RandomAccessFile(quotaFile.toFile(),"rw")) { sparse.setLength(FavoriteStore.MAX_IMAGE_BYTES); }
            check(missing.add(image(new UUID(0,16),false,staticPng,1),staticPng)==FavoriteStore.Result.LIMIT,"图片目录达到总容量上限时拒绝新增");
            Files.delete(quotaFile);

            // 手动执行异步队列，验证串行顺序和排队上限，无 sleep/不依赖线程调度。
            var tasks=new ArrayDeque<Runnable>();java.util.concurrent.Executor executor=tasks::add;
            var queued=new FavoriteStore(root.resolve("queued.json"));
            var one=text(new UUID(0,21),"一");var two=text(new UUID(0,22),"二");
            var add=queued.submit(executor,()->queued.add(one));
            var remove=queued.submit(executor,()->queued.remove(one.key()));
            var last=queued.submit(executor,()->queued.add(two));
            check(queued.busy() && tasks.size()==1 && queued.snapshot().isEmpty(),"只提交首个工作，排队不阻塞读快照");
            while(!tasks.isEmpty()) tasks.remove().run();
            check(add.join()==FavoriteStore.Result.OK && remove.join()==FavoriteStore.Result.OK
                    && last.join()==FavoriteStore.Result.OK && !queued.busy(),"保存、移除、保存按顺序完成");
            check(queued.snapshot().size()==1 && queued.snapshot().get(0).key().equals(two.key()),"旧快照不能覆盖后续操作");
            for(int i=0;i<16;i++) queued.submit(executor,()->FavoriteStore.Result.OK);
            check(queued.submit(executor,()->FavoriteStore.Result.OK).join()==FavoriteStore.Result.BUSY,"有界队列拒绝过量操作");
            while(!tasks.isEmpty()) tasks.remove().run();
            check(!queued.busy(),"队列完成后释放容量");

            Path retryLegacy=root.resolve("retry.json");Files.writeString(retryLegacy,backup,StandardCharsets.UTF_8);
            var failedMigration=new FavoriteStore(retryLegacy,(p,j)->{throw new IOException("迁移提交失败");});
            check(!failedMigration.available() && !Files.exists(failedMigration.index())
                    && Files.readString(retryLegacy).equals(backup),"迁移中途失败保留原文件，不发布半份索引");
            var retry=new FavoriteStore(retryLegacy);
            check(retry.available() && retry.text(retry.snapshot().get(0)).equals(original.text()),"再次打开可安全重试迁移");

            var valid=com.google.gson.JsonParser.parseString(before).getAsJsonObject();
            for(String key:List.of("message","target","group","sender","senderName","messageTime","savedTime","kind","bytes","width","height","frames","frameMs")) {
                var invalidIndex=valid.deepCopy();invalidIndex.getAsJsonArray("entries").get(0).getAsJsonObject().remove(key);
                Path invalidPath=root.resolve("invalid-"+key+".json");var emptyStore=new FavoriteStore(invalidPath);
                Files.writeString(emptyStore.index(),invalidIndex.toString());
                check(!new FavoriteStore(invalidPath).available(),"新版索引缺少字段必须拒绝："+key);
            }

            // 后台写盘被阻塞时，渲染线程仍能读取快照并立即排队下一次操作。
            var executorPool=Executors.newFixedThreadPool(2);
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            try {
                var blocked=new FavoriteStore(root.resolve("blocked.json"),(p,j)->{
                    entered.countDown();try { if(!release.await(5,TimeUnit.SECONDS)) throw new IOException("测试闸门超时"); }
                    catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IOException(e); }
                    com.november.mcphone.core.client.AtomicWrite.replace(p,w->w.write(j));
                });
                var firstWrite=blocked.submit(executorPool,()->blocked.add(one));
                check(entered.await(5,TimeUnit.SECONDS),"进入后台写入");
                var secondWrite=CompletableFuture.supplyAsync(()->blocked.submit(executorPool,()->blocked.add(two)),executorPool)
                        .get(3,TimeUnit.SECONDS);
                check(blocked.snapshot().isEmpty() && !secondWrite.isDone(),"未提交数据不可见，排队无需等待写入锁");
                release.countDown();
                check(firstWrite.get(5,TimeUnit.SECONDS)==FavoriteStore.Result.OK
                        && secondWrite.get(5,TimeUnit.SECONDS)==FavoriteStore.Result.OK,"解除写入阻塞后依次完成");
            } finally { release.countDown();executorPool.shutdownNow(); }

            // 新索引损坏时不能重新导入旧备份，也不能清理正文。
            Files.writeString(missing.index(),"{");var broken=new FavoriteStore(legacy);
            check(!broken.available() && Files.exists(broken.contentPath(still)),"损坏新索引不回退旧备份或清理图片");
            check(Files.readString(legacy).equals(backup),"所有操作均不改写原迁移备份");
        } finally {
            try(var paths=Files.walk(root)) { for(Path path:paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
        System.out.println("FavoriteImageStoreTest: "+checks+" checks passed");
    }
}
