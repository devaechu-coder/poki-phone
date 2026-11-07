package com.november.mcphone.feature.support.client;

import com.november.mcphone.core.client.PhoneApp;
import com.november.mcphone.core.client.PhoneScreen;
import net.minecraft.client.Minecraft;

/** 포키 고객센터: 문의·신고·내 문의 내역(운영진 답변)·운영진 문의함. 아이콘 textures/app/support.png */
public final class SupportApp extends PhoneApp {
    public SupportApp() { super("support"); }

    @Override
    public void onPress() {
        if (Minecraft.getInstance().screen instanceof PhoneScreen ps) ps.navigateTo(PhoneScreen.Mode.SUPPORT);
    }
}
