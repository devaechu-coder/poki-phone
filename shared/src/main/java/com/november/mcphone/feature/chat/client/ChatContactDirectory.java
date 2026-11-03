package com.november.mcphone.feature.chat.client;

import com.ibm.icu.text.AlphabeticIndex;
import com.ibm.icu.text.Collator;
import com.ibm.icu.util.ULocale;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** 纯数据目录：过滤、拼音排序和字母分组共用同一快照，不依赖网络或绘制。 */
final class ChatContactDirectory {
    static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#";
    record Contact(UUID id, String name, boolean online) {}
    record Section(String letter, List<Contact> contacts) {
        Section { contacts = List.copyOf(contacts); }
    }
    // 使用 Minecraft 自带 ICU。固定拼音规则，不能随操作系统语言改变排列或用 GBK 字节猜首字母。
    private final AlphabeticIndex.ImmutableIndex<Object> alphabet;
    private final Collator collator;

    ChatContactDirectory() {
        var builder = new AlphabeticIndex<Object>(new ULocale("zh@collation=pinyin"))
                .addLabels(Locale.ENGLISH);
        alphabet = builder.buildImmutableIndex();
        collator = builder.getCollator();
    }

    String letter(String name) {
        String label = alphabet.getBucket(alphabet.getBucketIndex(displayKey(name))).getLabel();
        return label.length() == 1 && label.charAt(0) >= 'A' && label.charAt(0) <= 'Z' ? label : "#";
    }

    List<Section> sections(List<Contact> source, String query) {
        String needle = searchKey(query);
        var sorted = new ArrayList<Contact>();
        for (Contact contact : source) {
            if (searchKey(contact.name()).contains(needle)) sorted.add(contact);
        }
        sorted.sort(Comparator.comparingInt((Contact c) -> LETTERS.indexOf(letter(c.name())))
                .thenComparing(Contact::name, (a, b) -> collator.compare(displayKey(a), displayKey(b)))
                .thenComparing(Contact::id));
        var sections = new ArrayList<Section>();
        for (int start = 0; start < sorted.size();) {
            String label = letter(sorted.get(start).name());
            int end = start + 1;
            while (end < sorted.size() && letter(sorted.get(end).name()).equals(label)) end++;
            sections.add(new Section(label, sorted.subList(start, end)));
            start = end;
        }
        return List.copyOf(sections);
    }

    private static String displayKey(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).strip();
    }
    static boolean matches(String name, String query) { return searchKey(name).contains(searchKey(query)); }
    private static String searchKey(String value) { return displayKey(value).toLowerCase(Locale.ROOT); }
}
