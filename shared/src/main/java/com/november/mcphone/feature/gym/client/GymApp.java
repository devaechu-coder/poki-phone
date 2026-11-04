package com.november.mcphone.feature.gym.client;

import com.november.mcphone.core.client.PhoneApp;
import com.november.mcphone.core.client.PhoneScreen;
import net.minecraft.client.Minecraft;

/** 포키 체육관 앱. 아이콘: assets/mcphone/textures/app/gym.png (20×20) */
public final class GymApp extends PhoneApp {
    public GymApp() { super("gym"); }

    @Override
    public void onPress() {
        if (Minecraft.getInstance().screen instanceof PhoneScreen ps) ps.navigateTo(PhoneScreen.Mode.GYM);
    }
}
