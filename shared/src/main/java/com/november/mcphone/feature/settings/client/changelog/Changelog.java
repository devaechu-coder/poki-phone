package com.november.mcphone.feature.settings.client.changelog;

import com.google.gson.Gson;
import java.io.Reader;
import java.util.List;
import java.util.Map;

/** 构建生成的只读日志。顺序由构建确定，运行时不比较版本大小。 */
public final class Changelog {
    private Changelog() {}
    public record Section(String title, List<String> entries) {}
    public record Release(String version, String date, Map<String, List<Section>> languages) {
        public List<Section> sections(String language) {
            return languages.getOrDefault("zh_cn".equals(language) ? "zh_cn" : "en_us", List.of());
        }
    }
    public static List<Release> read(Reader reader) {
        Release[] values = new Gson().fromJson(reader, Release[].class);
        if (values == null) throw new IllegalArgumentException("缺少更新日志列表");
        for (Release release : values) {
            if (release == null || release.version() == null || release.date() == null || release.languages() == null)
                throw new IllegalArgumentException("无效的更新日志");
            for (String language : List.of("zh_cn", "en_us")) {
                List<Section> sections = release.languages().get(language);
                if (sections == null || sections.isEmpty()) throw new IllegalArgumentException("缺少日志翻译");
                for (Section section : sections) {
                    if (section == null || section.title() == null || section.entries() == null
                            || section.entries().isEmpty() || section.entries().contains(null))
                        throw new IllegalArgumentException("无效的日志分类");
                }
            }
        }
        return List.of(values);
    }
    public static Release current(List<Release> releases, String version) {
        // 预发布与正式版共用同一基准号的正文；预发布不自动提醒。
        String base = version.split("-", 2)[0];
        return releases.stream().filter(r -> r.version().equals(base)).findFirst().orElse(null);
    }
    public static boolean shouldNotify(String version, String shown, boolean hasNotes) {
        return hasNotes && version.matches("\\d+\\.\\d+\\.\\d+") && !version.equals(shown);
    }
}
