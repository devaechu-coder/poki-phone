package com.november.mcphone.core.client;

import com.november.mcphone.api.client.ui.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;

/** 无窗口断言：实际页面宿主、UTF-16 兼容、控件路由及矩阵栈恢复。 */
public final class PhonePageHostTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static final PhoneViewport VIEW = new PhoneViewport(10, 20, 100, 200, 2);
    private static class Page implements IPhonePage {
        final List<String> events = new ArrayList<>();
        boolean consume = true;
        double dx, dy, sx, sy;
        int layouts, ticks, closes, cancels;
        @Override public void render(PhoneCanvas canvas) {}
        @Override public void onOpen() { events.add("open"); }
        @Override public void onResize(PhoneViewport viewport) { layouts++; events.add("layout"); }
        @Override public boolean mouseClicked(double x, double y, int button) { events.add("down" + button); return consume; }
        @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
            events.add("drag" + button); this.dx = dx; this.dy = dy; return true;
        }
        @Override public boolean mouseReleased(double x, double y, int button) { events.add("up" + button); return false; }
        @Override public boolean mouseScrolled(double x, double y, double sx, double sy) { this.sx = sx; this.sy = sy; return true; }
        @Override public void onInputCancelled() { cancels++; }
        @Override public void onClose() { closes++; }
        @Override public void tick() { ticks++; }
    }
    public static void main(String[] args) {
        List<Throwable> errors = new ArrayList<>();
        PhonePageHost host = new PhonePageHost((p, error) -> errors.add(error));
        Page page = new Page();
        host.open(page, VIEW, true);
        check(page.events.equals(List.of("open", "layout")), "先打开再初始布局");
        check(!VIEW.contains(110, 20) && !VIEW.contains(10, 220), "半开内容边界");
        check(!host.click(9, 20, 1) && !host.captured(), "系统区域不传给页面");
        check(host.click(20, 30, 1) && host.captured(), "右键按下可捕获");
        host.move(120, 240);
        check(page.events.contains("drag1") && page.dx == 100 && page.dy == 210, "越界右键拖动及逻辑增量");
        check(host.release(120, 240, 1) && !host.captured(), "越界松手不穿透");
        check(!host.release(120, 240, 1), "不重复释放");
        page.consume = false;
        check(!host.click(20, 30, 0) && !host.captured(), "false 不建立捕获");
        page.consume = true;
        host.click(20, 30, 0); host.click(20, 30, 2); host.move(21, 32);
        check(page.events.contains("drag0") && page.events.contains("drag2"), "多按钮分别路由");
        host.release(21, 32, 0);
        check(host.captured(), "释放一个按钮保留其他捕获");
        host.interactive(false);
        check(!host.captured() && page.cancels == 1 && page.closes == 0, "HUD 失交互只取消输入，不关页");
        check(!host.click(20, 30, 1), "后台页不接收点击");
        host.interactive(false);
        check(page.cancels == 1, "重复失焦无重复通知");
        host.invoke(p -> { p.tick(); return true; });
        check(page.ticks == 1, "后台仍能更新");
        host.interactive(true);
        check(host.scroll(20, 30, 3, -2) && page.sx == 3 && page.sy == -2, "滚动保留两轴");
        check(!host.scroll(0, 0, 3, -2), "内容区外不滚动页面");
        host.layout(VIEW);
        check(page.layouts == 1, "相同布局不重复通知");
        host.click(20, 30, 1);
        host.layout(new PhoneViewport(20, 30, 100, 200, 1.5));
        check(page.layouts == 2 && !host.captured(), "布局变化先结束拖动");
        check(host.keyPressed(65, 0, 0, true), "键盘捕获页可记录未消费按下");
        check(host.keyReleased(65, 0, 0), "按下与释放配对");
        check(!host.keyReleased(65, 0, 0), "孤立释放不转发");
        host.keyPressed(66, 0, 0, true); host.interactive(false); host.interactive(true);
        check(!host.keyReleased(66, 0, 0), "取消后晚到 key up 不穿透旧会话");
        host.close(); host.close();
        check(page.closes == 1 && host.page() == null, "关闭幂等");

        Page broken = new Page() { @Override public void onOpen() { throw new IllegalStateException("open"); } };
        host.open(broken, VIEW, true);
        check(host.page() == null && broken.closes == 1 && broken.layouts == 0, "初始化失败不激活或布局");
        Page badDrag = new Page() {
            @Override public boolean mouseDragged(double x, double y, int b, double dx, double dy) { throw new IllegalStateException("drag"); }
        };
        host.open(badDrag, VIEW, true); host.click(20, 30, 1); host.move(30, 40);
        check(host.page() == null && !host.captured() && badDrag.closes == 1, "输入异常清理页和捕获");
        Page badCancel = new Page() { @Override public void onInputCancelled() { throw new IllegalStateException("cancel"); } };
        host.open(badCancel, VIEW, true); host.close();
        check(badCancel.closes == 1, "取消回调异常也执行关闭");
        check(errors.size() == 3, "所有预期错误被报告");
        Page replacement = new Page();
        Page redirect = new Page() {
            @Override public void onClose() { super.onClose(); host.open(replacement, VIEW, true); }
        };
        host.open(redirect, VIEW, true);
        host.open(new Page(), VIEW, true);
        check(host.page() == replacement && redirect.closes == 1, "关闭回调重入跳转不被外层覆盖");
        host.close();

        Page badLayout = new Page() {
            @Override public void onResize(PhoneViewport viewport) { throw new IllegalStateException("layout"); }
        };
        host.open(badLayout, VIEW, true);
        check(host.page() == null && badLayout.closes == 1, "布局失败清理页面");
        Page badTick = new Page() { @Override public void tick() { throw new IllegalStateException("tick"); } };
        host.open(badTick, VIEW, true); host.invoke(p -> { p.tick(); return true; });
        check(host.page() == null && badTick.closes == 1, "更新失败清理页面");

        StringBuilder text = new StringBuilder();
        IPhonePage legacy = new Page() { @Override public boolean charTyped(char c, int mods) { text.append(c); return true; } };
        check(legacy.charTyped(0x1f680, 0) && text.toString().equals("\ud83d\ude80"), "旧 char 页面收到完整代理对");
        check(!legacy.charTyped(-1, 0), "拒绝非法码点");
        final int[] calls = {0};
        IPhonePage scroll = new IPhonePage() {
            @Override public void render(PhoneCanvas canvas) {}
            @Override public boolean mouseScrolled(double x, double y, double amount) { calls[0]++; return amount == -2; }
        };
        check(scroll.mouseScrolled(0, 0, 3, -2) && calls[0] == 1, "旧滚轮只收到一次");

        PoseStack pose = new PoseStack();
        pose.translate(5, 7, 0); pose.pushPose(); pose.scale(2, 2, 1);
        var expected = new org.joml.Matrix4f(pose.last().pose());
        try (var scope = new PhonePoseScope(pose)) { pose.popPose(); pose.translate(99, 99, 0); }
        check(pose.last().pose().equals(expected), "多 pop 也恢复栈顶矩阵");
        pose.popPose();
        check(com.november.mcphone.platform.client.Draw.poseAtBase(pose) && pose.last().pose().m30() == 5, "恢复完整父层和栈深");
        try {
            try (var scope = new PhonePoseScope(pose)) {
                pose.pushPose(); pose.pushPose(); pose.translate(99, 99, 0);
                throw new IllegalStateException("render");
            }
        } catch (IllegalStateException expectedFailure) { }
        check(com.november.mcphone.platform.client.Draw.poseAtBase(pose) && pose.last().pose().m30() == 5, "异常且少 pop 时恢复");

        widgetTests();
        System.out.println("PhonePageHostTest passed: " + checks + " assertions");
    }

    private static void widgetTests() {
        class Widget extends Page implements PhoneWidget {
            boolean focused;
            @Override public boolean contains(double x, double y) { return x >= 10 && x < 50 && y >= 20 && y < 60; }
            @Override public void onFocusChanged(boolean focused) { this.focused = focused; }
            @Override public boolean capturesKeyboard() { return true; }
        }
        class Widgets extends PhoneWidgetPage {
            Widget first = addWidget(new Widget()), second = addWidget(new Widget());
        }
        Widgets page = new Widgets(); page.onOpen(); page.onResize(VIEW);
        check(page.mouseClicked(20, 30, 1) && page.second.focused && !page.first.focused, "上层控件优先点击和焦点");
        page.mouseDragged(999, 999, 1, 2, 3); page.mouseReleased(999, 999, 1);
        check(page.second.events.contains("drag1") && page.second.events.contains("up1"), "子控件保留拖动归属");
        page.keyPressed(258, 0, 0);
        check(page.first.focused && !page.second.focused, "Tab 循环焦点");
        page.keyPressed(258, 0, 1);
        check(page.second.focused, "Shift+Tab 反向焦点");
        check(page.capturesKeyboard(), "文本焦点捕获键盘");
        page.onInputCancelled();
        check(!page.capturesKeyboard() && !page.second.focused, "取消清理焦点");
        page.onClose();
        check(page.first.closes == 1 && page.second.closes == 1, "子控件资源均关闭");
    }
}
