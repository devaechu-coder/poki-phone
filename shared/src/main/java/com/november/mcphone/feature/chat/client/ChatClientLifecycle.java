package com.november.mcphone.feature.chat.client;

import com.november.mcphone.feature.chat.client.favorite.ChatFavorites;
import com.november.mcphone.feature.chat.client.profile.ProfileClientCache;
import com.november.mcphone.feature.chat.net.ChatClientCache;

/** 四个平台在客户端主线程断线时调用同一个入口，新增会话状态只在这里登记清理。 */
public final class ChatClientLifecycle {
    private ChatClientLifecycle() {}
    public static void disconnect() {
        // 先使旧任务和票据失效，再释放像素；本机收藏和文件压缩缓存不删除。
        ChatImageSender.clear();
        GroupTransport.clear();
        ChatClientCache.clear(); // 同时清理纯数据的群缓存。
        ProfileClientCache.clear();
        ChatFavorites.clear();
        ChatImageCache.clear();
    }
}
