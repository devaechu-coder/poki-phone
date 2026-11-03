package com.november.mcphone.feature.chat.client;
import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.PhoneSkin;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 个人页共用行与图标，标签、图标和箭头使用同一纵向中心。 */
final class ChatProfileUi {
    /** 顺序同时驱动显示和操作；轮廓跨度来自原 PNG 的透明通道，原图不裁剪。 */
    enum Icon {
        FAVORITE("favorites", PhoneSkin.Element.CHAT_FAVORITE, 72, 48),
        GITHUB("github", PhoneSkin.Element.CHAT_GITHUB, 96, 48),
        SPONSOR("sponsor", PhoneSkin.Element.CHAT_SPONSOR, 80, 47),
        SETTINGS("settings", PhoneSkin.Element.CHAT_SETTINGS, 78, 48);

        final String label;
        final PhoneSkin.Element element;
        final int inkSpan, inkCenterY;
        Icon(String label, PhoneSkin.Element element, int inkSpan, int inkCenterY) {
            this.label = label;
            this.element = element;
            this.inkSpan = inkSpan;
            this.inkCenterY = inkCenterY;
        }
    }
    static String tr(String key) { return Component.translatable("mcphone.chat.profile."+key).getString(); }
    static void row(GuiGraphics g,Font font,ChatLayout.Rect b,String text,Icon icon,int mx,int my) {
        ChatUi.glass(g,ChatGlass.Surface.CARD,b.x(),b.y(),b.width(),b.height(),b.contains(mx,my));
        float centerY=b.y()+b.height()/2f;
        if (icon!=null) icon(g,icon,b.x()+5,centerY-4,FontPalette.body());
        ChatUi.label(g,font,ChatUi.truncate(font,text,b.width()-(icon==null?20:31),ChatLayout.TEXT_SCALE),
                b.x()+(icon==null?5:18),centerY,ChatLayout.TEXT_SCALE,FontPalette.body());
        ChatUi.forwardIcon(g,b.x()+b.width()-7,centerY,5,FontPalette.subtle());
    }
    static void button(GuiGraphics g,Font font,ChatLayout.Rect b,String text,int mx,int my,int color) {
        ChatUi.glass(g,ChatGlass.Surface.BUTTON,b.x(),b.y(),b.width(),b.height(),b.contains(mx,my));
        ChatUi.buttonText(g,font,ChatUi.truncate(font,text,b.width()-6,ChatLayout.TEXT_SCALE),b.x()+b.width()/2f,b.y()+b.height()/2f,ChatLayout.TEXT_SCALE,color);
    }
    static void icon(GuiGraphics g,Icon icon,float x,float y,int color) {
        g.pose().pushPose();
        try {
            // 四张图的留白不同：统一可见最长边为 8 像素，补偿心形的轮廓中心偏差。
            float scale = 8f / icon.inkSpan;
            g.pose().translate(x + 4 - 48 * scale, y + 4 - icon.inkCenterY * scale, 0);
            g.pose().scale(scale, scale, 1);
            g.setColor(((color >>> 16) & 255) / 255f, ((color >>> 8) & 255) / 255f,
                    (color & 255) / 255f, ((color >>> 24) & 255) / 255f);
            try { if (PhoneSkin.draw(g, icon.element, 0, 0, 96, 96)) return; }
            finally { g.setColor(1, 1, 1, 1); }
        } finally { g.pose().popPose(); }
        // 资源缺失时沿用几何兜底，保持入口可辨认。
        g.pose().pushPose();
        try {
            g.pose().translate(x,y,0);g.pose().scale(1f/3,1f/3,1);
            switch(icon) {
                case FAVORITE -> { g.renderOutline(5,3,14,17,color);g.fill(8,14,16,16,color);g.fill(5,20,7,22,color);g.fill(17,20,19,22,color); }
                case SETTINGS -> { g.renderOutline(5,5,14,14,color);g.renderOutline(9,9,6,6,color);
                    g.fill(10,2,14,5,color);g.fill(10,19,14,22,color);g.fill(2,10,5,14,color);g.fill(19,10,22,14,color); }
                case GITHUB -> { g.fill(5,6,19,17,color);g.fill(5,3,9,6,color);g.fill(15,3,19,6,color);
                    g.fill(10,17,14,22,color);g.fill(2,15,5,17,color);g.fill(4,17,10,19,color); }
                case SPONSOR -> { g.fill(3,6,10,13,color);g.fill(14,6,21,13,color);g.fill(9,8,15,19,color);
                    g.fill(5,12,19,16,color);g.fill(9,16,15,20,color);g.fill(11,20,13,22,color); }
            }
        } finally { g.pose().popPose(); }
    }
}
