package com.november.mcphone.api.client.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可选的轻量控件宿主。子控件后加入者在上层，点击按逆绘制顺序命中；
 * 管理焦点、Tab/Shift+Tab 和每按钮拖动归属。原版控件可由 PhoneWidget 包装，
 * 其绘制仍须适配手机缩放（带自身裁剪的控件优先用 PhoneMultiLineEditBox）。
 * 在构造器中 addWidget，在子控件 onResize 中布局；生命周期内不动态修改控件集合。
 */
public class PhoneWidgetPage implements IPhonePage {
    private final List<PhoneWidget> widgets = new ArrayList<>();
    private final Map<Integer, PhoneWidget> captured = new LinkedHashMap<>();
    private final Map<Integer, PhoneWidget> keys = new LinkedHashMap<>();
    private PhoneWidget focused;
    private boolean opened;
    private boolean closing;
    private long inputRevision;

    protected final <T extends PhoneWidget> T addWidget(T widget) {
        if (opened || closing) throw new IllegalStateException("Register widgets before opening the page");
        for (PhoneWidget existing : widgets) {
            if (existing == widget) throw new IllegalArgumentException("Widget is already registered");
        }
        widgets.add(java.util.Objects.requireNonNull(widget));
        return widget;
    }

    private void focus(PhoneWidget next) {
        if (focused == next) return;
        PhoneWidget old = focused;
        focused = next;
        if (old != null) old.onFocusChanged(false);
        if (focused == next && next != null) next.onFocusChanged(true);
    }

    @Override public void onOpen() {
        if (closing) throw new IllegalStateException("Cannot reopen while closing widgets");
        if (opened) return;
        opened = true;
        for (PhoneWidget widget : widgets) {
            if (!opened) break;
            widget.onOpen();
        }
    }
    @Override public void onClose() {
        if (!opened) return;
        opened = false;
        closing = true;
        inputRevision++;
        captured.clear();
        keys.clear();
        // 某个控件清理失败不能使后续控件泄漏。
        Throwable failure = null;
        try {
            try { focus(null); } catch (Throwable error) { failure = error; }
            for (PhoneWidget widget : widgets) {
                try { widget.onClose(); }
                catch (Throwable error) { failure = collect(failure, error); }
            }
        } finally {
            closing = false;
        }
        if (failure != null) throw new IllegalStateException("Widget cleanup failed", failure);
    }
    private static Throwable collect(Throwable first, Throwable next) {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }
    @Override public void onResize(PhoneViewport viewport) {
        long currentInput = inputRevision;
        for (PhoneWidget widget : widgets) {
            if (!opened || inputRevision != currentInput) break;
            widget.onResize(viewport);
        }
    }
    @Override public void render(PhoneCanvas canvas) {
        long currentInput = inputRevision;
        for (PhoneWidget widget : widgets) {
            if (!opened || inputRevision != currentInput) break;
            widget.render(canvas);
        }
    }
    @Override public void tick() {
        long currentInput = inputRevision;
        for (PhoneWidget widget : widgets) {
            if (!opened || inputRevision != currentInput) break;
            widget.tick();
        }
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!opened) return false;
        long currentInput = inputRevision;
        for (int i = widgets.size() - 1; i >= 0; i--) {
            PhoneWidget widget = widgets.get(i);
            if (widget.contains(x, y) && widget.mouseClicked(x, y, button)) {
                if (!opened || inputRevision != currentInput) return true;
                focus(widget.focusable() ? widget : null);
                if (opened && inputRevision == currentInput) captured.put(button, widget);
                return true;
            }
        }
        focus(null);
        return false;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        PhoneWidget widget = captured.get(button);
        return widget != null && widget.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        PhoneWidget widget = captured.remove(button);
        return widget != null && widget.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            PhoneWidget widget = widgets.get(i);
            if (widget.contains(x, y) && widget.mouseScrolled(x, y, sx, sy)) return true;
        }
        return false;
    }
    @Override public void mouseMoved(double x, double y) {
        for (PhoneWidget widget : widgets) widget.mouseMoved(x, y);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!opened) return false;
        if (key == 258) { // GLFW_KEY_TAB，Shift 的修饰位在各目标一致
            int direction = (modifiers & 1) == 0 ? 1 : -1;
            int start = widgets.indexOf(focused);
            if (start < 0) start = direction == 1 ? -1 : 0;
            for (int step = 1; step <= widgets.size(); step++) {
                PhoneWidget candidate = widgets.get(Math.floorMod(start + direction * step, widgets.size()));
                if (candidate.focusable()) { focus(candidate); return true; }
            }
            return false;
        }
        PhoneWidget target = focused;
        if (target == null) return false;
        long currentInput = inputRevision;
        boolean consumed = target.keyPressed(key, scan, modifiers);
        if (opened && inputRevision == currentInput && (consumed || target.capturesKeyboard())) keys.put(key, target);
        return consumed;
    }
    @Override public boolean keyReleased(int key, int scan, int modifiers) {
        PhoneWidget target = keys.remove(key);
        return target != null && target.keyReleased(key, scan, modifiers);
    }
    @Override public boolean charTyped(int codePoint, int modifiers) {
        return focused != null && focused.charTyped(codePoint, modifiers);
    }
    @Override public boolean charTyped(char unit, int modifiers) { return charTyped((int) unit, modifiers); }
    @Override public boolean capturesKeyboard() { return focused != null && focused.capturesKeyboard(); }
    @Override public void onInputCancelled() {
        inputRevision++;
        captured.clear();
        keys.clear();
        Throwable failure = null;
        try { focus(null); } catch (Throwable error) { failure = error; }
        for (PhoneWidget widget : widgets) {
            try { widget.onInputCancelled(); }
            catch (Throwable error) { failure = collect(failure, error); }
        }
        if (failure != null) throw new IllegalStateException("Widget input cleanup failed", failure);
    }
}
