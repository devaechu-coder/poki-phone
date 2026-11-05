package com.november.mcphone.feature.chat.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.GuiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 포키 옷장 — 치장(PokiCosmetics) 미리보기 전용. 얻는 곳·보유 여부는 안 보여 준다.
 * 목록은 PokiCosmetics 크리에이티브 탭(pokicos:cosmetics)에서 읽어서, 치장이 늘면 따라 늘어난다.
 * 지금은 OP 만 보인다 ({@link com.november.mcphone.feature.wardrobe.client.WardrobeApp}).
 */
public final class WardrobePage {
    /** 탭 순서 = 아이템 id (pokicos:<id>) */
    private static final String[] CATS = {"hat", "face", "back", "tail", "outfit", "lightstick"};
    /** 목록에서 뺄 한정템: "<id>:<번호>" (테스터 새싹, 포획 원정 모자, 관리자 제복) */
    private static final Set<String> HIDDEN = Set.of("hat:26", "hat:27", "outfit:3");

    private static final List<List<ItemStack>> ITEMS = new ArrayList<>();
    private static int cat;
    private static int detail = -1;
    private static double scroll;
    private static int contentH, viewH;
    private static float angle;
    private static long lastFrame, lastDrag;
    private record Hit(ChatLayout.Rect r, Runnable run) {}
    private static final List<Hit> HITS = new ArrayList<>();

    private static String tr(String k) { return Component.translatable("mcphone.wardrobe." + k).getString(); }

    public static void open() {
        detail = -1; scroll = 0; angle = 0;
        load();
    }

    public static void close() { HITS.clear(); detail = -1; }

    private static void load() {
        ITEMS.clear();
        for (int i = 0; i < CATS.length; i++) ITEMS.add(new ArrayList<>());
        try {
            Minecraft mc = Minecraft.getInstance();
            CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(ResourceLocation.fromNamespaceAndPath("pokicos", "cosmetics"));
            if (tab == null || mc.player == null || mc.level == null) return;
            if (tab.getDisplayItems().isEmpty())
                CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), mc.player.canUseGameMasterBlocks(), mc.level.registryAccess());
            for (ItemStack s : tab.getDisplayItems()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
                if (!"pokicos".equals(id.getNamespace())) continue;
                int c = indexOf(id.getPath());
                if (c < 0) continue;
                var cmd = s.get(DataComponents.CUSTOM_MODEL_DATA);
                if (cmd != null && HIDDEN.contains(id.getPath() + ":" + cmd.value())) continue;
                ITEMS.get(c).add(s.copy());
            }
        } catch (RuntimeException e) {
            com.november.mcphone.MCphone.LOGGER.warn("[포키 옷장] 치장 목록을 못 읽음", e);
        }
    }

    private static int indexOf(String path) {
        for (int i = 0; i < CATS.length; i++) if (CATS[i].equals(path)) return i;
        return -1;
    }

    private static List<ItemStack> current() { return ITEMS.isEmpty() ? List.of() : ITEMS.get(cat); }

    /** 뒤로: 상세면 목록으로. false 면 홈으로 */
    public static boolean back() {
        if (detail >= 0) { detail = -1; return true; }
        return false;
    }

    public static void drag(double dx) {
        if (detail < 0) return;
        angle += (float) dx * 2.5f;
        lastDrag = System.currentTimeMillis();
    }

    public static void render(GuiGraphics g, int left, int top, int sw, int sh, int statusH, int navH, int mx, int my, Font font) {
        HITS.clear();
        int x = left + 4, w = sw - 8, y = top + statusH + ChatLayout.TOP_GAP;
        int bottom = top + sh - navH - 2;
        List<ItemStack> items = current();
        boolean inDetail = detail >= 0 && detail < items.size();

        if (inDetail) {
            var back = new ChatLayout.Rect(x, y, 12, ChatLayout.HEADER_HEIGHT);
            ChatUi.sizedIcon(g, ChatUi.Icon.BACK, x + 2.5f, y + 2.5f, 7, back.contains(mx, my) ? ChatUi.accent() : FontPalette.body());
            HITS.add(new Hit(back, WardrobePage::back));
        }
        String title = inDetail ? ChatUi.truncate(font, items.get(detail).getHoverName().getString(), w - 28, ChatLayout.TEXT_SCALE) : tr("title");
        ChatUi.buttonText(g, font, title, x + w / 2f, y + ChatLayout.HEADER_HEIGHT / 2f, ChatLayout.TEXT_SCALE, FontPalette.title());
        y += ChatLayout.HEADER_HEIGHT + 3;

        if (inDetail) { detail(g, font, items, x, y, w, bottom, mx, my); return; }

        // 탭
        int tw = w / CATS.length;
        for (int i = 0; i < CATS.length; i++) {
            int tx = x + i * tw;
            var r = new ChatLayout.Rect(tx, y, tw - 1, 10);
            boolean on = i == cat;
            if (on) ChatUi.roundedFill(g, tx, y, tw - 1, 10, ChatUi.accent());
            else ChatUi.glass(g, ChatGlass.Surface.CARD, tx, y, tw - 1, 10, r.contains(mx, my));
            ChatUi.buttonText(g, font, tr("cat." + CATS[i]), tx + (tw - 1) / 2f, y + 5, ChatLayout.META_SCALE, on ? 0xFFFFFFFF : FontPalette.body());
            int idx = i;
            HITS.add(new Hit(r, () -> { cat = idx; scroll = 0; }));
        }
        y += 13;
        ChatUi.label(g, font, Component.translatable("mcphone.wardrobe.count", tr("cat." + CATS[cat]), items.size()).getString(),
                x + 2, y + 3, ChatLayout.META_SCALE, FontPalette.subtle());
        y += 8;

        if (items.isEmpty()) {
            ChatUi.label(g, font, tr("empty"), x + 4, y + 10, ChatLayout.META_SCALE, FontPalette.subtle());
            return;
        }
        viewH = bottom - y;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - viewH)));
        int cols = 3, gap = 2, cw = (w - gap * (cols - 1)) / cols, ch = 30;
        int listTop = y;
        GuiUtil.enableScissor(g, x, listTop, x + w, bottom);
        try {
            int base = (int) (listTop - scroll);
            for (int i = 0; i < items.size(); i++) {
                int cx = x + (i % cols) * (cw + gap), cy = base + (i / cols) * (ch + gap);
                if (cy + ch < listTop || cy > bottom) continue;
                var r = new ChatLayout.Rect(cx, cy, cw, ch);
                boolean inside = my >= listTop && my < bottom;
                ChatUi.glass(g, ChatGlass.Surface.CARD, cx, cy, cw, ch, inside && r.contains(mx, my));
                GuiUtil.drawItemIcon(g, items.get(i), cx + (cw - 16) / 2, cy + 3, 16);
                String name = ChatUi.truncate(font, items.get(i).getHoverName().getString(), cw - 3, ChatLayout.META_SCALE);
                ChatUi.buttonText(g, font, name, cx + cw / 2f, cy + 24, ChatLayout.META_SCALE, FontPalette.body());
                if (inside) { int idx = i; HITS.add(new Hit(r, () -> { detail = idx; angle = 0; })); }
            }
            contentH = ((items.size() + cols - 1) / cols) * (ch + gap);
        } finally { GuiUtil.disableScissor(g); }
    }

    private static void detail(GuiGraphics g, Font font, List<ItemStack> items, int x, int y, int w, int bottom, int mx, int my) {
        int boxH = Math.max(40, Math.min(w, bottom - y - 34));
        ChatUi.glass(g, ChatGlass.Surface.CARD, x, y, w, boxH, false);

        long now = System.currentTimeMillis();
        if (lastFrame != 0 && now - lastDrag > 1500) angle += (now - lastFrame) * 0.045f;
        lastFrame = now;
        GuiUtil.enableScissor(g, x, y, x + w, y + boxH);
        try { spin(g, items.get(detail), x + w / 2f, y + boxH / 2f, boxH * 0.72f); }
        finally { GuiUtil.disableScissor(g); }
        ChatUi.buttonText(g, font, tr("drag"), x + w / 2f, y + boxH - 5, ChatLayout.META_SCALE, FontPalette.subtle());
        y += boxH + 4;

        ChatUi.buttonText(g, font, tr("note"), x + w / 2f, y + 4, ChatLayout.META_SCALE, FontPalette.subtle());
        y += 11;

        // ◀ 2 / 20 ▶
        int n = items.size();
        ChatUi.buttonText(g, font, (detail + 1) + " / " + n, x + w / 2f, y + 6, ChatLayout.TEXT_SCALE, FontPalette.body());
        var prev = new ChatLayout.Rect(x, y, 20, 12);
        var next = new ChatLayout.Rect(x + w - 20, y, 20, 12);
        ChatUi.buttonText(g, font, "◀", x + 10, y + 6, ChatLayout.TEXT_SCALE, prev.contains(mx, my) ? ChatUi.accent() : FontPalette.body());
        ChatUi.buttonText(g, font, "▶", x + w - 10, y + 6, ChatLayout.TEXT_SCALE, next.contains(mx, my) ? ChatUi.accent() : FontPalette.body());
        HITS.add(new Hit(prev, () -> { detail = (detail - 1 + n) % n; angle = 0; }));
        HITS.add(new Hit(next, () -> { detail = (detail + 1) % n; angle = 0; }));
    }

    /** 아이템 모델을 크게 빙글빙글. GuiGraphics.renderItem 과 같은 순서에 Y 회전만 더함 */
    private static void spin(GuiGraphics g, ItemStack stack, float cx, float cy, float size) {
        if (!GuiUtil.canDrawItemIcon(stack)) return;
        Minecraft mc = Minecraft.getInstance();
        var model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
        var pose = g.pose();
        pose.pushPose();
        try {
            pose.translate(cx, cy, 150);
            pose.scale(size, -size, size);
            pose.mulPose(Axis.YP.rotationDegrees(angle));
            boolean flat = !model.usesBlockLight();
            if (flat) Lighting.setupForFlatItems();
            mc.getItemRenderer().render(stack, ItemDisplayContext.GUI, false, pose, g.bufferSource(), 0xF000F0, OverlayTexture.NO_OVERLAY, model);
            g.flush();
            if (flat) Lighting.setupFor3DItems();
        } finally {
            pose.popPose();
            // 큰 3D 모델이 남긴 깊이 때문에 뒤에 그리는 글자·외곽이 가려지지 않게
            RenderSystem.clear(256, Minecraft.ON_OSX);
        }
    }

    public static boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;
        for (int i = HITS.size() - 1; i >= 0; i--) if (HITS.get(i).r().contains(mx, my)) { HITS.get(i).run().run(); return true; }
        return true;
    }

    public static boolean mouseScrolled(double amount) {
        if (detail >= 0) return true;
        scroll -= amount * 14;
        if (scroll < 0) scroll = 0;
        return true;
    }
}
