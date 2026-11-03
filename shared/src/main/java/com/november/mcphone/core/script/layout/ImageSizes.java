package com.november.mcphone.core.script.layout;

/**
 * 图片的原始尺寸从哪儿来（施工方案 §5.2：{@code image} 不写 w / h 时按原始尺寸算）。
 *
 * <p>布局这一层算不出它 —— 尺寸在 PNG 的头里，读它的是客户端的
 * {@code core.script.client.tex.AppTextures}。和 {@link TextMeasure} 一样，这是布局对外界的一个口子，
 * 接口只收字符串、只还整数，纯计算层不因此沾上客户端类型。
 *
 * <p>{@link LayoutEngine#layout} 在建完树、开测之前把整棵树里的图问一遍，之后整趟 layout 都用那一份答案。
 * 不是测到哪张问哪张：定高 list 的项是滚到了才测的（{@link LayoutEngine#layoutItem}），那个入口拿不到包。
 */
public interface ImageSizes {

    /** {@code {宽, 高}}；这张图用不了（不在包里、超限、坏文件）时返回 null。 */
    int[] size(String src);

    /** 一张都问不到。没有包的场合（IR 的单测、还没装包的预览）用它，image 按占位尺寸排。 */
    ImageSizes NONE = src -> null;
}
