package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.PlayerAvatar;
import com.november.mcphone.feature.call.net.CallWire;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** 포키폰 통화 화면: 걸려 온 전화(받기/거절) · 거는 중 · 통화 중(참여자·시간·끊기) */
public final class CallPage {
    private CallPage() {}
    private record Hit(ChatLayout.Rect r, Runnable run) {}
    private static final List<Hit> HITS = new ArrayList<>();
    private static final int RED = 0xFFE5484D, GREEN = 0xFF3FA75A;

    static String tr(String k) { return Component.translatable("mcphone.call." + k).getString(); }

    public static String timer(long started) {
        if (started <= 0) return "";
        long s = Math.max(0, (System.currentTimeMillis() - started) / 1000);
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    /** 보여 줄 게 없으면 false (그럼 PhoneScreen 이 이전 화면으로) */
    public static boolean visible() { return CallClient.incoming != null || CallClient.active != null; }

    private static void button(GuiGraphics g, Font font, ChatLayout.Rect r, String label, int color, int mx, int my, Runnable run) {
        ChatUi.roundedFill(g, r.x(), r.y(), r.width(), r.height(), r.contains(mx, my) ? (color & 0x00FFFFFF) | 0xCC000000 : color);
        ChatUi.buttonText(g, font, label, r.x() + r.width() / 2f, r.y() + r.height() / 2f, ChatLayout.TEXT_SCALE, 0xFFFFFFFF);
        HITS.add(new Hit(r, run));
    }

    public static void render(GuiGraphics g, int left, int top, int sw, int sh, int statusH, int navH, int mx, int my, Font font) {
        HITS.clear();
        CallClient.tick();
        int x = left + 6, w = sw - 12, y = top + statusH + 10;
        int bottom = top + sh - navH - 6;
        var in = CallClient.incoming;
        var act = CallClient.active;
        if (in != null && act == null) {
            ChatUi.buttonText(g, font, tr(in.group() ? "incoming_group" : "incoming"), x + w / 2f, y + 4, ChatLayout.META_SCALE, FontPalette.subtle());
            if (!in.group()) PlayerAvatar.draw(g, in.from(), x + w / 2 - 14, y + 16, 28);
            ChatUi.buttonText(g, font, ChatUi.truncate(font, in.title(), w, ChatLayout.TEXT_SCALE), x + w / 2f, y + 54, ChatLayout.TEXT_SCALE, FontPalette.title());
            int bw = (w - 6) / 2;
            button(g, font, new ChatLayout.Rect(x, bottom - 18, bw, 16), tr("decline"), RED, mx, my, CallClient::decline);
            button(g, font, new ChatLayout.Rect(x + bw + 6, bottom - 18, bw, 16), tr("accept"), GREEN, mx, my, CallClient::accept);
            return;
        }
        if (act == null) return;
        boolean waiting = act.started() <= 0;
        ChatUi.buttonText(g, font, ChatUi.truncate(font, act.title(), w, ChatLayout.TEXT_SCALE), x + w / 2f, y + 4, ChatLayout.TEXT_SCALE, FontPalette.title());
        ChatUi.buttonText(g, font, waiting ? tr("calling") : timer(act.started()), x + w / 2f, y + 14, ChatLayout.META_SCALE, waiting ? FontPalette.subtle() : GREEN);
        y += 24;
        for (var m : act.members()) {
            if (y > bottom - 40) break;
            ChatUi.glass(g, ChatGlass.Surface.CARD, x, y, w, 16, false);
            PlayerAvatar.draw(g, m.id(), x + 3, y + 3, 10);
            ChatUi.label(g, font, ChatUi.truncate(font, m.name(), w - 50, ChatLayout.TEXT_SCALE), x + 17, y + 8, ChatLayout.TEXT_SCALE,
                    m.ringing() ? FontPalette.subtle() : FontPalette.body());
            String st = tr(m.ringing() ? "member_ringing" : "member_in");
            int stw = ChatUi.width(font, st, ChatLayout.META_SCALE);
            ChatUi.label(g, font, st, x + w - stw - 4, y + 8, ChatLayout.META_SCALE, m.ringing() ? FontPalette.subtle() : GREEN);
            y += 18;
        }
        ChatUi.buttonText(g, font, tr("hint"), x + w / 2f, bottom - 26, ChatLayout.GROUP_NAME_SCALE, FontPalette.dim());
        button(g, font, new ChatLayout.Rect(x + w / 4, bottom - 18, w / 2, 16), tr("hangup"), RED, mx, my, CallClient::hangup);
    }

    public static boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;
        for (int i = HITS.size() - 1; i >= 0; i--) if (HITS.get(i).r().contains(mx, my)) { HITS.get(i).run().run(); return true; }
        return true;
    }
}
