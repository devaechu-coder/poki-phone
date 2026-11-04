package com.november.mcphone.feature.chat.client;

import com.november.mcphone.feature.chat.PokemonCard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.Map;

/** 포키톡 포켓몬 카드 그림 (모델 아이콘 + 이름·레벨·성격·특성 + 개체값 막대 + 기술 + 어버이) */
public final class PokemonCardView {
    private PokemonCardView() {}
    public static final int WIDTH = 100, HEIGHT = 104;
    private static final int BORDER = 0xFFFFAAC8, GOLD = 0xFFE8B323, BG = 0xFFFFFFFF, INK = 0xFF2B2533, MUTE = 0xFF7A7286,
            BAR_BG = 0xFFE7E2EE, BAR = 0xFFFFAAC8, BAR_MAX = 0xFFE2588F;
    private static final String[] STATS = {"hp", "atk", "def", "spa", "spd", "spe"};

    private static String tk(String key, String fallback) {
        return I18n.exists(key) ? Component.translatable(key).getString() : fallback;
    }

    private static String pretty(String id) {
        if (id.isEmpty()) return "";
        String s = id.replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String speciesName(String sp) {
        String p = sp.contains(":") ? sp.substring(sp.indexOf(':') + 1) : sp;
        return tk("cobblemon.species." + p + ".name", pretty(p));
    }

    public static void draw(GuiGraphics g, Font font, String text, int x, int y, int w) {
        Map<String, String> c = PokemonCard.parse(text);
        boolean shiny = "1".equals(c.get("sh"));
        ChatUi.roundedFill(g, x, y, w, HEIGHT, shiny ? GOLD : BORDER);
        ChatUi.roundedFill(g, x + 1, y + 1, w - 2, HEIGHT - 2, BG);
        String sp = c.getOrDefault("sp", "");
        int icon = 26;
        g.fill(x + 3, y + 3, x + 3 + icon, y + 3 + icon, 0xFFF6F4FA);
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        if (!CobblemonIcon.draw(g, sp, shiny, x + 3, y + 3, icon, partial))
            ChatUi.buttonText(g, font, "?", x + 3 + icon / 2f, y + 3 + icon / 2f, ChatLayout.TEXT_SCALE, MUTE);
        int tx = x + icon + 6, tw = w - icon - 9;
        String name = speciesName(sp) + (shiny ? " ★" : "");
        ChatUi.label(g, font, ChatUi.truncate(font, name, tw, ChatLayout.TEXT_SCALE), tx, y + 8, ChatLayout.TEXT_SCALE, shiny ? 0xFFC8820A : INK);
        StringBuilder sub = new StringBuilder();
        if (c.containsKey("lv")) sub.append("Lv.").append(c.get("lv"));
        String gd = c.getOrDefault("g", "");
        if (gd.equals("M")) sub.append(" ♂"); else if (gd.equals("F")) sub.append(" ♀");
        if (c.containsKey("na")) sub.append(" · ").append(tk("cobblemon.nature." + c.get("na"), pretty(c.get("na"))));
        ChatUi.label(g, font, ChatUi.truncate(font, sub.toString(), tw, ChatLayout.META_SCALE), tx, y + 17, ChatLayout.META_SCALE, MUTE);
        if (c.containsKey("ab")) {
            String ab = Component.translatable("mcphone.chat.card.ability").getString() + " " + tk("cobblemon.ability." + c.get("ab"), pretty(c.get("ab")));
            ChatUi.label(g, font, ChatUi.truncate(font, ab, tw, ChatLayout.META_SCALE), tx, y + 25, ChatLayout.META_SCALE, INK);
        }
        // 개체값
        int by = y + 33;
        String[] iv = c.getOrDefault("iv", "").split("\\.");
        int labelW = 15, barX = x + 4 + labelW, barW = w - labelW - 20;
        for (int i = 0; i < 6; i++) {
            int v = 0;
            if (i < iv.length) try { v = Integer.parseInt(iv[i]); } catch (NumberFormatException ignored) {}
            ChatUi.label(g, font, Component.translatable("mcphone.chat.card.stat." + STATS[i]).getString(), x + 4, by + 3, ChatLayout.GROUP_NAME_SCALE, MUTE);
            g.fill(barX, by + 1, barX + barW, by + 5, BAR_BG);
            g.fill(barX, by + 1, barX + Math.round(barW * Math.min(31, v) / 31f), by + 5, v >= 31 ? BAR_MAX : BAR);
            ChatUi.label(g, font, String.valueOf(v), barX + barW + 3, by + 3, ChatLayout.GROUP_NAME_SCALE, INK);
            by += 7;
        }
        // 기술
        by += 2;
        String[] mv = c.getOrDefault("mv", "").isEmpty() ? new String[0] : c.get("mv").split(",");
        for (int row = 0; row < 2; row++) {
            StringBuilder line = new StringBuilder();
            for (int k = row * 2; k < Math.min(mv.length, row * 2 + 2); k++) {
                if (line.length() > 0) line.append(" · ");
                line.append(tk("cobblemon.move." + mv[k], pretty(mv[k])));
            }
            if (line.length() > 0) ChatUi.label(g, font, ChatUi.truncate(font, line.toString(), w - 8, ChatLayout.META_SCALE), x + 4, by + 3, ChatLayout.META_SCALE, INK);
            by += 8;
        }
        if (c.containsKey("ot"))
            ChatUi.label(g, font, ChatUi.truncate(font, Component.translatable("mcphone.chat.card.ot", c.get("ot")).getString(), w - 8, ChatLayout.GROUP_NAME_SCALE),
                    x + 4, by + 3, ChatLayout.GROUP_NAME_SCALE, MUTE);
    }
}
