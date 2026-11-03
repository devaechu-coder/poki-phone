package com.november.mcphone.feature.chat.client.favorite;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import javax.imageio.ImageIO;

/** 用真实文件及可控任务队列检查生产缓存；不创建游戏窗口或 GL 贴图。 */
public final class FavoriteMediaTest {
    private static int checks;
    private static void check(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }
    private static FavoriteEntry add(FavoriteStore store, int id) {
        var text = new FavoriteText(new UUID(0, id), new UUID(0, 2), false,
                new UUID(0, 3), "发送者", "旧会话", "正文 " + id, 100, 200);
        if (store.add(text) != FavoriteStore.Result.OK) throw new AssertionError("测试文件准备失败");
        return FavoriteEntry.text(text);
    }
    private static void drain(ArrayDeque<Runnable> tasks) {
        while (!tasks.isEmpty()) tasks.remove().run();
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("mcphone-favorite-media-");
        var io = new ArrayDeque<Runnable>();
        var ui = new ArrayDeque<Runnable>();
        var media = new FavoriteMedia(io::add, ui::add);
        try {
            var first = new FavoriteStore(root.resolve("first.json"));
            var a = add(first, 10);
            var loaded = media.get(first, a);
            check(loaded.text == null && io.size() == 1, "首次读取在后台排队");
            check(media.get(first, a) == loaded && io.size() == 1, "同条加载中不重复读盘");
            drain(io);
            check(loaded.text == null && ui.size() == 1, "后台结果不直接修改界面状态");
            drain(ui);
            check("正文 10".equals(loaded.text) && !loaded.failed, "渲染任务发布完整正文");

            // 删除后重新请求同一键，旧结果不得接管新条目。
            media.clear();
            var old = media.get(first, a);
            drain(io);
            media.remove(a.key());
            var replacement = media.get(first, a);
            drain(io); drain(ui);
            check(old.text == null && !old.failed, "移除条目的迟到结果被丢弃");
            check(replacement != old && "正文 10".equals(replacement.text), "同键新条目正常读取");

            // 清空之前的回调不能占用或提前释放新一代的四个加载名额。
            media.clear();
            var abandoned = media.get(first, a);
            media.clear();
            var entries = new ArrayList<FavoriteEntry>();
            for (int i = 20; i < 25; i++) entries.add(add(first, i));
            for (int i = 0; i < 4; i++) check(media.get(first, entries.get(i)) != null, "新一代加载名额 " + i);
            io.remove().run(); ui.remove().run();
            check(abandoned.text == null, "关闭前的旧结果不能复活");
            check(media.get(first, entries.get(4)) == null, "旧回调不会减少新一代在途计数");
            drain(io); drain(ui);
            check(media.get(first, entries.get(4)) != null, "当前四项完成后释放加载名额");
            drain(io); drain(ui);

            // 同一消息身份出现在不同环境时，前一环境的任务不能污染后一环境。
            media.clear();
            var oldScope = media.get(first, a);
            var second = new FavoriteStore(root.resolve("second.json"));
            var b = add(second, 10);
            var newScope = media.get(second, b);
            drain(io); drain(ui);
            check(oldScope.text == null && "正文 10".equals(newScope.text), "切换来源后丢弃旧环境结果");

            Files.delete(second.contentPath(b));
            media.remove(b.key());
            var missing = media.get(second, b);
            drain(io); drain(ui);
            check(missing.failed && missing.text == null, "文件缺失作为单条失败反馈");

            var png = new ByteArrayOutputStream();
            var pixels = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
            ImageIO.write(pixels, "PNG", png); pixels.flush();
            byte[] data = png.toByteArray();
            var image = new FavoriteEntry(new UUID(0, 30), new UUID(0, 2), false, new UUID(0, 3),
                    "发送者", 100, 200, FavoriteEntry.Kind.IMAGE, data.length, 2, 2, 1, 0);
            check(second.add(image, data) == FavoriteStore.Result.OK, "准备已提交 PNG");
            Files.write(second.contentPath(image), new byte[data.length]);
            var broken = media.get(second, image);
            drain(io); drain(ui);
            check(broken.failed && broken.texture == null, "损坏 PNG 不上传贴图");

            media.clear();
            FavoriteMedia.Loaded eldest = null;
            FavoriteEntry eldestEntry = null;
            for (int i = 100; i < 170; i++) {
                var entry = add(first, i);
                var value = media.get(first, entry);
                if (i == 100) { eldest = value; eldestEntry = entry; }
                drain(io); drain(ui);
            }
            check(media.get(first, eldestEntry) != eldest && io.size() == 1, "超过条数预算后回收最旧缓存");
            drain(io); drain(ui);
            media.clear();
            check(media.get(first, a) != loaded && io.size() == 1, "清空后再次访问重新读取");
            media.clear(); drain(io); drain(ui);
        } finally {
            media.clear();
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("FavoriteMediaTest: " + checks + " checks passed");
    }
}
