package com.november.mcphone.feature.chat.client.favorite;
import com.november.mcphone.feature.chat.TextBody;
import java.util.Objects;
import java.util.UUID;
/** 旧版文字 JSON 的迁移快照；新版持久化使用 FavoriteEntry 与独立正文文件。 */
public record FavoriteText(UUID message, UUID target, boolean group, UUID sender,
                           String senderName, String sourceName, String text, long messageTime, long savedTime) {
    public FavoriteText {
        Objects.requireNonNull(message); Objects.requireNonNull(target); Objects.requireNonNull(sender);
        Objects.requireNonNull(senderName); Objects.requireNonNull(sourceName); Objects.requireNonNull(text);
        if (senderName.length()>64 || sourceName.length()>128 || text.isBlank() || text.length()>TextBody.MAX_LENGTH
                || messageTime<0 || savedTime<0) throw new IllegalArgumentException("无效文字收藏");
    }
    public String key() { return (group?"group:":"direct:")+target+":"+message; }
}
