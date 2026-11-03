package com.november.mcphone.core.script.client.render;

import com.november.mcphone.core.client.GuiUtil;
import com.november.mcphone.core.script.layout.TextMeasure;
import net.minecraft.client.gui.Font;

/** 把 Font 包成布局引擎要的 {@link TextMeasure}（施工方案 §8.1）。只有一个字段，每帧新建即可。 */
public final class FontMeasure implements TextMeasure {

    private final Font font;

    public FontMeasure(Font font) {
        this.font = font;
    }

    @Override
    public int width(String text) {
        return font.width(text);
    }

    @Override
    public int lineHeight() {
        return font.lineHeight;
    }

    @Override
    public String truncate(String text, int maxWidth) {
        return GuiUtil.truncate(font, text, maxWidth);
    }
}
