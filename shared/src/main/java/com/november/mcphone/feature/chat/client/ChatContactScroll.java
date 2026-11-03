package com.november.mcphone.feature.chat.client;

import java.util.ArrayList;
import java.util.List;

/** 不持有控件的列表几何；滚轮和字母跳转都受同一视口约束。 */
final class ChatContactScroll {
    static final int HEADER_HEIGHT = 8, CONTACT_HEIGHT = 19;
    record Row(String letter, ChatContactDirectory.Contact contact, int top, int height) {
        boolean heading() { return contact == null; }
    }
    private List<Row> rows = List.of();
    private int contentHeight, offset;
    List<Row> rows() { return rows; }
    int offset() { return offset; }
    void reset() { offset = 0; }
    void clear() { rows = List.of(); contentHeight = offset = 0; }
    void update(List<ChatContactDirectory.Section> sections, int viewportHeight) {
        var next = new ArrayList<Row>();
        int y = 0;
        for (var section : sections) {
            next.add(new Row(section.letter(), null, y, HEADER_HEIGHT));
            y += HEADER_HEIGHT;
            for (var contact : section.contacts()) {
                next.add(new Row(section.letter(), contact, y, CONTACT_HEIGHT));
                y += CONTACT_HEIGHT;
            }
        }
        rows = List.copyOf(next);
        contentHeight = y;
        clamp(viewportHeight);
    }
    boolean scroll(double amount, int viewportHeight) {
        if (amount == 0) return false;
        int before = offset;
        offset += amount > 0 ? -CONTACT_HEIGHT : CONTACT_HEIGHT;
        clamp(viewportHeight);
        return offset != before;
    }
    boolean jump(String letter, int viewportHeight) {
        for (var row : rows) {
            if (row.heading() && row.letter().equals(letter)) {
                offset = row.top(); clamp(viewportHeight); return true;
            }
        }
        return false;
    }
    String firstLetter() {
        for (var row : rows) if (row.top() + row.height() > offset) return row.letter();
        return "";
    }
    private void clamp(int viewportHeight) {
        offset = Math.max(0, Math.min(offset, Math.max(0, contentHeight - Math.max(0, viewportHeight))));
    }
}
