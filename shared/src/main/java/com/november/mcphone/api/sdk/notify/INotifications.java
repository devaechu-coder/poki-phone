package com.november.mcphone.api.sdk.notify;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * 通知的收发（施工方案 §23.3）。
 *
 * <p><b>角标只有这一个来源</b>：{@code IPhoneApp.getBadgeCount()} 读的是这里的未读数，
 * 走本地缓存（§21.4），不是每帧问服务端。
 */
public interface INotifications {

    /** 发一条。超过 {@link Notification#MAX_PER_APP} 时淘汰最旧的已读项。 */
    void post(UUID player, Notification notification);

    /** 某个 App 的未读数 —— 角标画的就是它。 */
    int unread(UUID player, ResourceLocation appId);

    /** 某个 App 的通知，新的在前。 */
    List<Notification> list(UUID player, ResourceLocation appId);

    /** 标记已读。{@code topic} 为 null 时标这个 App 的全部。 */
    void markRead(UUID player, ResourceLocation appId, String topic);

    /** 标记单条已读。{@code Notification.id} 就是为这条方法存在的。 */
    void markRead(UUID player, ResourceLocation appId, long notificationId);
}
