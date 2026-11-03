package com.november.mcphone.feature.chat.group;

/** 存档、协议和界面共同使用的群聊边界，不由客户端自行扩大。 */
public final class GroupLimits {
    private GroupLimits() {}
    public static final int MEMBERS = 128;
    public static final int GROUPS_PER_PLAYER = 32;
    public static final int GROUPS_PER_WORLD = 4096;
    public static final int MESSAGES = 100;
    public static final int NAME_LENGTH = 32;
    /** 固定协议容量，区别于实际每帧下载量和每群保留的图片数。 */
    public static final int IMAGE_REQUEST_IDS = 20;
    public static final int INVITATIONS_PER_PLAYER = 64;
    public static final int INVITATIONS_PER_WORLD = 8192;
    public static final long INVITATION_LIFETIME_MS = 7L * 24 * 60 * 60 * 1000;
}
