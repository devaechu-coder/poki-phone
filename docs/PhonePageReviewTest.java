package com.november.mcphone.core.client;

import com.november.mcphone.api.client.ui.*;
import java.util.ArrayList;
import java.util.List;

/** 二次审查：回调重入、取消后的输入归属、子控件异常清理。 */
public final class PhonePageReviewTest {
    private static final PhoneViewport VIEW = new PhoneViewport(0, 0, 100, 200, 1);
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static class Page implements IPhonePage {
        int closes, layouts;
        PhoneViewport layout;
        @Override public void render(PhoneCanvas canvas) {}
        @Override public void onClose() { closes++; }
        @Override public void onResize(PhoneViewport next) { layout = next; layouts++; }
        @Override public boolean mouseClicked(double x, double y, int button) { return true; }
    }
    public static void main(String[] args) {
        List<Throwable> errors = new ArrayList<>();
        PhonePageHost host = new PhonePageHost((page, error) -> errors.add(error));
        Page reused = new Page();
        host.open(reused, VIEW, true);
        host.invoke(page -> {
            host.close(); host.open(reused, VIEW, true);
            throw new IllegalStateException("old callback");
        });
        check(host.page() == reused && reused.closes == 1, "旧回调异常不能关闭同实例的新会话");
        check(errors.size() == 1, "旧会话异常仍被报告");
        host.close();

        PhoneViewport replacementLayout = new PhoneViewport(20, 30, 70, 80, 2);
        Page replacement = new Page();
        Page changing = new Page() {
            @Override public void onInputCancelled() { host.open(replacement, replacementLayout, true); }
        };
        host.open(changing, VIEW, true); host.click(10, 10, 0);
        host.layout(new PhoneViewport(0, 0, 50, 50, 1));
        check(host.page() == replacement && replacement.layout.equals(replacementLayout), "取消回调换页后不覆盖新页布局");
        host.close();

        Page cancelledClick = new Page() {
            @Override public boolean mouseClicked(double x, double y, int button) { host.cancelInput(); return true; }
            @Override public boolean keyPressed(int key, int scan, int modifiers) { host.cancelInput(); return true; }
        };
        host.open(cancelledClick, VIEW, true); host.click(10, 10, 0);
        check(!host.captured(), "按下回调取消后不重新建立鼠标捕获");
        host.keyPressed(65, 0, 0, true);
        check(!host.keyReleased(65, 0, 0), "按键回调取消后不重新建立键盘捕获");
        host.close();

        List<Integer> drags = new ArrayList<>();
        Page multi = new Page() {
            @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
                drags.add(button);
                if (button == 0) host.release(x, y, 1);
                return true;
            }
        };
        host.open(multi, VIEW, true);
        host.click(10, 10, 0); host.click(10, 10, 1); host.click(10, 10, 2); host.move(20, 20);
        check(drags.equals(List.of(0, 2)), "取消中间按钮不能漏发其他有效按钮");
        host.close(); drags.clear();
        Page recapture = new Page() {
            @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
                drags.add(button);
                if (button == 0) { host.release(x, y, 1); host.click(x, y, 1); }
                return true;
            }
        };
        host.open(recapture, VIEW, true); host.click(10, 10, 0); host.click(10, 10, 1); host.move(20, 20);
        check(drags.equals(List.of(0)), "同按钮重新捕获后不发送上一会话的拖动");
        host.close();

        widgetCleanup();
        System.out.println("PhonePageReviewTest passed: " + checks + " assertions");
    }

    private static void widgetCleanup() {
        RuntimeException sharedFailure = new RuntimeException("shared failure");
        class Widget extends Page implements PhoneWidget {
            boolean focused, fail;
            Widget(boolean fail) { this.fail = fail; }
            @Override public boolean contains(double x, double y) { return true; }
            @Override public void onFocusChanged(boolean next) { focused = next; }
            @Override public void onInputCancelled() { if (fail) throw sharedFailure; }
            @Override public void onClose() { super.onClose(); if (fail) throw sharedFailure; }
        }
        class Widgets extends PhoneWidgetPage {
            Widget first = addWidget(new Widget(true));
            Widget second = addWidget(new Widget(true));
            Widget last = addWidget(new Widget(false));
            void duplicate() { addWidget(first); }
        }
        Widgets widgets = new Widgets();
        boolean rejected = false;
        try { widgets.duplicate(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "同实例控件不重复注册");
        widgets.onOpen(); widgets.onResize(VIEW); widgets.mouseClicked(0, 0, 0);
        try { widgets.onInputCancelled(); } catch (IllegalStateException expected) { }
        check(!widgets.last.focused, "取消失败仍移除焦点");
        try { widgets.onClose(); } catch (IllegalStateException expected) { }
        check(widgets.first.closes == 1 && widgets.second.closes == 1 && widgets.last.closes == 1,
                "多个控件抛同一异常仍清理所有控件");
        widgets.onClose();
        check(widgets.last.closes == 1, "重复关闭幂等");
        check(!widgets.mouseDragged(0, 0, 0, 1, 1), "关闭后无残留捕获");

        class CloseOnTick extends PhoneWidgetPage {
            int laterTicks;
            PhoneWidget first = addWidget(new PhoneWidget() {
                @Override public boolean contains(double x, double y) { return false; }
                @Override public void render(PhoneCanvas canvas) {}
                @Override public void tick() { CloseOnTick.this.onClose(); }
            });
            PhoneWidget later = addWidget(new PhoneWidget() {
                @Override public boolean contains(double x, double y) { return false; }
                @Override public void render(PhoneCanvas canvas) {}
                @Override public void tick() { laterTicks++; }
            });
        }
        CloseOnTick closedDuringTick = new CloseOnTick();
        closedDuringTick.onOpen(); closedDuringTick.tick();
        check(closedDuringTick.laterTicks == 0, "前一个控件关闭页面后不再更新其余已关闭控件");
    }
}
