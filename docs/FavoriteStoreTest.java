package com.november.mcphone.feature.chat.client.favorite;
import java.nio.file.*;
import java.util.UUID;
public final class FavoriteStoreTest {
    private static int checks;
    private static void check(boolean ok,String why) { checks++;if (!ok) throw new AssertionError(why); }
    private static FavoriteText entry(UUID id,UUID target,boolean group,String text) { return new FavoriteText(id,target,group,new UUID(0,4),"甲","会话",text,100,200); }
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("mcphone-favorites-");UUID id=new UUID(0,1),target=new UUID(0,2);
        try {
            Path file=dir.resolve("favorites.json");var store=new FavoriteStore(file);var original=entry(id,target,false,"完整正文\n第二行😀");
            check(store.add(original)==FavoriteStore.Result.OK,"收藏写入成功");check(Files.exists(store.index()),"实际索引存在");
            check(store.add(entry(id,target,false,"改写"))==FavoriteStore.Result.EXISTS,"同来源重复收藏不覆盖原文");
            check(store.text(store.snapshot().get(0)).equals(original.text()),"独立文本保存整条正文与换行");
            check(store.add(entry(id,target,true,"群聊"))==FavoriteStore.Result.OK,"群与私聊相同 ID 互相独立");
            var reopened=new FavoriteStore(file);check(reopened.available()&&reopened.snapshot().size()==2,"重启加载");
            check(reopened.snapshot().get(0).group(),"最近收藏靠前");var frozen=reopened.snapshot();
            check(reopened.remove(original.key())==FavoriteStore.Result.OK,"移除并落盘");check(new FavoriteStore(file).snapshot().size()==1,"移除不在重启后恢复");check(frozen.size()==2,"已显示快照不会被后台修改");
            String persisted=Files.readString(store.index());
            String before="{\"format\":1,\"entries\":"+new com.google.gson.Gson().toJson(java.util.List.of(entry(id,target,true,"群聊")))+"}";
            var failing=new FavoriteStore(file,(p,j)->{ throw new java.io.IOException("模拟磁盘失败"); });
            check(failing.add(original)==FavoriteStore.Result.STORAGE_ERROR,"写失败不伪装收藏成功");check(failing.snapshot().size()==1,"写失败内存不变");
            check(failing.remove(failing.snapshot().get(0).key())==FavoriteStore.Result.STORAGE_ERROR,"删除失败有反馈");check(failing.snapshot().size()==1&&Files.readString(store.index()).equals(persisted),"删除失败内存磁盘都保留");
            for (String broken:new String[]{"{","{\"format\":2,\"entries\":[]}","{\"format\":1,\"entries\":[{}]}"}) {
                Path bad=dir.resolve("bad.json");Files.writeString(bad,broken);var locked=new FavoriteStore(bad);
                check(!locked.available(),"损坏或未来格式锁定");check(locked.add(original)==FavoriteStore.Result.STORAGE_ERROR&&Files.readString(bad).equals(broken),"不能以空收藏覆盖损坏文件");
            }
            // 从真实写入文件删掉关键字段，不能把缺省 false/0 当作另一条有效收藏。
            for (String key:new String[]{"message","target","group","sender","senderName","sourceName","text","messageTime","savedTime"}) {
                var document=com.google.gson.JsonParser.parseString(before).getAsJsonObject();
                document.getAsJsonArray("entries").get(0).getAsJsonObject().remove(key);
                locked(dir,document.toString(),original,"缺少字段 "+key);
            }
            for (String[] mutation:new String[][]{
                    {"group","\"true\""},{"group","1"},{"text","123"},{"savedTime","\"200\""},
                    {"savedTime","200.5"},{"savedTime","9223372036854775808"},{"savedTime","null"},
                    {"message","\"1-1-1-1-1\""}}) {
                var document=com.google.gson.JsonParser.parseString(before).getAsJsonObject();
                document.getAsJsonArray("entries").get(0).getAsJsonObject().add(mutation[0],com.google.gson.JsonParser.parseString(mutation[1]));
                locked(dir,document.toString(),original,"错误类型或范围 "+mutation[0]);
            }
            locked(dir,"{\"format\":\"1\",\"entries\":[]}",original,"格式号不能是字符串");
            locked(dir,"{\"format\":1.5,\"entries\":[]}",original,"格式号不能截断小数");
            Path invalidUtf8=dir.resolve("invalid-utf8.json");Files.write(invalidUtf8,new byte[]{(byte)0xc3,0x28});
            check(!new FavoriteStore(invalidUtf8).available(),"非法 UTF-8 锁定，不以替代字形读取");
            Path excessive=dir.resolve("excessive.json");Files.write(excessive,new byte[FavoriteStore.MAX_BYTES+1]);
            check(!new FavoriteStore(excessive).available()&&Files.size(excessive)==FavoriteStore.MAX_BYTES+1,"超大文件有界读取并原样保留");
            var capacity=new FavoriteStore(dir.resolve("capacity.json"),(p,j)->{});
            for (int i=0;i<FavoriteStore.MAX_ENTRIES;i++) check(capacity.add(entry(new UUID(3,i),target,false,"文字"))==FavoriteStore.Result.OK,"容量内可写");
            check(capacity.add(original)==FavoriteStore.Result.LIMIT,"超出容量明确拒绝");
            Path a=ChatFavorites.path(dir,id,"remote:a:25565"),b=ChatFavorites.path(dir,id,"remote:b:25565"),c=ChatFavorites.path(dir,target,"remote:a:25565");
            check(!a.equals(b)&&!a.equals(c),"账号和服务器相互隔离");check(a.startsWith(dir)&&a.getFileName().toString().matches("[0-9a-f]{64}\\.json"),"路径使用摘要，不将服务器字符拼入路径");
        } finally { try(var paths=Files.walk(dir)) { for (var p:paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p); } }
        System.out.println("全部通过："+checks+" 条断言");
    }
    private static void locked(Path dir,String json,FavoriteText entry,String reason) throws Exception {
        Path file=dir.resolve("schema-invalid.json");Files.writeString(file,json);
        var store=new FavoriteStore(file);
        check(!store.available()&&store.snapshot().isEmpty(),reason+"：整份文件锁定");
        check(store.add(entry)==FavoriteStore.Result.STORAGE_ERROR,reason+"：不能重写");
        check(Files.readString(file).equals(json),reason+"：原文保留");
    }
}
