package com.november.mcphone.core.script.layout;

/** 布局引擎对外界的唯一依赖（施工方案 §7.2）。真实实现在 client 侧包 Font，测试里是每字符 6px 的假实现。 */
public interface TextMeasure {

    /** 字符串像素宽度。 */
    int width(String text);

    /** 行高。Minecraft 默认 9。 */
    int lineHeight();

    /**
     * 把 text 截到不超过 maxWidth，超出时尾部加省略号，放得下时原样返回。
     *
     * <p>真实实现转发给 GuiUtil.truncate：那边已经算进了省略号自身的宽度，别自己写。
     */
    String truncate(String text, int maxWidth);
}
