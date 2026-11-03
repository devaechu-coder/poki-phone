package com.november.mcphone.core.script.client.render;

import com.november.mcphone.api.client.ui.PhoneCanvas;
import com.november.mcphone.core.script.layout.LayoutEngine;
import com.november.mcphone.core.script.layout.LayoutNode;
import com.november.mcphone.core.script.layout.TextMeasure;

import java.util.HashSet;
import java.util.Set;

/**
 * 命中判定（施工方案 §8.5）。点击、悬停、滚轮走同一次遍历，偏移与 {@link Renderer} 画的一致：
 * 子节点逆序测（后画的在上），scroll / list 的裁剪区逐级取交集、子节点减去 scrollY，list 只测可见区里的项。
 * 画布矩形是最外层的裁剪区：溢出到内容区之外的部分画得出来，但不悬停、不命中。
 *
 * <p>鼠标坐标原样比较，不取整。节点边界都是整数，小数 m 落在 [x, x+w) 里当且仅当 floor(m) 落在里面，
 * 所以宿主的手机倍率恰为 1 时，给悬停的截断整数与给点击的小数判给同一个节点。倍率不是 1 时
 * （开机动画、窗口放不下被缩小、HUD、设置里调了界面大小）宿主的两条坐标本身就对不齐，这里怎么取整都会错判一部分，只能在 PhoneScreen 修。
 */
public final class HitTest {

    /** 滚轮一格滚动的像素，约两行字。 */
    public static final int SCROLL_STEP = 12;

    private HitTest() {
    }

    /** 鼠标下最上面那个可交互节点（button / toggle / tab-bar），没有返回 null。禁用的也返回：它要吃掉点击。 */
    public static LayoutNode pick(LayoutNode root, PhoneCanvas c, double mx, double my) {
        return pick(root, c.x(), c.y(), c.width(), c.height(), mx, my, new FontMeasure(c.font()));
    }

    /** 同上，画布矩形与鼠标直接给数。 */
    public static LayoutNode pick(LayoutNode root, int x, int y, int w, int h, double mx, double my, TextMeasure tm) {
        return find(root, x, y, mx, my, x, y, (long) x + w, (long) y + h, tm, false, null);
    }

    /** 鼠标下最内层的 scroll / list，没有返回 null。不往外冒泡：内层滚到头也不带着外层滚（§5.1）。 */
    public static LayoutNode pickScroller(LayoutNode root, PhoneCanvas c, double mx, double my) {
        return pickScroller(root, c.x(), c.y(), c.width(), c.height(), mx, my, new FontMeasure(c.font()));
    }

    /** 同上，画布矩形与鼠标直接给数。 */
    public static LayoutNode pickScroller(LayoutNode root, int x, int y, int w, int h, double mx, double my,
                                          TextMeasure tm) {
        return find(root, x, y, mx, my, x, y, (long) x + w, (long) y + h, tm, true, null);
    }

    /** pick 命中的节点连同它的全部祖先；没命中是空集。 */
    public static Set<LayoutNode> hoverChain(LayoutNode root, int x, int y, int w, int h, double mx, double my,
                                             TextMeasure tm) {
        Set<LayoutNode> chain = new HashSet<>();
        find(root, x, y, mx, my, x, y, (long) x + w, (long) y + h, tm, false, chain);
        return chain;
    }

    /**
     * 滚一格滚轮，amount 为正是往上。触控板不足 1px 的小数格也滚 1px，否则慢慢推永远推不动。
     * 滚到头返回 false，把事件留给手机页面（§8.7）。
     */
    public static boolean scroll(LayoutNode scroller, double amount) {
        int before = Renderer.clampScroll(scroller);
        long step = Math.round(amount * SCROLL_STEP);
        if (step == 0) step = (long) Math.signum(amount);
        // 夹一下再减：amount 极大时 step 是 Long.MIN_VALUE，before - step 会回绕成负数，滚到顶而不是底
        step = Math.max(-Integer.MAX_VALUE, Math.min(Integer.MAX_VALUE, step));
        scroller.scrollY = (int) Math.max(0, Math.min(before - step, scroller.scrollMax()));
        return scroller.scrollY != before;
    }

    /** tab-bar 上 mx 落在第几段。切法与 Renderer 画的一样：等宽，余数给最后一段。 */
    public static int segmentAt(LayoutNode tabBar, PhoneCanvas c, double mx) {
        return segmentAt(tabBar, c.x(), mx);
    }

    /** 同上，ox 是画布左边界。 */
    public static int segmentAt(LayoutNode tabBar, int ox, double mx) {
        int count = Renderer.tabs(tabBar).size();
        if (count == 0) return 0;
        int segW = tabBar.w / count;
        // 比段数还窄时前面几段宽 0，画出来整条都是最后一段
        if (segW <= 0) return count - 1;
        long i = (long) Math.floor((mx - ((long) tabBar.x + ox)) / segW);
        return (int) Math.max(0, Math.min(count - 1, i));
    }

    /**
     * (mx, my) 处最上面那个要找的节点：scrollers 为 true 找 scroll / list，否则找可交互节点。chain 不为 null 时收下命中节点与祖先。
     *
     * <p>只有 scroll / list 按自己的矩形剪掉子树。别的容器不裁剪，溢出去的子节点画在外面，也就得点得到。
     */
    private static LayoutNode find(LayoutNode n, int ox, int oy, double mx, double my,
                                   long clipL, long clipT, long clipR, long clipB,
                                   TextMeasure tm, boolean scrollers, Set<LayoutNode> chain) {
        if (mx < clipL || mx >= clipR || my < clipT || my >= clipB) return null;
        long x = (long) n.x + ox;
        long y = (long) n.y + oy;
        boolean inSelf = n.w > 0 && n.h > 0 && mx >= x && mx < x + n.w && my >= y && my < y + n.h;
        boolean scroller = Renderer.isScroller(n);
        LayoutNode hit = null;
        if (scroller) {
            if (!inSelf) return null;
            int sy = Renderer.clampScroll(n);
            int[] range = Renderer.childRange(n, sy);
            for (int i = range[1]; i >= range[0] && hit == null; i--) {
                LayoutEngine.layoutItem(n, i, tm);
                hit = find(n.children.get(i), ox, oy - sy, mx, my, Math.max(clipL, x), Math.max(clipT, y),
                        Math.min(clipR, x + n.w), Math.min(clipB, y + n.h), tm, scrollers, chain);
            }
        } else {
            for (int i = n.children.size() - 1; i >= 0 && hit == null; i--) {
                hit = find(n.children.get(i), ox, oy, mx, my, clipL, clipT, clipR, clipB, tm, scrollers, chain);
            }
        }
        if (hit == null && inSelf && (scrollers ? scroller : n.node.type().interactive)) hit = n;
        if (hit != null && chain != null) chain.add(n);
        return hit;
    }
}
