package com.november.mcphone.feature.chat.group;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 像素文件先完整写入同目录临时文件；失败的半份文件不能成为后续去重的命中。 */
final class GroupImageFileIO {
    @FunctionalInterface interface Writer { void write(Path path, byte[] bytes) throws IOException; }
    private GroupImageFileIO() {}

    static void write(Path file, byte[] bytes) throws IOException {
        write(file, bytes, (path, data) -> Files.write(path, data));
    }

    static void write(Path file, byte[] bytes, Writer writer) throws IOException {
        Files.createDirectories(file.getParent());
        // 正常重复上传只读一次；已有残缺文件必须修复，不能仅凭文件存在就跳过。
        if (Files.isRegularFile(file) && Files.size(file) == bytes.length) {
            try (var input = Files.newInputStream(file)) {
                if (java.util.Arrays.equals(input.readNBytes(bytes.length + 1), bytes)) return;
            }
        }
        Path temp = Files.createTempFile(file.getParent(), file.getFileName() + ".", ".tmp");
        try {
            writer.write(temp, bytes);
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }
}
