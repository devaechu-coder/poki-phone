package com.november.mcphone.core.script.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 一个节点的布局结果（施工方案 §7.3）。坐标相对页面内容区；scroll / list 的子节点在内容坐标系里，画的时候再减 scrollY。
 */
public final class LayoutNode {

    public final Node node;
    public final Style style;
    /**
     * 重排后找回运行时状态用的 key（§7.7）。有 id 时是 {@code #id}，否则是从最近的带 id 祖先（或根）起的路径，
     * 如 {@code #feed.children[2]}；路径用原始下标，兄弟节点被 showIf 藏起来时不会错位。模板写了 {@code :key} 的节点
     * 用 {@code key[长度:值]} 代替下标，列表增删时不错位；IR 的 JSON 形态没有 key 字段，只有模板产出的树有。
     */
    public final String key;
    public final List<LayoutNode> children = new ArrayList<>();

    // build 之后、measure 之前填
    /**
     * 这一趟 layout 问到的图片原始尺寸，{@code src → {宽, 高}}，用不了的图值是 null（见 {@link ImageSizes}）。
     * 建完树、开测之前一次问完，整棵树共用这一份 —— 之后不再改。
     *
     * <p>存下来而不是 measure 时现问：定高 list 的项是滚到了才测的（{@link LayoutEngine#layoutItem}），
     * 而那个入口只有 list 这个节点、拿不到包。一趟 layout 用同一份答案，先测的和后测的才不会对不上。
     */
    Map<String, int[]> imageSizes;

    // measure 阶段填
    int measuredW;
    int measuredH;
    List<String> lines;
    int contentH;
    int[] cumHeights;
    boolean measured;
    boolean truncated;

    // arrange 阶段填
    public int x;
    public int y;
    public int w;
    public int h;

    // 运行时状态：重排时由页面按 key 搬到新树上（§7.7）
    public int scrollY;

    LayoutNode(Node node, Style style, String key) {
        this.node = node;
        this.style = style;
        this.key = key;
    }

    public int measuredW() {
        return measuredW;
    }

    public int measuredH() {
        return measuredH;
    }

    /** text / button 的换行结果，只读；其他类型为 null。 */
    public List<String> lines() {
        return lines;
    }

    /** scroll / list 的子内容总高。 */
    public int contentH() {
        return contentH;
    }

    /** 不定高 list 的累积高度：第 i 项的顶边是 {@code cumHeights[i]}，末尾一格是总高；其他情况为 null。返回的是内部数组，别改。 */
    public int[] cumHeights() {
        return cumHeights;
    }

    /** scrollY 的上限。contentH 不含 padding 而 h 含：不把 padding 加回去，内容最底下那截永远滚不到。 */
    public int scrollMax() {
        return (int) Math.max(0, (long) contentH + style.padTop() + style.padBottom() - h);
    }

    /** 只在根上有意义：节点超过 LayoutEngine 的预算，有子节点没排。页面应当报出来，而不是让它悄悄少一截。 */
    public boolean truncated() {
        return truncated;
    }
}
