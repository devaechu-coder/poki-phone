package com.november.mcphone.core.script.sfc;

import com.november.mcphone.core.script.sfc.SfcError.Code;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 顶层块切分（施工方案 §9.3）。逐行扫，块标签必须独占一行、从第 1 列开始、前后没有别的字符。
 *
 * <p>不用 HTML 解析器：{@code </script>} 可以出现在 JS 字符串里，靠「独占一行且顶格」一条规则就分得开，报错位置也准。
 * 代价是块里顶格写一整行 {@code </script>} 会被当成闭标签，缩进一格即可。
 */
public final class SfcSplitter {

    public static final Set<String> BLOCKS = Set.of("manifest", "template", "script", "style");

    private static final Pattern TAG = Pattern.compile("</?([a-z][a-z0-9-]*)>");
    /** 顶格、写歪了的块标签：尖括号里多了空白，或者后面跟了空白。 */
    private static final Pattern LOOSE = Pattern.compile("<\\s*/?\\s*([a-z][a-z0-9-]*)\\s*>");
    private static final char BOM = (char) 0xFEFF;

    private SfcSplitter() {
    }

    /** 单文件形态（§11.2 形态一）：四个块里 manifest 与 template 必须有。 */
    public static Map<String, Block> split(String src) throws SfcError {
        return split(src, true);
    }

    /**
     * @param requireManifest 要不要有 {@code <manifest>} 块。zip 形态（§11.2 形态二）的 app.vue 与 pages/*.vue
     *                        没有这个块 —— 清单是包里独立的 manifest.json，写在每个页面里只会有两份、还可能互相矛盾
     */
    public static Map<String, Block> split(String src, boolean requireManifest) throws SfcError {
        Objects.requireNonNull(src, "src");
        if (!src.isEmpty() && src.charAt(0) == BOM) src = src.substring(1);
        String[] lines = src.split("\n", -1);
        Map<String, Block> out = new LinkedHashMap<>();
        String open = null;
        int openLine = -1;
        StringBuilder buf = null;

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            if (raw.endsWith("\r")) raw = raw.substring(0, raw.length() - 1);
            Matcher m = TAG.matcher(raw);
            boolean tag = m.matches();

            if (open != null) {
                // 顶格的块标签后面跟了空白：编辑器留下的尾随空格最常见，当成内容会报成「没有闭合」且指错行
                if (!tag && !raw.isEmpty() && raw.charAt(0) == '<') {
                    Matcher loose = LOOSE.matcher(raw.stripTrailing());
                    if (loose.matches() && BLOCKS.contains(loose.group(1))) {
                        throw SfcError.at(Code.E_SFC_BLOCK_FORMAT, i + 1, 1);
                    }
                }
                // 块里顶格的 <column> 是模板内容，只有四个块名才算块标签；缩进的块标签也是内容（§9.3 的代价）
                if (!tag || !BLOCKS.contains(m.group(1))) {
                    buf.append(raw).append('\n');
                    continue;
                }
                String name = m.group(1);
                if (raw.charAt(1) != '/') throw SfcError.at(Code.E_SFC_NESTED_BLOCK, i + 1, 1, open, name);
                if (!name.equals(open)) throw SfcError.at(Code.E_SFC_MISMATCH, i + 1, 1, open, name);
                out.put(open, new Block(open, openLine + 2, buf.toString()));
                open = null;
                buf = null;
                continue;
            }

            if (raw.isBlank()) continue;
            if (tag) {
                String name = m.group(1);
                if (raw.charAt(1) == '/') throw SfcError.at(Code.E_SFC_UNEXPECTED_CLOSE, i + 1, 1, name);
                if (!BLOCKS.contains(name)) throw SfcError.at(Code.E_SFC_UNKNOWN_BLOCK, i + 1, 1, name);
                if (out.containsKey(name)) throw SfcError.at(Code.E_SFC_DUP_BLOCK, i + 1, 1, name);
                open = name;
                openLine = i;
                buf = new StringBuilder();
            } else if (looksLikeTag(raw)) {
                throw SfcError.at(Code.E_SFC_BLOCK_FORMAT, i + 1, 1);
            } else {
                throw SfcError.at(Code.E_SFC_STRAY_TEXT, i + 1, 1);
            }
        }
        // 报开标签那一行：跳到文件末尾看不出是哪个块没收尾
        if (open != null) throw SfcError.at(Code.E_SFC_UNCLOSED_BLOCK, openLine + 1, 1, open);
        if (requireManifest && !out.containsKey("manifest")) throw SfcError.at(Code.E_SFC_NO_MANIFEST, 1, 0);
        if (!out.containsKey("template")) throw SfcError.at(Code.E_SFC_NO_TEMPLATE, 1, 0);
        return out;
    }

    /** 缩进了、同一行还有别的东西的块标签：报格式错比报「块外有内容」更快指到问题。注释不算。 */
    private static boolean looksLikeTag(String raw) {
        String t = raw.strip();
        if (t.length() < 2 || t.charAt(0) != '<') return false;
        char c = t.charAt(1);
        return c == '/' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
}
