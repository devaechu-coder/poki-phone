package com.november.mcphone.core.client;

import com.november.mcphone.api.client.ui.IPhonePage;
import com.november.mcphone.api.client.ui.PhoneViewport;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** 附属页的生命周期和输入归属。与原版 Screen 的左键 dragging 状态无关。 */
public final class PhonePageHost {
    private final BiConsumer<IPhonePage, Throwable> errors;
    private IPhonePage page;
    private PhoneViewport viewport;
    private boolean interactive;
    private long revision;
    private long inputRevision;
    private static final class Press {
        final int button;
        double x, y;
        Press(int button, double x, double y) { this.button = button; this.x = x; this.y = y; }
    }
    private final Map<Integer, Press> presses = new LinkedHashMap<>();
    private final java.util.Set<Integer> keys = new java.util.HashSet<>();

    public PhonePageHost(BiConsumer<IPhonePage, Throwable> errors) { this.errors = errors; }
    public IPhonePage page() { return page; }
    public boolean captured() { return !presses.isEmpty(); }

    public void open(IPhonePage next, PhoneViewport layout, boolean acceptsInput) {
        long request = revision + 1;
        close();
        // 旧页清理回调若已经打开新页，尊重那次跳转，不泄漏或覆盖它。
        if (revision != request || page != null) return;
        page = next;
        viewport = layout;
        interactive = acceptsInput;
        if (invoke(p -> { p.onOpen(); return true; }) && revision == request && page == next) {
            invoke(p -> { p.onResize(layout); return true; });
        }
    }

    public boolean invoke(Predicate<IPhonePage> callback) {
        IPhonePage current = page;
        long currentRevision = revision;
        if (current == null) return false;
        try { return callback.test(current); }
        catch (Throwable error) {
            errors.accept(current, error);
            if (revision == currentRevision && page == current) close();
            return false;
        }
    }

    public void close() {
        revision++;
        inputRevision++;
        IPhonePage current = page;
        page = null; // 回调可重入；旧页的清理不能抹掉新页
        presses.clear();
        keys.clear();
        interactive = false;
        viewport = null;
        if (current == null) return;
        try { current.onInputCancelled(); }
        catch (Throwable error) { errors.accept(current, error); }
        try { current.onClose(); }
        catch (Throwable error) { errors.accept(current, error); }
    }

    public void layout(PhoneViewport next) {
        if (page == null || next.equals(viewport)) return;
        long currentRevision = revision;
        if (captured() || !keys.isEmpty()) cancelInput();
        if (page == null || revision != currentRevision) return;
        viewport = next;
        invoke(p -> { p.onResize(next); return true; });
    }

    public void interactive(boolean next) {
        if (interactive == next) return;
        interactive = next;
        if (!next) cancelInput();
    }

    public void cancelInput() {
        inputRevision++;
        presses.clear();
        keys.clear();
        invoke(p -> { p.onInputCancelled(); return true; });
    }

    public boolean keyPressed(int key, int scan, int modifiers, boolean captureKeyboard) {
        if (!interactive) return false;
        IPhonePage current = page;
        long currentRevision = revision, currentInput = inputRevision;
        boolean consumed = invoke(p -> p.keyPressed(key, scan, modifiers));
        if (revision == currentRevision && inputRevision == currentInput && page == current
                && page != null && interactive && (consumed || captureKeyboard)) keys.add(key);
        return consumed || captureKeyboard;
    }

    public boolean keyReleased(int key, int scan, int modifiers) {
        if (!interactive || !keys.remove(key)) return false;
        invoke(p -> p.keyReleased(key, scan, modifiers));
        return true;
    }

    public boolean click(double x, double y, int button) {
        if (!interactive || viewport == null || !viewport.contains(x, y)) return false;
        IPhonePage current = page;
        long currentRevision = revision, currentInput = inputRevision;
        boolean consumed = invoke(p -> p.mouseClicked(x, y, button));
        if (consumed && revision == currentRevision && inputRevision == currentInput
                && page == current && interactive) presses.put(button, new Press(button, x, y));
        return consumed;
    }

    /** 由 mouseMoved 驱动，所有按钮都可拖动；不依赖原版只针对左键的拖动判定。 */
    public boolean move(double x, double y) {
        if (!interactive || page == null || presses.isEmpty()) return false;
        IPhonePage current = page;
        long currentRevision = revision, currentInput = inputRevision;
        for (Press press : new java.util.ArrayList<>(presses.values())) {
            if (revision != currentRevision || inputRevision != currentInput || page != current || !interactive) break;
            if (presses.get(press.button) != press) continue;
            double dx = x - press.x, dy = y - press.y;
            press.x = x; press.y = y;
            if (dx != 0 || dy != 0) invoke(p -> p.mouseDragged(x, y, press.button, dx, dy));
        }
        return captured();
    }

    public boolean release(double x, double y, int button) {
        if (!interactive || presses.remove(button) == null) return false;
        invoke(p -> p.mouseReleased(x, y, button));
        return true; // 按下属于此页，松手也不能穿透
    }

    public boolean scroll(double x, double y, double sx, double sy) {
        return interactive && viewport != null && viewport.contains(x, y)
                && invoke(p -> p.mouseScrolled(x, y, sx, sy));
    }
}
