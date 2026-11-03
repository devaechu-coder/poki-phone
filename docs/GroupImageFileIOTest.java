package com.november.mcphone.feature.chat.group;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

/** 实际磁盘写入与故障注入；不需要创建 MinecraftServer 或图形窗口。 */
public final class GroupImageFileIOTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static void fails(Path file, byte[] data) throws IOException {
        try {
            GroupImageFileIO.write(file, data, (temp, bytes) -> {
                Files.write(temp, Arrays.copyOf(bytes, 2)); throw new IOException("模拟写到一半失败");
            });
            throw new AssertionError("应报告写入失败");
        } catch (IOException expected) { checks++; }
    }
    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("mcphone-group-image-io-");
        byte[] first = {1, 2, 3, 4}, second = {5, 6, 7, 8};
        try {
            Path file = folder.resolve("images/content.png");
            fails(file, first);
            check(!Files.exists(file), "首次失败不会留下可被去重认领的最终文件");
            GroupImageFileIO.write(file, first);
            check(Arrays.equals(Files.readAllBytes(file), first), "失败后重试得到完整内容");
            fails(file, second);
            check(Arrays.equals(Files.readAllBytes(file), first), "替换失败保留已有完整内容");
            Files.write(file, new byte[]{9, 9}); GroupImageFileIO.write(file, first);
            check(Arrays.equals(Files.readAllBytes(file), first), "既有截断文件会修复");
            Files.write(file, second); GroupImageFileIO.write(file, first);
            check(Arrays.equals(Files.readAllBytes(file), first), "同长度损坏文件也会修复");
            GroupImageFileIO.write(file, first, (p, b) -> { throw new AssertionError("完整重复内容不必重写"); });
            check(Arrays.equals(Files.readAllBytes(file), first), "正常去重保留完整内容");
            try (var files = Files.list(file.getParent())) { check(files.count() == 1, "成功与失败都清理临时文件"); }
        } finally {
            try (var files = Files.walk(folder)) { for (var p : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); }
        }
        System.out.println("全部通过：" + checks + " 条断言");
    }
}
