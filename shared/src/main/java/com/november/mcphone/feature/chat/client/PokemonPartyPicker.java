package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.GuiUtil;
import com.november.mcphone.core.client.PhoneTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** 포키톡: 카드로 보낼 내 파티 포켓몬 고르기 (클라이언트에 있는 코블몬 파티 정보를 읽기만 함) */
public final class PokemonPartyPicker {
    private int selected = -1, hovered = -1;
    private record Slot(int index, String species, boolean shiny, String label) {}

    public void open() { selected = -1; }
    public void close() { selected = -1; hovered = -1; }
    public int consumeSelection() { int s = selected; selected = -1; return s; }

    private static Object get(Object o, String name) {
        if (o == null) return null;
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String n : new String[]{"get" + cap, name, "is" + cap}) {
            try { Method m = o.getClass().getMethod(n); return m.invoke(o); }
            catch (NoSuchMethodException ignored) {}
            catch (Throwable t) { return null; }
        }
        return null;
    }

    private static List<Slot> party() {
        List<Slot> out = new ArrayList<>();
        try {
            Class<?> cc = Class.forName("com.cobblemon.mod.common.client.CobblemonClient");
            Object client = cc.getField("INSTANCE").get(null);
            Object party = get(get(client, "storage"), "party");
            if (party == null) return out;
            // 코블몬 ClientParty: slots(빈칸 포함 목록)가 있으면 그 순서가 서버 파티 칸 번호와 같음
            List<?> slots = null;
            Object raw = get(party, "slots");
            if (raw instanceof List<?> l) slots = l;
            else if (party instanceof Iterable<?> it) { var l = new ArrayList<Object>(); for (Object o : it) l.add(o); slots = l; }
            if (slots == null) {
                try {
                    Method g = party.getClass().getMethod("get", int.class);
                    var l = new ArrayList<Object>();
                    for (int i = 0; i < 6; i++) l.add(g.invoke(party, i));
                    slots = l;
                } catch (Throwable ignored) {}
            }
            if (slots == null) {
                com.november.mcphone.MCphone.LOGGER.warn("[포키폰] 파티를 못 읽음: {}", party.getClass().getName());
                return out;
            }
            for (int i = 0; i < Math.min(6, slots.size()); i++) {
                Object pk = slots.get(i);
                if (pk == null) continue;
                Object id = get(get(pk, "species"), "resourceIdentifier");
                if (id == null) continue;
                Object lv = get(pk, "level");
                String name = PokemonCardView.speciesName(id.toString());
                Object nick = get(pk, "nickname");
                if (nick instanceof Component nc && !nc.getString().isBlank()) name = nc.getString();
                out.add(new Slot(i, id.toString(), Boolean.TRUE.equals(get(pk, "shiny")), name + (lv != null ? "  Lv." + lv : "")));
            }
        } catch (Throwable t) {
            com.november.mcphone.MCphone.LOGGER.warn("[포키폰] 파티 읽기 실패: {}", t.toString());
        }
        return out;
    }

    public void render(GuiGraphics g, int phoneLeft, int phoneTop, int screenW, int screenH, int statusH, int navH,
                       int mx, int my, Font font) {
        int x = phoneLeft + 6, y = phoneTop + statusH + 4, w = screenW - 12;
        g.drawString(font, GuiUtil.truncate(font, Component.translatable("mcphone.chat.pick_pokemon").getString(), w), x, y, FontPalette.title(), true);
        y += font.lineHeight + 4;
        g.fill(x, y, x + w, y + 1, PhoneTheme.COLOR_DIVIDER);
        y += 4;
        hovered = -1;
        var slots = party();
        if (slots.isEmpty()) {
            for (var line : font.split(Component.translatable("mcphone.chat.pick_pokemon_empty"), w)) {
                g.drawString(font, line, x, y, FontPalette.subtle(), false); y += font.lineHeight;
            }
            return;
        }
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        for (Slot s : slots) {
            int h = 22;
            boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
            if (hov) hovered = s.index();
            ChatUi.glass(g, ChatGlass.Surface.CARD, x, y, w, h, hov);
            CobblemonIcon.draw(g, s.species(), s.shiny(), x + 2, y + 1, 20, partial);
            ChatUi.label(g, font, ChatUi.truncate(font, s.label() + (s.shiny() ? " ★" : ""), w - 30, ChatLayout.TEXT_SCALE),
                    x + 25, y + h / 2f, ChatLayout.TEXT_SCALE, s.shiny() ? 0xFFC8820A : FontPalette.body());
            y += h + 3;
        }
        y += 2;
        for (var line : font.split(Component.translatable("mcphone.chat.pick_pokemon_note"), ChatLayout.unscaledWidth(w, ChatLayout.META_SCALE))) {
            ChatUi.text(g, font, line, x, y, ChatLayout.META_SCALE, FontPalette.dim()); y += 7;
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && hovered >= 0) { selected = hovered; return true; }
        return false;
    }
}
