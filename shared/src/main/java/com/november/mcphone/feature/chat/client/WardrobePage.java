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
    /** true 면 내 캐릭터에 씌워서 보기 (응원봉은 아이템만) */
    private static boolean tryOn = true;
    private static long lastFrame, lastDrag;
    private record Hit(ChatLayout.Rect r, Runnable run) {}
    private static final List<Hit> HITS = new ArrayList<>();

    private static String tr(String k) { return Component.translatable("mcphone.wardrobe." + k).getString(); }

    public static void open() {
        detail = -1; scroll = 0; angle = 0; FITS.clear();
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
        ItemStack stack = items.get(detail);
        String slot = slotOf(stack);
        boolean canTry = slot != null;
        // 아이템만 / 입어보기
        if (canTry) {
            int hw = w / 2;
            var a = new ChatLayout.Rect(x, y, hw - 1, 11);
            var b = new ChatLayout.Rect(x + hw, y, w - hw, 11);
            seg(g, font, a, tr("item_only"), !tryOn, mx, my);
            seg(g, font, b, tr("try_on"), tryOn, mx, my);
            HITS.add(new Hit(a, () -> tryOn = false));
            HITS.add(new Hit(b, () -> tryOn = true));
            y += 14;
        }
        int boxH = Math.max(40, Math.min(w + 10, bottom - y - 34));
        ChatUi.glass(g, ChatGlass.Surface.CARD, x, y, w, boxH, false);

        long now = System.currentTimeMillis();
        if (lastFrame != 0 && now - lastDrag > 1500) angle += (now - lastFrame) * 0.045f;
        lastFrame = now;
        boolean wearing = canTry && tryOn;
        GuiUtil.enableScissor(g, x, y, x + w, y + boxH);
        try {
            if (wearing) wear(g, stack, slot, x + w / 2f, y + boxH * 0.52f, boxH * 0.36f);
            else spin(g, stack, x + w / 2f, y + (boxH - 10) / 2f, w, boxH - 10);
        } finally { GuiUtil.disableScissor(g); }
        if (wearing) ChatUi.label(g, font, tr("fitting"), x + 4, y + 6, ChatLayout.META_SCALE, ChatUi.accent());
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

    private static void seg(GuiGraphics g, Font font, ChatLayout.Rect r, String text, boolean on, int mx, int my) {
        if (on) ChatUi.roundedFill(g, r.x(), r.y(), r.width(), r.height(), ChatUi.accent());
        else ChatUi.glass(g, ChatGlass.Surface.CARD, r.x(), r.y(), r.width(), r.height(), r.contains(mx, my));
        ChatUi.buttonText(g, font, text, r.x() + r.width() / 2f, r.y() + r.height() / 2f, ChatLayout.META_SCALE, on ? 0xFFFFFFFF : FontPalette.body());
    }

    /** 이 치장이 들어가는 Accessories 칸 이름 (응원봉 같은 손 아이템은 null) */
    private static String slotOf(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return switch (path) { case "hat", "face", "back", "tail", "outfit" -> path; default -> null; };
    }

    /**
     * 내 캐릭터를 그리면서 그 칸의 "치장" 자리에 이 아이템을 잠깐 넣었다가 바로 뺀다.
     * 클라이언트 화면에만 그리는 거라 서버·다른 사람한테는 아무 일 없음.
     * Accessories 는 컴파일 의존이 없어서 이름으로 찾아 부름.
     */
    private static void wear(GuiGraphics g, ItemStack stack, String slot, float cx, float cy, float scale) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;
        net.minecraft.world.Container box = cosmeticBox(player, slot);
        ItemStack old = null;
        float bodyRot = player.yBodyRot, yRot = player.getYRot(), xRot = player.getXRot(), headO = player.yHeadRotO, head = player.yHeadRot;
        try {
            if (box != null && box.getContainerSize() > 0) { old = box.getItem(0); box.setItem(0, stack.copy()); }
            float face = 180f + angle;
            player.yBodyRot = face; player.setYRot(face); player.setXRot(0); player.yHeadRot = face; player.yHeadRotO = face;
            var pose = new org.joml.Quaternionf().rotateZ((float) Math.PI);
            var cam = new org.joml.Quaternionf().rotateX((float) Math.toRadians(-8));
            pose.mul(cam);
            float s = player.getScale();
            var off = new org.joml.Vector3f(0, player.getBbHeight() / 2f + 0.0625f * s, 0);
            net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(g, cx, cy, scale / s, off, pose, cam, player);
        } catch (RuntimeException e) {
            com.november.mcphone.MCphone.LOGGER.debug("[포키 옷장] 입어보기 그리기 실패", e);
        } finally {
            if (old != null) box.setItem(0, old);
            player.yBodyRot = bodyRot; player.setYRot(yRot); player.setXRot(xRot); player.yHeadRotO = headO; player.yHeadRot = head;
            RenderSystem.clear(256, Minecraft.ON_OSX);
        }
    }

    private static net.minecraft.world.Container cosmeticBox(net.minecraft.world.entity.LivingEntity e, String slot) {
        try {
            Class<?> capC = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
            Class<?> conC = Class.forName("io.wispforest.accessories.api.AccessoriesContainer");
            Object opt = capC.getMethod("getOptionally", net.minecraft.world.entity.LivingEntity.class).invoke(null, e);
            Object cap = ((java.util.Optional<?>) opt).orElse(null);
            if (cap == null) return null;
            Object con = ((java.util.Map<?, ?>) capC.getMethod("getContainers").invoke(cap)).get(slot);
            if (con == null) return null;
            Object cos = conC.getMethod("getCosmeticAccessories").invoke(con);
            return cos instanceof net.minecraft.world.Container c ? c : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return null;
        }
    }

    /** 아이템 모델을 크게 빙글빙글. GuiGraphics.renderItem 과 같은 순서에 Y 회전만 더함 */
    /** 모델 크기·중심 (GUI 변환 뒤 공간). 모델마다 한 번만 셈 */
    private record Fit(float cx, float cy, float cz, float rxz, float hy) {}
    private static final java.util.Map<Object, Fit> FITS = new java.util.IdentityHashMap<>();

    private static Fit fitOf(net.minecraft.client.resources.model.BakedModel model) {
        return FITS.computeIfAbsent(model, m -> {
            float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            var rnd = net.minecraft.util.RandomSource.create(42);
            java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads = new ArrayList<>(m.getQuads(null, null, rnd));
            for (var d : net.minecraft.core.Direction.values()) quads.addAll(m.getQuads(null, d, rnd));
            var t = m.getTransforms().getTransform(ItemDisplayContext.GUI);
            var mat = new org.joml.Matrix4f().translate(t.translation)
                    .rotate(new org.joml.Quaternionf().rotationXYZ((float) Math.toRadians(t.rotation.x()), (float) Math.toRadians(t.rotation.y()), (float) Math.toRadians(t.rotation.z())))
                    .scale(t.scale).translate(-0.5f, -0.5f, -0.5f);
            var v = new org.joml.Vector3f();
            for (var q : quads) {
                int[] vs = q.getVertices();
                int st = vs.length / 4;
                for (int i = 0; i + 2 < vs.length; i += st) {
                    v.set(Float.intBitsToFloat(vs[i]), Float.intBitsToFloat(vs[i + 1]), Float.intBitsToFloat(vs[i + 2]));
                    mat.transformPosition(v);
                    lo[0] = Math.min(lo[0], v.x); lo[1] = Math.min(lo[1], v.y); lo[2] = Math.min(lo[2], v.z);
                    hi[0] = Math.max(hi[0], v.x); hi[1] = Math.max(hi[1], v.y); hi[2] = Math.max(hi[2], v.z);
                }
            }
            if (lo[0] > hi[0]) return new Fit(0, 0, 0, 0.5f, 0.5f);
            float cx = (lo[0] + hi[0]) / 2, cy = (lo[1] + hi[1]) / 2, cz = (lo[2] + hi[2]) / 2;
            float hx = (hi[0] - lo[0]) / 2, hz = (hi[2] - lo[2]) / 2;
            return new Fit(cx, cy, cz, Math.max(0.05f, (float) Math.sqrt(hx * hx + hz * hz)), Math.max(0.05f, (hi[1] - lo[1]) / 2));
        });
    }

    /**
     * 아이템 모델을 크게 빙글빙글. GuiGraphics.renderItem 과 같은 순서에 Y 회전만 더함.
     * 작은 장식(볼 패치 등)은 모델 한가운데가 아니라 한쪽에 붙어 있어서, 실제 모양의 중심을 축으로 돌리고
     * 크기도 상자에 맞게 키운다.
     */
    private static void spin(GuiGraphics g, ItemStack stack, float cx, float cy, int boxW, int boxH) {
        if (!GuiUtil.canDrawItemIcon(stack)) return;
        Minecraft mc = Minecraft.getInstance();
        var model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
        Fit f;
        try { f = fitOf(model); } catch (RuntimeException e) { f = new Fit(0, 0, 0, 0.5f, 0.5f); }
        float size = Math.min(boxW * 0.42f / f.rxz(), boxH * 0.42f / f.hy());
        size = Math.min(size, boxH * 1.6f);
        var pose = g.pose();
        pose.pushPose();
        try {
            pose.translate(cx, cy, 150);
            pose.scale(size, -size, size);
            pose.mulPose(Axis.YP.rotationDegrees(angle));
            pose.translate(-f.cx(), -f.cy(), -f.cz());
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
