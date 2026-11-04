package com.november.mcphone.feature.chat.client;

/** 消息滚动的纯状态。零表示贴底，翻历史时新增内容不能把视野挤走。 */
final class ChatScrollState {
    private static final int STEP = 18;
    private int contentHeight;
    private int offset;
    private int limit;

    int contentHeight() { return contentHeight; }
    int offset() { return offset; }

    void contentChanged(int height) {
        if (offset > 0 && height > contentHeight) offset += height - contentHeight;
        contentHeight = Math.max(0, height);
    }

    void viewport(int height) {
        limit = Math.max(0, contentHeight - Math.max(0, height));
        offset = Math.max(0, Math.min(offset, limit));
    }

    boolean scroll(double amount) {
        if (limit <= 0) return false;
        offset = Math.max(0, Math.min(offset + (int) (amount * STEP), limit));
        return true;
    }

    void latest() { offset = 0; }
    /** 포키: 답장 원문으로 이동 (다음 viewport 에서 범위로 맞춰짐) */
    void set(int value) { offset = Math.max(0, Math.min(value, limit)); }
    void reset() { contentHeight = offset = limit = 0; }
}
