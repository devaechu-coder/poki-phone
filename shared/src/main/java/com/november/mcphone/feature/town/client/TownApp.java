package com.november.mcphone.feature.town.client;

import com.november.mcphone.core.client.PhoneApp;
import com.november.mcphone.core.client.PhoneScreen;
import net.minecraft.client.Minecraft;

/** 포키 마을: 내 마을·주민·알바·초대, 전체 마을 목록. 아이콘 textures/app/town.png (20×20). 실제 마을은 Towny (PokiTown 플러그인) */
public final class TownApp extends PhoneApp {
    public TownApp() { super("town"); }

    @Override
    public void onPress() {
        if (Minecraft.getInstance().screen instanceof PhoneScreen ps) ps.navigateTo(PhoneScreen.Mode.TOWN);
    }
}
