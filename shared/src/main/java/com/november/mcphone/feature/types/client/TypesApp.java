package com.november.mcphone.feature.types.client;

import com.november.mcphone.core.client.PhoneApp;
import com.november.mcphone.core.client.PhoneScreen;
import net.minecraft.client.Minecraft;

/** 타입 상성표. 서버에 아무것도 안 물음 (표는 클라에 내장). 아이콘: textures/app/types.png */
public final class TypesApp extends PhoneApp {
    public TypesApp() { super("types"); }

    @Override
    public void onPress() {
        if (Minecraft.getInstance().screen instanceof PhoneScreen ps) ps.navigateTo(PhoneScreen.Mode.TYPES);
    }
}
