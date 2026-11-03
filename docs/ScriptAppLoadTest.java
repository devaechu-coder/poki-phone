package com.november.mcphone.core.script.client;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 脚本 App 从文件到装好的那一段（施工方案 §14.1、§11.2）：两种形态怎么读、坏的怎么拒。
 *
 * <p>只测读与编，不测扫目录 —— 那一步要游戏目录。装上之后长什么样、点起来对不对，
 * 只能在游戏里看（§14.6）。
 *
 * <p>跑法：{@code ./gradlew assertTests}（在 {@code platforms/<目标名>/} 下）。要 Minecraft 的类路径。
 */
public class ScriptAppLoadTest {

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

    /** 读得出来就返回 null，读不出来返回那句话。 */
    static String reject(String name, byte[] content) {
        try {
            ScriptAppFolder.read(name, content);
            return null;
        } catch (RuntimeException e) {
            return String.valueOf(e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        singleFile();
        zipPackage();
        rejections();

        System.out.println("断言 " + checks + " 条");
        if (!failures.isEmpty()) {
            System.out.println("失败 " + failures.size() + " 条：");
            for (String f : failures) System.out.println("  - " + f);
            System.exit(1);
        }
        System.out.println("全部通过");
    }

    // ============================================================
    //  形态一：单个 .vue
    // ============================================================

    static void singleFile() {
        ScriptApp app = ScriptAppFolder.read("counter.vue", VUE.getBytes(StandardCharsets.UTF_8));

        eq(app.id().toString(), "example:counter", "id 从内联清单来");
        eq(app.manifest().version(), "1.0.0", "版本");
        eq(app.manifest().author(), "yumeka", "作者");
        eq(app.manifest().name(), "计数器", "名字");
        eq(app.pkg(), null, "单文件形态没有包 —— 也就没有素材，image 一律画占位图");
        eq(app.pages().size(), 0, "只有入口一页");
        eq(app.file(), "counter.vue", "记着从哪个文件来的");
        check(app.entry() != null, "入口页编出来了");
        eq(app.page(""), app.entry(), "空串就是入口页");
        eq(app.page("detail"), null, "没有别的页");

        eq(app.entry().template().initialState().get("n"), 0, "script 块里的 state 编进产物了");
        check(app.entry().stylesheet().ruleCount() > 0, "<style> 块编进样式表了");

        // 图标：data URI 解成字节
        check(app.icon() != null, "内联图标解出来了");
        check(app.icon().length > 0 && (app.icon()[0] & 0xFF) == 0x89, "解出来的是 PNG 的头一个字节");
        eq(app.icon().length, Base64.getDecoder().decode(ICON_BASE64).length, "长度与原图一致");

        // 不写 icon 也能装
        ScriptApp noIcon = ScriptAppFolder.read("a.vue", b(vue("")));
        eq(noIcon.icon(), null, "不写图标就是没有，商店画占位方块");
    }

    // ============================================================
    //  形态二：zip
    // ============================================================

    static void zipPackage() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", b(ZIP_MANIFEST));
        entries.put("app.vue", b(PAGE_VUE));
        entries.put("pages/detail.vue", b(PAGE_VUE));
        entries.put("pages/about.vue", b(PAGE_VUE));
        entries.put("pages/sub/deep.vue", b(PAGE_VUE));
        entries.put("assets/icon.png", png());
        ScriptApp app = ScriptAppFolder.read("my-app.zip", zip(entries));

        eq(app.id().toString(), "example:notice", "id 从 manifest.json 来");
        check(app.pkg() != null, "zip 形态带着包 —— image 与图标都要看它");
        check(app.entry() != null, "app.vue 是入口（不写 ui 时的默认）");
        eq(new java.util.TreeSet<>(app.pages().keySet()).toString(), "[about, detail]",
           "pages/ 下的 .vue 各是一页，nav('detail') 找得到");
        eq(app.page("sub/deep"), null, "pages/ 下再套目录的不算一页 —— nav 也写不出这种名字");
        eq(app.page("detail"), app.pages().get("detail"), "按名字取得到");
        eq(app.page(""), app.entry(), "空串仍是入口页");
        check(app.icon() != null && app.icon().length == png().length, "图标是包里那张 png");

        // 写了 ui 的老形态：入口仍然只认 .vue
        Map<String, byte[]> old = new LinkedHashMap<>(entries);
        old.put("manifest.json", b(ZIP_MANIFEST.replace("\"engine\"",
                "\"ui\":{\"tree\":\"ui.json\",\"style\":\"ui.mss\"},\"engine\"")));
        old.put("ui.json", b("{\"type\":\"column\"}"));
        old.put("ui.mss", b(".x{}"));
        byte[] oldZip = zip(old);
        String why = reject("old.zip", oldZip);
        check(why != null && why.contains(".vue"), "入口是 ui.json 的包读不了，理由说的是 .vue：" + why);
    }

    // ============================================================
    //  坏的怎么拒（§14.1：跳过 + 记日志，不影响别的包）
    // ============================================================

    static void rejections() throws Exception {
        check(reject("bad.vue", b("<template>\n<column/>\n</template>")) != null,
              "单文件缺 <manifest> 块，拒");
        check(reject("bad.vue", b(VUE.replace("<column>", "<colunm>"))) != null, "模板里的错，拒");
        check(reject("bad.vue", b("")) != null, "空文件，拒");
        check(reject("bad.zip", b("这不是 zip")) != null, "坏 ZIP，拒");

        Map<String, byte[]> noEntry = new LinkedHashMap<>();
        noEntry.put("manifest.json", b(ZIP_MANIFEST));
        noEntry.put("assets/icon.png", png());
        check(reject("x.zip", zip(noEntry)) != null, "包里没有 app.vue，拒");

        // 一个包坏了，另一个照常读得出来
        ScriptApp good = ScriptAppFolder.read("counter.vue", b(VUE));
        eq(good.id().toString(), "example:counter", "坏包不影响好包");
    }

    // ============================================================
    //  素材
    // ============================================================

    /** 24 字节的 PNG 头，判定看的就是这些。 */
    static byte[] png() {
        byte[] out = new byte[64];
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        System.arraycopy(sig, 0, out, 0, sig.length);
        out[8] = 0; out[9] = 0; out[10] = 0; out[11] = 13;
        out[12] = 'I'; out[13] = 'H'; out[14] = 'D'; out[15] = 'R';
        out[19] = 16;   // 宽 16
        out[23] = 16;   // 高 16
        for (int i = 24; i < out.length; i++) out[i] = (byte) (i * 31);
        return out;
    }

    static final String ICON_BASE64 = Base64.getEncoder().encodeToString(png());

    /** iconField 是清单里 icon 那一行（含逗号），空串就是不写图标。 */
    static String vue(String iconField) {
        return """
                <manifest>
                { "format": 1, "id": "example:counter", "version": "1.0.0",
                  "name": "计数器", "author": "yumeka"%s }
                </manifest>
                <template>
                <column>
                  <text>{{ n }}</text>
                  <button @click="n = n + 1">加一</button>
                </column>
                </template>
                <script>
                state = { n: 0 }
                </script>
                <style>
                .x { color: $title; }
                </style>
                """.formatted(iconField);
    }

    static final String VUE = vue(", \"icon\": \"data:image/png;base64," + ICON_BASE64 + "\"");

    /** zip 里的页：没有 <manifest> 块（§11.2 形态二） */
    static final String PAGE_VUE = """
            <template>
            <column>
              <text>公告</text>
            </column>
            </template>
            """;

    static final String ZIP_MANIFEST = """
            {"format":1,"id":"example:notice","version":"1.0.0","name":"公告","author":"yumeka",\
            "description":"看看今天有什么事","icon":"assets/icon.png","engine":"declarative-1"}""";

    static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

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
