package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.PhoneItem;
import com.november.mcphone.core.client.PhoneToast;
import com.november.mcphone.feature.chat.notice.FriendNotice;
import net.minecraft.client.Minecraft;

/** 포키: 친구 요청·수락 팝업 (포키톡 메시지 팝업과 같은 모양) */
public final class FriendNoticeClient {
    private FriendNoticeClient() {}

    public static void accept(FriendNotice.Notice n) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null || !PhoneItem.isCarriedBy(mc.player)) return;
            String text = n.kind() == FriendNotice.Kind.REQUEST ? "친구 요청을 보냈어요" : "친구가 됐어요!";
            mc.getToasts().addToast(new PhoneToast(n.who(), n.name(), text));
        });
    }
}
