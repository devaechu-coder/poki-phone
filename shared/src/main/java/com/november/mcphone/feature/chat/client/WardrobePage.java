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

import com.november.mcphone.feature.wardrobe.net.WardrobeRequestPacket;
import com.november.mcphone.feature.wardrobe.net.WardrobeWire;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 포키 옷장 — 치장(PokiCosmetics) 미리보기 + 옷장 귀속 입기.
 * 목록은 PokiCosmetics 크리에이티브 탭(pokicos:cosmetics)에서 읽어서, 치장이 늘면 따라 늘어난다.
 * 이름이 "포키 모자 (하늘)" 처럼 "이름 (색)" 이면 한 칸으로 묶고, 상세에서 색을 고른다.
 * 가진 치장(옷장)은 서버 PokiCosmetics 가 저장하고, 여기서 입기·벗기 요청을 보낸다 ({@link WardrobeWire}).
 */
public final class WardrobePage {
    /** 탭 순서 = 아이템 id (pokicos:<id>) */
    private static final String[] CATS = {"hat", "face", "back", "tail", "outfit", "lightstick"};
    /** 전체 목록에서 뺄 한정템: "<id>:<번호>" (테스터 새싹, 포획 원정 모자, 관리자 제복 1·2) — 가지고 있으면 내 치장에는 나옴 */
    private static final Set<String> HIDDEN = Set.of("hat:26", "hat:27", "outfit:3", "outfit:5");
    /** OP 한테만 보이는 것 (아직 공개 전): 머리 위 포키냥 */
    private static final Set<String> OP_ONLY = Set.of("hat:28");
    private static final String[] WEAR = {"hat", "face", "back", "tail", "outfit"};
    private static final Pattern VARIANT = Pattern.compile("^(.+) \\((.+)\\)$");

    /** 한 디자인 (색 묶음) */
    private record Family(String name, List<ItemStack> variants) {}

    private static final List<List<Family>> FAMS = new ArrayList<>();
    /** 키 → 아이템 (숨김 포함) */
    private static final Map<String, ItemStack> ALL = new LinkedHashMap<>();
    private static int cat;
    private static int detail = -1, variant;
    private static double scroll;
    private static int contentH, viewH;
    private static float angle;
    /** true 면 내 캐릭터에 씌워서 보기 (응원봉은 아이템만) */
    private static boolean tryOn = true;
    /** true 면 '내 치장' (옷장에 있는 것 · 입은 것) */
    private static boolean mine;
    private static long lastFrame, lastDrag;
    private record Hit(ChatLayout.Rect r, Runnable run) {}
    private static final List<Hit> HITS = new ArrayList<>();

    // 서버 옷장
    /** null = 아직 모름 (불러오는 중 / 서버에 옷장 기능 없음) */
    private static Set<String> owned;
    private static boolean noMod;
    private static long pendingAt;
    private static String toast = "";
    private static long toastAt;

    private static String tr(String k) { return Component.translatable("mcphone.wardrobe." + k).getString(); }

    public static void open() {
        detail = -1; scroll = 0; angle = 0; FITS.clear();
        load();
        request(WardrobeWire.Kind.LIST, "", 0);
    }

    public static void close() { HITS.clear(); detail = -1; }

    private static void request(WardrobeWire.Kind kind, String slot, int cmd) {
        long now = System.currentTimeMillis();
        if (kind != WardrobeWire.Kind.LIST && now - pendingAt < 300) return;
        pendingAt = now;
        try { com.november.mcphone.core.net.MCphoneNetwork.sendToServer(new WardrobeRequestPacket(WardrobeWire.Query.of(kind, slot, cmd))); }
        catch (RuntimeException e) { noMod = true; }
    }

    /** 서버 답 (네트워크 스레드 → 클라 메인에서 불림) */
    public static void accept(WardrobeWire.Reply r) {
        pendingAt = 0;
        switch (r.status()) {
            case OK -> { owned = new HashSet<>(r.owned()); noMod = false; }
            case ERROR -> { if (!r.owned().isEmpty() || owned == null) owned = new HashSet<>(r.owned()); say(r.message().isEmpty() ? tr("fail") : r.message()); }
            case NO_MOD -> noMod = true;
            default -> { }
        }
    }

    private static void say(String s) { toast = s; toastAt = System.currentTimeMillis(); }

    private static String keyOf(ItemStack s) {
        var cmd = s.get(DataComponents.CUSTOM_MODEL_DATA);
        return BuiltInRegistries.ITEM.getKey(s.getItem()).getPath() + ":" + (cmd == null ? 0 : cmd.value());
    }

    private static boolean has(ItemStack s) { return owned != null && owned.contains(keyOf(s)); }

    private static void load() {
        FAMS.clear(); ALL.clear();
        for (int i = 0; i < CATS.length; i++) FAMS.add(new ArrayList<>());
        try {
            Minecraft mc = Minecraft.getInstance();
            CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(ResourceLocation.fromNamespaceAndPath("pokicos", "cosmetics"));
            if (tab == null || mc.player == null || mc.level == null) return;
            if (tab.getDisplayItems().isEmpty())
                CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), mc.player.canUseGameMasterBlocks(), mc.level.registryAccess());
            List<Map<String, Family>> byName = new ArrayList<>();
            for (int i = 0; i < CATS.length; i++) byName.add(new LinkedHashMap<>());
            for (ItemStack s : tab.getDisplayItems()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
                if (!"pokicos".equals(id.getNamespace())) continue;
                int c = indexOf(id.getPath());
                if (c < 0) continue;
                String key = keyOf(s);
                ALL.put(key, s.copy());
                if (HIDDEN.contains(key)) continue;
                if (OP_ONLY.contains(key) && !mc.player.hasPermissions(2)) continue;
                String name = s.getHoverName().getString();
                Matcher m = VARIANT.matcher(name);
                String fam = m.matches() ? m.group(1) : name + "\u0000" + key; // 묶음이 아니면 혼자
                byName.get(c).computeIfAbsent(fam, k -> new Family(m.matches() ? m.group(1) : name, new ArrayList<>())).variants().add(s.copy());
            }
            for (int i = 0; i < CATS.length; i++) FAMS.get(i).addAll(byName.get(i).values());
        } catch (RuntimeException e) {
            com.november.mcphone.MCphone.LOGGER.warn("[포키 옷장] 치장 목록을 못 읽음", e);
        }
    }

    private static int indexOf(String path) {
        for (int i = 0; i < CATS.length; i++) if (CATS[i].equals(path)) return i;
        return -1;
    }

    private static List<Family> current() { return FAMS.isEmpty() ? List.of() : FAMS.get(cat); }

    /** 묶음에서 처음 보여줄 색: 입고 있는 것 → 가진 것 → 첫 번째 */
    private static int pick(Family f) {
        for (int i = 0; i < f.variants().size(); i++) if (worn(f.variants().get(i))) return i;
        for (int i = 0; i < f.variants().size(); i++) if (has(f.variants().get(i))) return i;
        return 0;
    }

    private static String variantName(ItemStack s) {
        Matcher m = VARIANT.matcher(s.getHoverName().getString());
        return m.matches() ? m.group(2) : s.getHoverName().getString();
    }

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
        List<Family> fams = current();
        boolean inDetail = detail >= 0 && detail < fams.size();

        if (inDetail) {
            var back = new ChatLayout.Rect(x, y, 12, ChatLayout.HEADER_HEIGHT);
            ChatUi.sizedIcon(g, ChatUi.Icon.BACK, x + 2.5f, y + 2.5f, 7, back.contains(mx, my) ? ChatUi.accent() : FontPalette.body());
            HITS.add(new Hit(back, WardrobePage::back));
        }
        String title = inDetail ? ChatUi.truncate(font, fams.get(detail).name(), w - 28, ChatLayout.TEXT_SCALE) : tr("title");
        ChatUi.buttonText(g, font, title, x + w / 2f, y + ChatLayout.HEADER_HEIGHT / 2f, ChatLayout.TEXT_SCALE, FontPalette.title());
        y += ChatLayout.HEADER_HEIGHT + 3;

        if (inDetail) detail(g, font, fams, x, y, w, bottom, mx, my);
        else {
            // 전체 / 내 치장
            int hw = w / 2;
            var a = new ChatLayout.Rect(x, y, hw - 1, 11);
            var b = new ChatLayout.Rect(x + hw, y, w - hw, 11);
            seg(g, font, a, tr("all"), !mine, mx, my);
            seg(g, font, b, tr("mine"), mine, mx, my);
            HITS.add(new Hit(a, () -> { mine = false; scroll = 0; }));
            HITS.add(new Hit(b, () -> { mine = true; scroll = 0; }));
            y += 14;
            if (mine) mineList(g, font, x, y, w, bottom, mx, my);
            else grid(g, font, fams, x, y, w, bottom, mx, my);
        }

        // 알림 (입기 실패 등)
        if (!toast.isEmpty() && System.currentTimeMillis() - toastAt < 2500) {
            int tw = Math.min(w, (int) ChatUi.width(font, toast, ChatLayout.META_SCALE) + 10);
            int ty = bottom - 14;
            ChatUi.roundedFill(g, x + (w - tw) / 2, ty, tw, 12, 0xE0302838);
            ChatUi.buttonText(g, font, toast, x + w / 2f, ty + 6, ChatLayout.META_SCALE, 0xFFFFFFFF);
        }
    }

    private static void grid(GuiGraphics g, Font font, List<Family> fams, int x, int y, int w, int bottom, int mx, int my) {
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
        int total = 0, have = 0;
        for (Family f : fams) for (ItemStack s : f.variants()) { total++; if (has(s)) have++; }
        boolean ownable = slotOf(fams.isEmpty() ? ItemStack.EMPTY : fams.get(0).variants().get(0)) != null;
        String count = Component.translatable("mcphone.wardrobe.count", tr("cat." + CATS[cat]), total).getString();
        if (ownable && owned != null) count += " · " + Component.translatable("mcphone.wardrobe.have", have).getString();
        ChatUi.label(g, font, count, x + 2, y + 3, ChatLayout.META_SCALE, FontPalette.subtle());
        y += 8;

        if (fams.isEmpty()) {
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
            for (int i = 0; i < fams.size(); i++) {
                int cx = x + (i % cols) * (cw + gap), cy = base + (i / cols) * (ch + gap);
                if (cy + ch < listTop || cy > bottom) continue;
                Family f = fams.get(i);
                ItemStack show = f.variants().get(pick(f));
                boolean any = false;
                for (ItemStack s : f.variants()) any |= has(s);
                var r = new ChatLayout.Rect(cx, cy, cw, ch);
                boolean inside = my >= listTop && my < bottom;
                ChatUi.glass(g, ChatGlass.Surface.CARD, cx, cy, cw, ch, inside && r.contains(mx, my));
                GuiUtil.drawItemIcon(g, show, cx + (cw - 16) / 2, cy + 3, 16);
                boolean locked = ownable && owned != null && !any;
                if (locked) {
                    g.fill(cx + 1, cy + 1, cx + cw - 1, cy + ch - 1, 0x66101018);
                    ChatUi.label(g, font, tr("locked"), cx + 2, cy + 4, 0.5f, 0xFFD0C8E0);
                }
                if (f.variants().size() > 1) {
                    String n = Component.translatable("mcphone.wardrobe.colors", f.variants().size()).getString();
                    ChatUi.label(g, font, n, cx + cw - 2 - ChatUi.width(font, n, 0.5f), cy + 4, 0.5f, ChatUi.accent());
                }
                String name = ChatUi.truncate(font, f.name(), cw - 3, ChatLayout.META_SCALE);
                ChatUi.buttonText(g, font, name, cx + cw / 2f, cy + 24, ChatLayout.META_SCALE, locked ? FontPalette.subtle() : FontPalette.body());
                if (inside) { int idx = i; HITS.add(new Hit(r, () -> { detail = idx; variant = pick(fams.get(idx)); angle = 0; })); }
            }
            contentH = ((fams.size() + cols - 1) / cols) * (ch + gap);
        } finally { GuiUtil.disableScissor(g); }
    }

    /** 내 치장: 지금 입은 것 + 옷장에 있는 것. 입기·벗기는 서버 옷장으로 */
    private static void mineList(GuiGraphics g, Font font, int x, int y, int w, int bottom, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;
        if (owned == null) {
            ChatUi.label(g, font, tr(noMod ? "no_mod" : "loading"), x + 4, y + 8, ChatLayout.META_SCALE, FontPalette.subtle());
            return;
        }
        record Row(String header, ItemStack stack, String label, Runnable act, String actText) {}
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(tr("worn"), null, null, null, null));
        int worn = 0;
        for (String slot : WEAR) {
            ItemStack st = wornStack(slot);
            if (st.isEmpty()) continue;
            worn++;
            rows.add(new Row(null, st, tr("cat." + slot), () -> request(WardrobeWire.Kind.OFF, slot, 0), tr("take_off")));
        }
        if (worn == 0) rows.add(new Row(null, null, tr("worn_none"), null, null));
        rows.add(new Row(Component.translatable("mcphone.wardrobe.closet", owned.size()).getString(), null, null, null, null));
        int n = 0;
        for (String c : WEAR) {
            for (var e : ALL.entrySet()) {
                if (!e.getKey().startsWith(c + ":") || !owned.contains(e.getKey())) continue;
                ItemStack st = e.getValue();
                if (worn(st)) continue;
                n++;
                int cmd = Integer.parseInt(e.getKey().substring(c.length() + 1));
                rows.add(new Row(null, st, tr("cat." + c), () -> request(WardrobeWire.Kind.WEAR, c, cmd), tr("put_on")));
            }
        }
        if (n == 0) rows.add(new Row(null, null, tr(owned.isEmpty() ? "closet_empty" : "closet_all_worn"), null, null));
        int bag = 0;
        for (ItemStack st : player.getInventory().items) if (!st.isEmpty() && slotOf(st) != null && "pokicos".equals(BuiltInRegistries.ITEM.getKey(st.getItem()).getNamespace())) bag++;
        if (bag > 0) rows.add(new Row(null, null, Component.translatable("mcphone.wardrobe.bag_hint", bag).getString(), null, null));

        int rowH = 20, headH = 11;
        int total = 0;
        for (Row r : rows) total += r.header() != null ? headH : (r.stack() == null ? 12 : rowH + 2);
        viewH = bottom - y; contentH = total;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - viewH)));
        GuiUtil.enableScissor(g, x, y, x + w, bottom);
        try {
            int cy = (int) (y - scroll);
            for (Row r : rows) {
                if (r.header() != null) {
                    ChatUi.label(g, font, r.header(), x + 2, cy + 6, ChatLayout.META_SCALE, ChatUi.accent());
                    cy += headH; continue;
                }
                if (r.stack() == null) {
                    ChatUi.label(g, font, ChatUi.truncate(font, r.label(), w - 8, ChatLayout.META_SCALE), x + 4, cy + 5, ChatLayout.META_SCALE, FontPalette.subtle());
                    cy += 12; continue;
                }
                if (cy + rowH >= y && cy <= bottom) {
                    ChatUi.glass(g, ChatGlass.Surface.CARD, x, cy, w, rowH, false);
                    GuiUtil.drawItemIcon(g, r.stack(), x + 2, cy + 2, 16);
                    int bw = 22;
                    String name = ChatUi.truncate(font, r.stack().getHoverName().getString(), w - 24 - bw - 4, ChatLayout.META_SCALE);
                    ChatUi.label(g, font, name, x + 21, cy + 7, ChatLayout.META_SCALE, FontPalette.body());
                    ChatUi.label(g, font, r.label(), x + 21, cy + 14, ChatLayout.META_SCALE, FontPalette.subtle());
                    var br = new ChatLayout.Rect(x + w - bw - 2, cy + 4, bw, 12);
                    boolean in = my >= y && my < bottom && br.contains(mx, my);
                    ChatUi.roundedFill(g, br.x(), br.y(), br.width(), br.height(), in ? ChatUi.accent() : 0xFF6A5AA8);
                    ChatUi.buttonText(g, font, r.actText(), br.x() + br.width() / 2f, br.y() + br.height() / 2f, ChatLayout.META_SCALE, 0xFFFFFFFF);
                    if (my >= y && my < bottom) HITS.add(new Hit(br, r.act()));
                }
                cy += rowH + 2;
            }
        } finally { GuiUtil.disableScissor(g); }
    }

    /** 지금 그 칸에 입은 치장 (Accessories 본 칸) */
    private static ItemStack wornStack(String slot) {
        var p = Minecraft.getInstance().player;
        if (p == null) return ItemStack.EMPTY;
        net.minecraft.world.Container box = wornBox(p, slot);
        return box != null && box.getContainerSize() > 0 ? box.getItem(0) : ItemStack.EMPTY;
    }

    private static boolean worn(ItemStack s) {
        String slot = slotOf(s);
        if (slot == null) return false;
        ItemStack w = wornStack(slot);
        return !w.isEmpty() && keyOf(w).equals(keyOf(s));
    }

    private static net.minecraft.world.Container wornBox(net.minecraft.world.entity.LivingEntity e, String slot) {
        try {
            Class<?> capC = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
            Class<?> conC = Class.forName("io.wispforest.accessories.api.AccessoriesContainer");
            Object opt = capC.getMethod("getOptionally", net.minecraft.world.entity.LivingEntity.class).invoke(null, e);
            Object cap = ((java.util.Optional<?>) opt).orElse(null);
            if (cap == null) return null;
            Object con = ((java.util.Map<?, ?>) capC.getMethod("getContainers").invoke(cap)).get(slot);
            if (con == null) return null;
            Object acc = conC.getMethod("getAccessories").invoke(con);
            return acc instanceof net.minecraft.world.Container c ? c : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return null;
        }
    }

    private static void detail(GuiGraphics g, Font font, List<Family> fams, int x, int y, int w, int bottom, int mx, int my) {
        Family fam = fams.get(detail);
        if (variant < 0 || variant >= fam.variants().size()) variant = 0;
        ItemStack stack = fam.variants().get(variant);
        String slot = slotOf(stack);
        boolean canTry = slot != null;
        boolean many = fam.variants().size() > 1;
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
        int below = (many ? 30 : 12) + 14 + 14;
        int boxH = Math.max(40, Math.min(w + 10, bottom - y - below));
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
        y += boxH + 3;

        // 색 고르기
        if (many) {
            ChatUi.buttonText(g, font, variantName(stack), x + w / 2f, y + 4, ChatLayout.META_SCALE, FontPalette.body());
            y += 9;
            int n = fam.variants().size(), sz = 15, gap = 2;
            int per = Math.max(1, (w + gap) / (sz + gap));
            int rows = (n + per - 1) / per;
            int rowY = y;
            for (int i = 0; i < n; i++) {
                int inRow = Math.min(per, n - (i / per) * per);
                int rx = x + (w - (inRow * (sz + gap) - gap)) / 2 + (i % per) * (sz + gap);
                int ry = rowY + (i / per) * (sz + gap);
                var r = new ChatLayout.Rect(rx, ry, sz, sz);
                ItemStack v = fam.variants().get(i);
                boolean sel = i == variant;
                if (sel) ChatUi.roundedFill(g, rx - 1, ry - 1, sz + 2, sz + 2, ChatUi.accent());
                ChatUi.glass(g, ChatGlass.Surface.CARD, rx, ry, sz, sz, r.contains(mx, my));
                GuiUtil.drawItemIcon(g, v, rx + 1, ry + 1, 13);
                if (owned != null && !has(v)) g.fill(rx, ry, rx + sz, ry + sz, 0x77101018);
                int idx = i;
                HITS.add(new Hit(r, () -> variant = idx));
            }
            y += rows * (sz + gap) + 2;
        }

        // 입기 / 벗기 / 미보유
        if (canTry) {
            if (owned == null) {
                ChatUi.buttonText(g, font, tr(noMod ? "no_mod" : "loading"), x + w / 2f, y + 6, ChatLayout.META_SCALE, FontPalette.subtle());
            } else if (!has(stack)) {
                ChatUi.buttonText(g, font, tr("locked_hint"), x + w / 2f, y + 6, ChatLayout.META_SCALE, FontPalette.subtle());
            } else {
                boolean on = worn(stack);
                int bw = 50;
                var br = new ChatLayout.Rect(x + (w - bw) / 2, y, bw, 12);
                ChatUi.roundedFill(g, br.x(), br.y(), bw, 12, br.contains(mx, my) ? ChatUi.accent() : (on ? 0xFF8A7F99 : 0xFF6A5AA8));
                ChatUi.buttonText(g, font, tr(on ? "take_off" : "put_on"), br.x() + bw / 2f, br.y() + 6, ChatLayout.META_SCALE, 0xFFFFFFFF);
                int cmd = Integer.parseInt(keyOf(stack).substring(slot.length() + 1));
                HITS.add(new Hit(br, on ? () -> request(WardrobeWire.Kind.OFF, slot, 0) : () -> request(WardrobeWire.Kind.WEAR, slot, cmd)));
            }
        } else {
            ChatUi.buttonText(g, font, tr("note"), x + w / 2f, y + 6, ChatLayout.META_SCALE, FontPalette.subtle());
        }
        y += 14;

        // ◀ 2 / 20 ▶ (디자인 넘기기)
        int n = fams.size();
        ChatUi.buttonText(g, font, (detail + 1) + " / " + n, x + w / 2f, y + 6, ChatLayout.TEXT_SCALE, FontPalette.body());
        var prev = new ChatLayout.Rect(x, y, 20, 12);
        var next = new ChatLayout.Rect(x + w - 20, y, 20, 12);
        ChatUi.buttonText(g, font, "◀", x + 10, y + 6, ChatLayout.TEXT_SCALE, prev.contains(mx, my) ? ChatUi.accent() : FontPalette.body());
        ChatUi.buttonText(g, font, "▶", x + w - 10, y + 6, ChatLayout.TEXT_SCALE, next.contains(mx, my) ? ChatUi.accent() : FontPalette.body());
        HITS.add(new Hit(prev, () -> { detail = (detail - 1 + n) % n; variant = pick(fams.get(detail)); angle = 0; }));
        HITS.add(new Hit(next, () -> { detail = (detail + 1) % n; variant = pick(fams.get(detail)); angle = 0; }));
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
    private static final java.util.Map<net.minecraft.client.resources.model.BakedModel, Fit> FITS = new java.util.IdentityHashMap<>();

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
