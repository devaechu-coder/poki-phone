package com.november.mcphone.feature.settings.client.changelog;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 支持发布说明中的 Markdown 链接；链接另起可点击行，长标题照常换行。 */
public final class ChangelogText {
    private ChangelogText() {}
    private static final Pattern LINK = Pattern.compile("\\[([^]\\r\\n]+)]\\((https?://[^\\s)]+)\\)");
    public record Link(String label, String url) {}
    public record Entry(String text, List<Link> links) {}
    public static Entry parse(String source) {
        var matcher = LINK.matcher(source);
        var links = new ArrayList<Link>();
        var text = new StringBuffer();
        while (matcher.find()) {
            String url = matcher.group(2);
            try {
                URI uri = URI.create(url);
                if (uri.getHost() != null && uri.getUserInfo() == null) links.add(new Link(matcher.group(1), url));
            } catch (IllegalArgumentException ignored) { }
            matcher.appendReplacement(text, java.util.regex.Matcher.quoteReplacement(matcher.group(1)));
        }
        matcher.appendTail(text);
        return new Entry(text.toString(), List.copyOf(links));
    }
}
