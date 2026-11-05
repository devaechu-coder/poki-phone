package com.november.mcphone.feature.wardrobe.client;

import com.november.mcphone.core.client.PhoneApp;
import com.november.mcphone.core.client.PhoneScreen;
import net.minecraft.client.Minecraft;

/**
 * 포키 옷장 (치장 미리보기). 아이콘: assets/mcphone/textures/app/wardrobe.png (20×20)
 * 지금은 OP(권한 2)만 홈 화면에 보인다. 모두에게 열 때는 isHidden 을 지우면 됨.
 * 서버에 아무것도 안 묻는 화면이라 클라이언트에서 막는 걸로 충분.
 */
public final class WardrobeApp extends PhoneApp {
    public WardrobeApp() { super("wardrobe"); }

    @Override
    public boolean isHidden() {
        var p = Minecraft.getInstance().player;
        return p == null || !p.hasPermissions(2);
    }

    @Override
    public void onPress() {
        if (isHidden()) return;
        if (Minecraft.getInstance().screen instanceof PhoneScreen ps) ps.navigateTo(PhoneScreen.Mode.WARDROBE);
    }
}
