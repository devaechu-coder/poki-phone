package com.november.mcphone.feature.chat.client.favorite;

import com.november.mcphone.feature.chat.ChatImage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** 索引只存元信息；正文位于同一环境的 text/、image/，文件名由身份计算。 */
public record FavoriteEntry(UUID message, UUID target, boolean group, UUID sender, String senderName,
                            long messageTime, long savedTime, Kind kind, long bytes,
                            int width, int height, int frames, int frameMs) {
    public enum Kind {
        TEXT("text", ".txt"), IMAGE("image", ".png");
        public final String folder, extension;
        Kind(String folder, String extension) { this.folder = folder; this.extension = extension; }
    }
    public FavoriteEntry {
        Objects.requireNonNull(message); Objects.requireNonNull(target); Objects.requireNonNull(sender);
        Objects.requireNonNull(senderName); Objects.requireNonNull(kind);
        if (senderName.length() > 64 || messageTime < 0 || savedTime < 0 || bytes <= 0)
            throw new IllegalArgumentException("无效收藏元信息");
        if (kind == Kind.TEXT) {
            if (bytes > com.november.mcphone.feature.chat.TextBody.MAX_LENGTH * 4L
                    || width != 0 || height != 0 || frames != 1 || frameMs != 0)
                throw new IllegalArgumentException("无效文字收藏");
        } else if (bytes > ChatImage.MAX_BYTES_CEILING || width < 1 || height < 1
                || width > ChatImage.MAX_SIDE || height > ChatImage.MAX_SIDE
                || frames < 1 || frames > ChatImage.MAX_FRAMES
                || frameMs < 0 || frames > 1 && frameMs < 1 || frames == 1 && frameMs != 0)
            throw new IllegalArgumentException("无效图片收藏");
    }
    public String key() { return (group ? "group:" : "direct:") + target + ":" + message; }
    public String fileName() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(key().getBytes(StandardCharsets.UTF_8))) + kind.extension;
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static FavoriteEntry text(FavoriteText old) {
        return new FavoriteEntry(old.message(), old.target(), old.group(), old.sender(), old.senderName(),
                old.messageTime(), old.savedTime(), Kind.TEXT, old.text().getBytes(StandardCharsets.UTF_8).length, 0, 0, 1, 0);
    }
}
