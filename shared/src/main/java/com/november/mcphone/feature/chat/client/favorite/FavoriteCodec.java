package com.november.mcphone.feature.chat.client.favorite;

import com.google.gson.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 旧版完整文字快照只用于迁移；新版索引不包含正文，也不接受外部文件路径。 */
final class FavoriteCodec {
    private static final Gson JSON = new Gson();
    private FavoriteCodec() {}
    static String encode(Map<String, FavoriteEntry> values) {
        var doc = new JsonObject(); doc.addProperty("format", 2);
        doc.add("entries", JSON.toJsonTree(values.values())); return JSON.toJson(doc);
    }
    static Map<String, FavoriteEntry> decode(String json) {
        var doc = document(json, 2); var values = new LinkedHashMap<String, FavoriteEntry>();
        for (var raw : doc.getAsJsonArray("entries")) {
            var row = raw.getAsJsonObject();
            var item = new FavoriteEntry(uuid(row,"message"), uuid(row,"target"), bool(row,"group"),
                    uuid(row,"sender"), string(row,"senderName"), integer(row,"messageTime"), integer(row,"savedTime"),
                    FavoriteEntry.Kind.valueOf(string(row,"kind")), integer(row,"bytes"),
                    Math.toIntExact(integer(row,"width")), Math.toIntExact(integer(row,"height")),
                    Math.toIntExact(integer(row,"frames")), Math.toIntExact(integer(row,"frameMs")));
            if (values.putIfAbsent(item.key(), item) != null) throw new IllegalArgumentException("收藏键重复");
        }
        return values;
    }
    static Map<String, FavoriteText> legacy(String json) {
        var doc = document(json, 1); var values = new LinkedHashMap<String, FavoriteText>();
        for (var raw : doc.getAsJsonArray("entries")) {
            var row = raw.getAsJsonObject();
            var item = new FavoriteText(uuid(row,"message"), uuid(row,"target"), bool(row,"group"), uuid(row,"sender"),
                    string(row,"senderName"), string(row,"sourceName"), string(row,"text"),
                    integer(row,"messageTime"), integer(row,"savedTime"));
            if (values.putIfAbsent(item.key(), item) != null) throw new IllegalArgumentException("收藏键重复");
        }
        return values;
    }
    private static JsonObject document(String json, int format) {
        var doc = JsonParser.parseString(json).getAsJsonObject();
        if (integer(doc,"format") != format) throw new IllegalArgumentException("未知收藏格式");
        if (field(doc,"entries").getAsJsonArray().size() > FavoriteStore.MAX_ENTRIES)
            throw new IllegalArgumentException("收藏数量超限");
        return doc;
    }
    private static JsonElement field(JsonObject row, String key) {
        var value = row.get(key);
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("收藏缺少字段 " + key);
        return value;
    }
    private static boolean bool(JsonObject row, String key) {
        var value = field(row,key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("布尔类型错误");
        return value.getAsBoolean();
    }
    private static String string(JsonObject row, String key) {
        var value = field(row,key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("字符串类型错误");
        return value.getAsString();
    }
    private static UUID uuid(JsonObject row, String key) {
        String text = string(row,key); UUID id = UUID.fromString(text);
        if (!id.toString().equalsIgnoreCase(text)) throw new IllegalArgumentException("无效 UUID");
        return id;
    }
    private static long integer(JsonObject row, String key) {
        var value = field(row,key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("整数类型错误");
        return value.getAsBigDecimal().longValueExact();
    }
}
