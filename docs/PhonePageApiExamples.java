package com.november.mcphone.api.client.ui;

/** 只引用稳定 API 的最小地图交互示例，随四目标断言源码一起编译。不是 HowToGo 适配实现。 */
public final class PhonePageApiExamples {
    public static final class MapPage implements IPhonePage {
        private PhoneViewport viewport;
        private double offsetX, offsetY, zoom = 1;
        private boolean dragging;
        @Override public void onResize(PhoneViewport viewport) { this.viewport = viewport; }
        @Override public void render(PhoneCanvas canvas) {
            // 示例背景；真实地图由附属自己的渲染器绘制。
            canvas.graphics().fill(canvas.x(), canvas.y(), canvas.x() + canvas.width(),
                    canvas.y() + canvas.height(), 0xff202830);
            canvas.graphics().drawString(canvas.font(), "Map zoom: " + zoom,
                    canvas.x() + 4, canvas.y() + 4, canvas.style().bodyColor(), false);
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            dragging = viewport != null && viewport.contains(x, y) && button == 1;
            return dragging;
        }
        @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
            if (!dragging || button != 1) return false;
            offsetX += dx / zoom; offsetY += dy / zoom;
            return true;
        }
        @Override public boolean mouseReleased(double x, double y, int button) {
            if (button != 1) return false;
            dragging = false;
            return true;
        }
        @Override public void onInputCancelled() { dragging = false; }
        @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
            offsetX += sx * 8 / zoom;
            zoom = Math.max(0.25, Math.min(8, zoom * Math.pow(1.1, sy)));
            return true;
        }
    }
}
