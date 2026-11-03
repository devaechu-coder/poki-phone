package com.november.mcphone.core.script.client.tex;

import com.november.mcphone.core.client.ImageCodec;
import com.november.mcphone.core.script.layout.ImageSizes;
import com.november.mcphone.core.script.pkg.AppPackage;
import com.november.mcphone.core.script.pkg.PackageReader;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 包内图片的判定、显存名额、缓存与释放（施工方案 §5.2 image、§5.4、§28.2 #13、§8.8）。
 *
 * <p>贴图真的进没进显存这里测不了 —— 有 Minecraft 的类路径，没有游戏的运行环境。所以上传口换成假的：
 * 要测的是"哪些图收、哪些图拒、满了淘汰谁、关页面还不还"，那部分一个 GL 调用都不需要。
 *
 * <p><b>真上传口那条链一行都没跑</b>（{@code GameUploader} → {@code ImageCodec.decodeAndScale} → 建 NativeImage
 * → {@code TextureManager.register}）：NativeImage 要 LWJGL 的本地库，register 要 GL 上下文，断言测试这个 JVM
 * 两样都没有。所以带透明 / 灰度 / 索引色 / APNG 的 PNG 解出来对不对、ARGB→ABGR 的字节序、真的显存有没有涨，
 * 只能在游戏里看（§8.8）—— 这是"测不了"，不是"忘了测"。
 *
 * <p>假上传口给的 location 是 null（docs/ 是三个目标共编一份，而 ResourceLocation 的构造在 1.20.1 与
 * 1.21.1 上不同名），所以断言"画得出来"一律走 {@link AppTextures#uploaded}，不看 {@code of()} 的返回值 ——
 * 拿 {@code of(...) == null} 当正面断言的话，实现整个坏掉也照样绿。
 *
 * <p>跑法：{@code ./gradlew assertTests}（在 {@code platforms/<目标名>/} 下）。要 Minecraft 的类路径，
 * 本地拿 javac 单编这一份编不过 —— {@code shared/} 引的 {@code MCphone} 在 {@code platforms/} 下。
 */
public class AppTexturesTest {

    static int checks = 0;
    static final List<String> failures = new ArrayList<>();

    static void eq(Object actual, Object expected, String what) {
        checks++;
        if (!java.util.Objects.equals(actual, expected)) {
            failures.add(what + "  期望 " + expected + "，实际 " + actual);
        }
    }

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) failures.add(what);
    }

    public static void main(String[] args) throws Exception {
        pngHeader();
        paths();
        limits();
        rejectedCap();
        vram();
        cacheAndRelease();
        twoApps();
        reload();
        bothSides();

        System.out.println("断言 " + checks + " 条");
        if (!failures.isEmpty()) {
            System.out.println("失败 " + failures.size() + " 条：");
            for (String f : failures) System.out.println("  - " + f);
            System.exit(1);
        }
        System.out.println("全部通过");
    }

    // ============================================================
    //  PNG 头
    // ============================================================

    static void pngHeader() {
        eq(Arrays.toString(PngHeader.size(png(64, 48))), "[64, 48]", "头里读得出宽高");
        eq(Arrays.toString(PngHeader.size(png(1, 1))), "[1, 1]", "1×1 也读得出");

        // 一张真能解开的 PNG（IHDR + IDAT + IEND、CRC 都对），不是只有头的样本
        eq(Arrays.toString(PngHeader.size(realPng(3, 2))), "[3, 2]", "真 PNG 读得出宽高");
        check(realPng(3, 2).length > 24, "真 PNG 不止一个头");

        eq(PngHeader.size(null), null, "null 不是 PNG");
        eq(PngHeader.size(new byte[0]), null, "空字节不是 PNG");
        eq(PngHeader.size(Arrays.copyOf(png(64, 48), 23)), null, "少一个字节就不够判");

        // 改后缀名的别的格式：签名对不上，解码之前就拦住
        byte[] zipBytes = new byte[64];
        zipBytes[0] = 'P';
        zipBytes[1] = 'K';
        eq(PngHeader.size(zipBytes), null, "PK 开头的 zip 改名成 .png 也不收");

        byte[] jpeg = new byte[64];
        jpeg[0] = (byte) 0xFF;
        jpeg[1] = (byte) 0xD8;
        eq(PngHeader.size(jpeg), null, "JPEG 改名成 .png 也不收");

        byte[] sigOnly = png(64, 48);
        sigOnly[3] = 'X';
        eq(PngHeader.size(sigOnly), null, "签名第 4 个字节错了就不收");

        byte[] notIhdr = png(64, 48);
        notIhdr[12] = 'I';
        notIhdr[13] = 'D';
        notIhdr[14] = 'A';
        notIhdr[15] = 'T';
        eq(PngHeader.size(notIhdr), null, "签名对但第一个块不是 IHDR，不收");

        eq(PngHeader.size(png(0, 48)), null, "宽 0 不收");
        eq(PngHeader.size(png(64, 0)), null, "高 0 不收");

        // PNG 的宽高是 31 位无符号；最高位置 1 的写法不合法，按有符号读正好是负数
        byte[] huge = png(64, 48);
        huge[16] = (byte) 0x80;
        eq(PngHeader.size(huge), null, "宽的最高位置 1 不收");

        eq(Arrays.toString(PngHeader.size(png(20000, 20000))), "[20000, 20000]",
           "天文数字照读 —— 拦它是上限那一关的事，头这一层只负责读得准");
    }

    // ============================================================
    //  路径：只判后缀与在不在包里，判据与 S2 的 NodeParser.imageSrc 一致
    // ============================================================

    static void paths() throws Exception {
        Fake fake = reset();
        AppPackage pkg = pkg(map(
                "assets/a.png", png(16, 16),
                "assets/sub/b.png", png(8, 8),
                "assets/d.txt", b("不是图"),
                "root.png", png(16, 16)));

        eq(AppTextures.resultOf(pkg, "assets/a.png"), AppTextures.Result.OK, "assets/ 下的 .png 收");
        eq(AppTextures.resultOf(pkg, "assets/sub/b.png"), AppTextures.Result.OK, "子目录照收");
        eq(AppTextures.resultOf(pkg, "root.png"), AppTextures.Result.OK,
           "根目录的 .png 也收：§11.2 的 assets/ 规则要么装包时拒，要么在 NodeParser 拒，不在这儿另判一次");
        eq(AppTextures.resultOf(pkg, "assets/d.txt"), AppTextures.Result.BAD_PATH, "后缀不是 png 就拒");
        eq(AppTextures.resultOf(pkg, "assets/A.PNG"), AppTextures.Result.BAD_PATH,
           "后缀按字面比，与 NodeParser.imageSrc 的 endsWith(\".png\") 同一条判据");
        eq(AppTextures.resultOf(pkg, "assets/没有这张.png"), AppTextures.Result.MISSING, "包里没有就拒");
        eq(AppTextures.resultOf(pkg, ""), AppTextures.Result.BAD_PATH, "空路径拒");
        eq(AppTextures.resultOf(pkg, "assets/a.png.txt"), AppTextures.Result.BAD_PATH, "双后缀按最后一段判");

        eq(AppTextures.size(null, "assets/a.png"), null, "没有包时没有尺寸，不抛");
        eq(AppTextures.size(pkg, null), null, "src 是 null 时没有尺寸，不抛");
        eq(AppTextures.of(null, null), null, "两个都是 null 也只是拿不到贴图");
        eq(AppTextures.sizes(null).size("assets/a.png"), null, "绑了个 null 包也不抛");
        eq(fake.uploads, 0, "判路径一张都没上传");
    }

    // ============================================================
    //  两道判定上限
    // ============================================================

    static void limits() throws Exception {
        Fake fake = reset();
        AppPackage pkg = pkg(map(
                "assets/ok.png", png(128, 128, 64 * 1024),
                "assets/wide.png", png(129, 128),
                "assets/tall.png", png(128, 129),
                "assets/fat.png", png(16, 16, 64 * 1024 + 1),
                "assets/fake.png", b("PK 这其实是个 zip，只是改了后缀而已，长度要够 24 个字节"),
                "assets/fatfake.png", fill(100 * 1024),
                "assets/short.png", new byte[]{(byte) 0x89, 'P', 'N', 'G'}));

        eq(AppTextures.resultOf(pkg, "assets/ok.png"), AppTextures.Result.OK, "128×128、整 64 KiB 正好收");
        eq(wh(pkg, "assets/ok.png"), "[128, 128]", "收下的图给得出原始尺寸");

        eq(AppTextures.resultOf(pkg, "assets/wide.png"), AppTextures.Result.TOO_BIG, "129 宽超边长");
        eq(AppTextures.resultOf(pkg, "assets/tall.png"), AppTextures.Result.TOO_BIG, "129 高超边长");
        eq(AppTextures.resultOf(pkg, "assets/fat.png"), AppTextures.Result.TOO_LARGE, "64 KiB 多一个字节就拒");
        eq(AppTextures.resultOf(pkg, "assets/fake.png"), AppTextures.Result.NOT_PNG, "假扩展名拒");
        eq(AppTextures.resultOf(pkg, "assets/fatfake.png"), AppTextures.Result.NOT_PNG,
           "又大又不是 PNG 的，报的是「不是 PNG」——先判格式再判字节数，不然会把人往错的方向指");
        eq(AppTextures.resultOf(pkg, "assets/short.png"), AppTextures.Result.NOT_PNG, "截断的 PNG 拒");

        for (String bad : List.of("assets/wide.png", "assets/tall.png", "assets/fat.png",
                                  "assets/fake.png", "assets/short.png")) {
            eq(AppTextures.size(pkg, bad), null, bad + " 拒了就没有原始尺寸");
            eq(AppTextures.of(pkg, bad), null, bad + " 拒了就没有贴图");
            check(!AppTextures.uploaded(pkg, bad), bad + " 拒了就没进显存");
            eq(AppTextures.sizes(pkg).size(bad), null, bad + " 在布局那一侧也没有");
        }
        eq(fake.uploads, 0, "被拒的一张都没送去上传");

        eq(AppTextures.MAX_BYTES, 64 * 1024, "单张上限 64 KiB（§11.2）");
        eq(AppTextures.MAX_SIDE, 128, "边长上限 128（§5.2）");
        eq(AppTextures.MAX_PER_APP, 16, "同时在显存里 16 张（§28.2 #13）");

        // 头过了、像素没过：判定就地改成 BROKEN，不是每帧重解一次
        Fake broken = reset();
        broken.fail = true;
        AppPackage p2 = pkg(map("assets/ok.png", png(16, 16)));
        int before = AppTextures.epoch();
        eq(AppTextures.resultOf(p2, "assets/ok.png"), AppTextures.Result.OK, "光看头是好的");
        eq(AppTextures.of(p2, "assets/ok.png"), null, "像素解不开就拿不到贴图");
        eq(AppTextures.resultOf(p2, "assets/ok.png"), AppTextures.Result.BROKEN, "判定改成 BROKEN");
        eq(AppTextures.epoch(), before + 1,
           "epoch 前进：measure 是按头里那个宽高留的位置，这张图没了要按占位尺寸重排一次");
        eq(AppTextures.of(p2, "assets/ok.png"), null, "再问还是没有");
        eq(broken.uploads, 1, "坏图只解一次，不是每帧一次");
        eq(AppTextures.size(p2, "assets/ok.png"), null, "BROKEN 之后尺寸也不给了");
        check(!AppTextures.uploaded(p2, "assets/ok.png"), "BROKEN 的图不占显存");
    }

    // ============================================================
    //  判定表封顶：只封用不了的那些，:src 可以是表达式，每次重排都能造出一批没见过的路径
    // ============================================================

    static void rejectedCap() throws Exception {
        Fake fake = reset();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) entries.put("assets/真" + i + ".png", png(8, 8));
        AppPackage pkg = pkg(entries);

        for (int i = 0; i < AppTextures.MAX_REJECTED; i++) {
            eq(AppTextures.resultOf(pkg, "assets/变" + i + ".png"), AppTextures.Result.MISSING,
               "包里没有这张，记一条");
        }
        eq(AppTextures.cachedEntries(), AppTextures.MAX_REJECTED, "用不了的记满了");

        eq(AppTextures.resultOf(pkg, "assets/再来一张.png"), AppTextures.Result.TOO_MANY, "满了就不再记");
        eq(AppTextures.cachedEntries(), AppTextures.MAX_REJECTED, "满了之后表不再涨");
        for (int i = 0; i < 500; i++) AppTextures.resultOf(pkg, "assets/洪水" + i + ".png");
        eq(AppTextures.cachedEntries(), AppTextures.MAX_REJECTED, "五百条也涨不动");

        // 封顶只封用不了的：包里真实存在的图一张都不许误伤。
        // 连 OK 一起封的话，一个装满图的大 App 只要先被问到几条失效路径，后面真实存在的图就没了
        for (int i = 0; i < 20; i++) {
            eq(AppTextures.resultOf(pkg, "assets/真" + i + ".png"), AppTextures.Result.OK,
               "第 " + (i + 1) + " 张真实存在的图照收，不受失效路径的连累");
        }
        eq(AppTextures.cachedEntries(), AppTextures.MAX_REJECTED + 20, "收下的那些不占封顶的额度");
        eq(fake.uploads, 0, "一张都没上传");

        AppTextures.release(pkg);
        eq(AppTextures.cachedEntries(), 0, "关页面之后表清空");
    }

    // ============================================================
    //  显存名额：一帧之内满了就画占位图，跨帧按最久没画的淘汰
    // ============================================================

    static void vram() throws Exception {
        Fake fake = reset();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) entries.put("assets/n" + i + ".png", png(8 + i, 8));
        AppPackage pkg = pkg(entries);

        // 尺寸不限量：一条 8 个字节，限它没有意义
        for (int i = 0; i < 20; i++) {
            eq(wh(pkg, "assets/n" + i + ".png"), "[" + (8 + i) + ", 8]", "第 " + (i + 1) + " 张的尺寸照给");
        }
        eq(fake.live, 0, "问尺寸不占显存");

        // 第一帧：画 16 张
        AppTextures.beginFrame(pkg);
        for (int i = 0; i < AppTextures.MAX_PER_APP; i++) AppTextures.of(pkg, "assets/n" + i + ".png");
        eq(fake.live, 16, "画了 16 张，显存里 16 张");
        eq(AppTextures.liveCount(pkg), 16, "表里也是 16 张");
        eq(fake.uploads, 16, "各传了一次");
        check(AppTextures.uploaded(pkg, "assets/n0.png"), "第一张在显存里");

        // 同一帧里的第 17 张：拒绝加载、画占位图（§28.2 #13），不许把刚画过的换出去
        AppTextures.of(pkg, "assets/n16.png");
        eq(fake.uploads, 16, "没传 —— 这一帧的额度用完了");
        eq(fake.releases, 0, "更没有把刚画过的还回去");
        check(!AppTextures.uploaded(pkg, "assets/n16.png"), "第 17 张这一帧画占位图");
        check(AppTextures.uploaded(pkg, "assets/n0.png"), "第一张还在");
        eq(wh(pkg, "assets/n16.png"), "[24, 8]", "拿不到贴图不影响尺寸：布局照原始尺寸留位置");
        eq(AppTextures.resultOf(pkg, "assets/n16.png"), AppTextures.Result.OK, "也不算被拒");

        // 下一帧：上一帧那 16 张不再画了（换了 tab / 滚走了），第 17 张就该上得来
        AppTextures.beginFrame(pkg);
        AppTextures.of(pkg, "assets/n16.png");
        check(AppTextures.uploaded(pkg, "assets/n16.png"), "换一帧就画得出来 —— 不是永久占位图");
        check(!AppTextures.uploaded(pkg, "assets/n0.png"), "被淘汰的是最久没画的 n0");
        eq(fake.live, 16, "还是 16 张，没涨");
        eq(fake.releases, 1, "还了一张");
        eq(wh(pkg, "assets/n0.png"), "[8, 8]", "被淘汰的那张，尺寸还在（布局不受影响）");
        eq(AppTextures.resultOf(pkg, "assets/n0.png"), AppTextures.Result.OK, "被淘汰不等于被拒");

        // 访问序：这一帧画过 n1，再上一张新的时候被淘汰的就该是 n2
        AppTextures.of(pkg, "assets/n1.png");
        eq(fake.uploads, 17, "n1 还在显存里，重画不重传");
        AppTextures.of(pkg, "assets/n17.png");
        check(AppTextures.uploaded(pkg, "assets/n1.png"), "这一帧画过的 n1 没被淘汰");
        check(!AppTextures.uploaded(pkg, "assets/n2.png"), "被淘汰的是最久没画的 n2");
        eq(AppTextures.liveCount(pkg), 16, "始终 16 张");

        // 被淘汰的那张再画到时重传
        AppTextures.of(pkg, "assets/n0.png");
        eq(fake.uploads, 19, "n0 被淘汰过，重画时重传");
        check(AppTextures.uploaded(pkg, "assets/n0.png"), "重传之后又画得出来了");
        eq(AppTextures.liveCount(pkg), 16, "还是 16 张");

        AppTextures.release(pkg);
        eq(fake.live, 0, "关页面全还回去");
        eq(fake.releases, fake.uploads, "传了多少还了多少，一张不差");

        // 一个装了 20 张图的 scroll：scroll 不剔除滚出可见区的子节点，裁掉的照样每帧问一遍。
        // 连画两帧，上传与归还都不许涨 —— 涨了就是每帧几十次解码加建删纹理
        Fake gallery = reset();
        AppPackage g = pkg(entries);
        for (int frame = 0; frame < 2; frame++) {
            AppTextures.beginFrame(g);
            for (int i = 0; i < 20; i++) AppTextures.of(g, "assets/n" + i + ".png");
            eq(AppTextures.liveCount(g), 16, "第 " + frame + " 帧里显存里 16 张");
        }
        eq(gallery.uploads, 16, "两帧一共只传了 16 张");
        eq(gallery.releases, 0, "一张都没换出去 —— 多出来的四张每帧都画占位图，稳定，不闪");
        check(AppTextures.uploaded(g, "assets/n0.png"), "赢的是画得早的那批（树序），每帧同一批");
        check(!AppTextures.uploaded(g, "assets/n19.png"), "输的也是同一批");
    }

    // ============================================================
    //  缓存与释放（§8.8：装卸 200 次不涨）
    // ============================================================

    static void cacheAndRelease() throws Exception {
        Fake fake = reset();
        AppPackage pkg = pkg(map("assets/a.png", png(16, 16), "assets/b.png", png(32, 8)));

        AppTextures.of(pkg, "assets/a.png");
        AppTextures.of(pkg, "assets/a.png");
        AppTextures.of(pkg, "assets/a.png");
        eq(fake.uploads, 1, "同一张图只上传一次");
        eq(fake.live, 1, "显存里一张");

        AppTextures.size(pkg, "assets/b.png");
        eq(fake.uploads, 1, "只问尺寸不上传 —— 一页里没露出来的图不占显存");
        AppTextures.of(pkg, "assets/b.png");
        eq(fake.uploads, 2, "画到了才上传");
        eq(fake.live, 2, "显存里两张");

        AppTextures.release(pkg);
        eq(fake.live, 0, "关页面还回去了");
        eq(fake.releases, 2, "两张都还了");
        eq(AppTextures.cachedEntries(), 0, "判定表也清了");

        AppTextures.release(pkg);
        eq(fake.releases, 2, "再释放一次不重复还账");
        AppTextures.release(null);
        eq(fake.live, 0, "释放 null 不抛");

        // 装卸 200 次：显存与表都不许涨。第一轮与最后一轮顺带问一张用不了的，
        // 确认被拒的那条也不会攒下来（中间不问，免得往构建日志里灌两百行 warn）
        Fake cycle = reset();
        AppPackage p = pkg(map("assets/a.png", png(16, 16), "assets/b.png", png(8, 8),
                               "assets/bad.png", png(200, 200)));
        for (int i = 0; i < 200; i++) {
            AppTextures.of(p, "assets/a.png");
            AppTextures.of(p, "assets/b.png");
            if (i == 0 || i == 199) AppTextures.of(p, "assets/bad.png");
            check(cycle.live == 2, "第 " + i + " 轮里显存里正好两张");
            AppTextures.release(p);
            eq(cycle.live, 0, "第 " + i + " 轮关掉之后显存是空的");
            eq(AppTextures.cachedEntries(), 0, "第 " + i + " 轮关掉之后判定表是空的");
        }
        eq(cycle.uploads, 400, "两百轮各传两张，没有多出来的");
        eq(cycle.releases, 400, "传了多少还了多少");
    }

    // ============================================================
    //  两个 App 各算各的
    // ============================================================

    static void twoApps() throws Exception {
        Fake fake = reset();
        AppPackage a = pkg(map("assets/a.png", png(16, 16)));
        AppPackage b = pkg(map("assets/a.png", png(16, 16), "assets/b.png", png(8, 8)));
        check(!a.digest().equals(b.digest()), "两个包的摘要不同");

        AppTextures.of(a, "assets/a.png");
        AppTextures.of(b, "assets/a.png");
        AppTextures.of(b, "assets/b.png");
        eq(fake.live, 3, "两个包各自的贴图都在");

        AppTextures.release(a);
        eq(fake.live, 2, "关掉 A 只还 A 的");
        check(AppTextures.uploaded(b, "assets/a.png"), "B 的同名图不受影响");
        check(!AppTextures.uploaded(a, "assets/a.png"), "A 的还回去了");

        // 缓存键是包摘要：内容逐字节相同的两个 AppPackage 共用一份判定与贴图
        AppPackage sameAsB = pkg(map("assets/a.png", png(16, 16), "assets/b.png", png(8, 8)));
        eq(sameAsB.digest(), b.digest(), "内容一样，摘要就一样");
        check(AppTextures.uploaded(sameAsB, "assets/a.png"),
              "换个 AppPackage 实例装进来，贴图不必重传");
        AppTextures.of(sameAsB, "assets/a.png");
        eq(fake.uploads, 3, "确实没重传");
        AppTextures.release(sameAsB);
        eq(fake.live, 0, "摘要相同就是同一份，没有引用计数：谁关都是全还");
    }

    // ============================================================
    //  资源重载
    // ============================================================

    static void reload() throws Exception {
        Fake fake = reset();
        AppPackage pkg = pkg(map("assets/a.png", png(16, 16)));

        int before = AppTextures.epoch();
        AppTextures.of(pkg, "assets/a.png");
        eq(AppTextures.epoch(), before, "只是画图，epoch 不动");
        AppTextures.release(pkg);
        eq(AppTextures.epoch(), before, "关页面也不动 —— 页面都关了，没人需要重排");

        AppTextures.of(pkg, "assets/a.png");
        AppTextures.clearCache();
        eq(AppTextures.epoch(), before + 1, "重载之后 epoch 前进一格（§7.6 的四个 epoch 之一）");
        eq(fake.live, 0, "重载把显存还回去了");
        eq(AppTextures.cachedEntries(), 0, "判定表也清了");

        AppTextures.of(pkg, "assets/a.png");
        eq(fake.uploads, 3, "重载之后重新上传一份");
        eq(wh(pkg, "assets/a.png"), "[16, 16]", "尺寸重新读得出来");

        int now = AppTextures.epoch();
        AppTextures.clearCache();
        AppTextures.clearCache();
        eq(AppTextures.epoch(), now + 2, "连着重载两次就是两格");
    }

    // ============================================================
    //  measure 与 render 问的是同一张表
    // ============================================================

    static void bothSides() throws Exception {
        Fake fake = reset();
        AppPackage pkg = pkg(map("assets/ok.png", png(24, 12), "assets/bad.png", png(200, 200)));
        ImageSizes bound = AppTextures.sizes(pkg);

        eq(Arrays.toString(bound.size("assets/ok.png")), "[24, 12]", "布局那一侧读得出原始尺寸");
        eq(Arrays.toString(AppTextures.size(pkg, "assets/ok.png")), "[24, 12]", "两个入口同一个答案");
        AppTextures.of(pkg, "assets/ok.png");
        check(AppTextures.uploaded(pkg, "assets/ok.png"), "画得出来");

        eq(bound.size("assets/bad.png"), null, "用不了的图，布局这边没有尺寸");
        eq(AppTextures.of(pkg, "assets/bad.png"), null, "render 那边也没有贴图");
        check(!AppTextures.uploaded(pkg, "assets/bad.png"), "也没进显存");
        eq(fake.uploads, 1, "只传了能用的那张");
    }

    // ============================================================
    //  小工具
    // ============================================================

    /** 假上传口：数上传与归还，不碰显存。 */
    static final class Fake implements AppTextures.Uploader {
        int uploads;
        int releases;
        int live;
        /** true 时模拟"头是好的、像素解不开" */
        boolean fail;

        @Override
        public ImageCodec.Texture upload(byte[] png) {
            uploads++;
            if (fail) return null;
            live++;
            // location 给 null：docs/ 三个目标共编一份，ResourceLocation 的构造在 1.20.1 与 1.21.1 上不同名
            return new ImageCodec.Texture(null, 1, 1);
        }

        @Override
        public void release(ImageCodec.Texture texture) {
            if (texture == null) return;
            releases++;
            live--;
        }
    }

    /** 换上一个干净的假上传口，顺带把上一节留下的表清空。 */
    static Fake reset() {
        Fake fake = new Fake();
        AppTextures.uploader(fake);
        return fake;
    }

    static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** 原始尺寸的可读形式，拿不到时是 "null"。 */
    static String wh(AppPackage pkg, String src) {
        return Arrays.toString(AppTextures.size(pkg, src));
    }

    /** 只有头是真的 PNG：判定看的就是这 24 个字节，像素在这套测试里没人解。 */
    static byte[] png(int w, int h) {
        return png(w, h, 64);
    }

    static byte[] png(int w, int h, int totalBytes) {
        byte[] out = fill(Math.max(24, totalBytes));
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        System.arraycopy(sig, 0, out, 0, sig.length);
        put32(out, 8, 13);
        out[12] = 'I';
        out[13] = 'H';
        out[14] = 'D';
        out[15] = 'R';
        put32(out, 16, w);
        put32(out, 20, h);
        return out;
    }

    /** 不可压缩的噪声：全是 0 的话 zip 压出 600:1，S1 的压缩比闸（100:1）先把包拒了。 */
    static byte[] fill(int n) {
        byte[] out = new byte[n];
        long seed = 0x5DEECE66DL;
        for (int i = 0; i < n; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            out[i] = (byte) (seed >>> 33);
        }
        return out;
    }

    /** 一张真能解开的 PNG：IHDR + IDAT + IEND，CRC 都对。头那几个字节之外也得有东西是真的。 */
    static byte[] realPng(int w, int h) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

        byte[] ihdr = new byte[13];
        put32(ihdr, 0, w);
        put32(ihdr, 4, h);
        ihdr[8] = 8;        // 位深
        ihdr[9] = 6;        // RGBA
        chunk(out, "IHDR", ihdr);

        // 每行前面一个过滤器字节，然后是 w 个 RGBA 像素
        byte[] raw = new byte[h * (1 + w * 4)];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w * 4; x++) raw[y * (1 + w * 4) + 1 + x] = (byte) (x * 37 + y * 11);
        }
        Deflater deflater = new Deflater();
        deflater.setInput(raw);
        deflater.finish();
        byte[] buf = new byte[raw.length + 64];
        int n = deflater.deflate(buf);
        deflater.end();
        chunk(out, "IDAT", Arrays.copyOf(buf, n));
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] len = new byte[4];
        put32(len, 0, data.length);
        out.writeBytes(len);
        byte[] typed = type.getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(typed);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(typed);
        crc.update(data);
        byte[] c = new byte[4];
        put32(c, 0, (int) crc.getValue());
        out.writeBytes(c);
    }

    static void put32(byte[] out, int off, int v) {
        out[off] = (byte) (v >>> 24);
        out[off + 1] = (byte) (v >>> 16);
        out[off + 2] = (byte) (v >>> 8);
        out[off + 3] = (byte) v;
    }

    static Map<String, byte[]> map(Object... kv) {
        Map<String, byte[]> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (byte[]) kv[i + 1]);
        return m;
    }

    /** 走真的 PackageReader 造一个包：判定读的是 AppPackage.entry，别的路子造不出同样的东西。 */
    static AppPackage pkg(Map<String, byte[]> assets) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", b(MANIFEST));
        entries.put("ui.json", b("{\"type\":\"column\"}"));
        entries.put("ui.mss", b(".x { color: #fff; }"));
        entries.put("icon.png", png(16, 16));
        entries.putAll(assets);
        return PackageReader.read(zip(entries));
    }

    static final String MANIFEST = "{"
            + "\"format\":1,"
            + "\"id\":\"example:tex\","
            + "\"version\":\"1.0.0\","
            + "\"name\":\"贴图\","
            + "\"author\":\"yumeka\","
            + "\"description\":\"测贴图用的包\","
            + "\"icon\":\"icon.png\","
            + "\"ui\":{\"tree\":\"ui.json\",\"style\":\"ui.mss\"},"
            + "\"engine\":\"declarative-1\"}";

    static byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
