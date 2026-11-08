package com.november.mcphone.feature.wardrobe.client;

import com.november.mcphone.core.client.PhoneApp;
import com.november.mcphone.core.client.PhoneScreen;
import net.minecraft.client.Minecraft;

/**
 * 포키 옷장 (치장 미리보기). 아이콘: assets/mcphone/textures/app/wardrobe.png (20×20)
 * 모두에게 보임 (치장 미리보기·입어보기, 서버에 아무것도 안 물음).
 */
public final class WardrobeApp extends PhoneApp {
    public WardrobeApp() { super("wardrobe"); }


    @Override
    public void onPress() {
        if (Minecraft.getInstance().screen instanceof PhoneScreen ps) ps.navigateTo(PhoneScreen.Mode.WARDROBE);
    }
}
