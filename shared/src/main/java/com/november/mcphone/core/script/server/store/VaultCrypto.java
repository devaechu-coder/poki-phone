package com.november.mcphone.core.script.server.store;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * 保险箱的密码学构造（施工方案 §17.4.1）。<b>纯 JDK，不碰 Minecraft，也不引第三方。</b>
 *
 * <h2>威胁模型，先说清楚</h2>
 *
 * 防的是<b>服主读取明文</b>：翻存档、翻日志、翻内存转储都读不出来。
 * <b>不防</b>：服主删除数据、服主拒绝服务、服主篡改（篡改会被 GCM 发现，但数据仍然没了）。
 * 回滚要另外一层，见 {@link VaultClient}。
 *
 * <h2>构造表（§17.4.1，一条都不许省）</h2>
 *
 * <pre>
 * 口令 → key = PBKDF2-HMAC-SHA256(password, salt, 600_000, 256 bit)
 * 密文       = AES-256-GCM(key, nonce12, pad64(plaintext), AAD)
 * AAD        = serverId | playerUUID | appId | key | schemaVersion | recordVersion
 * </pre>
 *
 * <ul>
 *   <li><b>salt 16 字节随机，存服务端</b> —— salt 不是秘密，跨设备要能取回</li>
 *   <li><b>nonce 12 字节随机，绝不重用</b> —— GCM 上重用 nonce 等于泄漏密钥流</li>
 *   <li><b>AAD 绑定六个字段</b> —— 服主没法把 A 的密文塞给 B，也没法张冠李戴</li>
 *   <li><b>明文补齐到 64 字节的倍数</b> —— 否则长度泄漏信息（token 长度约等于"哪家服务商"）</li>
 * </ul>
 */
public final class VaultCrypto {

    private VaultCrypto() {
    }

    /** PBKDF2 轮数（§17.4.1）。实测 600k 轮约 180 毫秒。<b>只许增，不许减。</b> */
    public static final int KDF_ITERATIONS = 600_000;

    /** 派生密钥长度，位。 */
    public static final int KEY_BITS = 256;

    /** salt 长度，字节。 */
    public static final int SALT_BYTES = 16;

    /** nonce 长度，字节。GCM 的标准长度，换别的会让实现走慢路径。 */
    public static final int NONCE_BYTES = 12;

    /** GCM 认证标签长度，位。 */
    public static final int TAG_BITS = 128;

    /** 明文补齐到它的倍数（§17.4.3 缓解长度泄漏）。 */
    public static final int PAD_BLOCK = 64;

    /** 口令最少多长（§17.4.4）。 */
    public static final int MIN_PASSPHRASE = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 一条密文记录：nonce 与密文一起存，salt 与版本在外层（{@link SealedRecord}）。 */
    public record Sealed(byte[] nonce, byte[] cipher) {
    }

    // ---------------------------------------------------------------- 随机

    public static byte[] newSalt() {
        return random(SALT_BYTES);
    }

    /** 每次加密都要一个新的。<b>绝不重用</b>。 */
    public static byte[] newNonce() {
        return random(NONCE_BYTES);
    }

    private static byte[] random(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    // ---------------------------------------------------------------- KDF

    /**
     * 口令 + salt → 256 位密钥。
     *
     * <p>用完请 {@link #wipe} 掉字符数组：{@code String} 在堆上留副本，内存转储里就看得到。
     */
    public static SecretKey derive(char[] passphrase, byte[] salt) {
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(passphrase, salt, KDF_ITERATIONS, KEY_BITS);
            try {
                return new SecretKeySpec(f.generateSecret(spec).getEncoded(), "AES");
            } finally {
                spec.clearPassword();
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("这个 JRE 没有 PBKDF2WithHmacSHA256", e);
        }
    }

    /** 把口令字符数组抹掉。 */
    public static void wipe(char[] passphrase) {
        if (passphrase != null) Arrays.fill(passphrase, '\0');
    }

    // ---------------------------------------------------------------- AAD

    /**
     * 六个字段拼成 AAD（§17.4.1）。
     *
     * <p>用长度前缀拼，不是直接连：{@code ("ab","c")} 与 {@code ("a","bc")} 直接连出来是同一串，
     * 那样两条不同的记录会共用一个 AAD，"张冠李戴"就挡不住了。
     */
    public static byte[] aad(String serverId, String playerUuid, String appId,
                             String key, int schemaVersion, long recordVersion) {
        ByteBuffer buf = ByteBuffer.allocate(1024);
        putLp(buf, serverId);
        putLp(buf, playerUuid);
        putLp(buf, appId);
        putLp(buf, key);
        buf.putInt(schemaVersion);
        buf.putLong(recordVersion);
        byte[] out = new byte[buf.position()];
        buf.rewind();
        buf.get(out);
        return out;
    }

    private static void putLp(ByteBuffer buf, String s) {
        byte[] b = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
        buf.putInt(b.length);
        buf.put(b);
    }

    // ---------------------------------------------------------------- 填充

    /**
     * 明文 → 4 字节长度前缀 + 明文 + 零填充到 {@link #PAD_BLOCK} 的倍数。
     *
     * <p>带长度前缀才能无歧义地还原 —— 明文本身可能以零字节结尾。
     */
    public static byte[] pad(byte[] plain) {
        int need = 4 + plain.length;
        int total = ((need + PAD_BLOCK - 1) / PAD_BLOCK) * PAD_BLOCK;
        ByteBuffer buf = ByteBuffer.allocate(total);
        buf.putInt(plain.length);
        buf.put(plain);
        return buf.array();
    }

    /** 还原。长度字段不合法时抛 —— 那说明解出来的不是我们填的东西。 */
    public static byte[] unpad(byte[] padded) {
        if (padded.length < 4) throw new IllegalArgumentException("填充块太短");
        ByteBuffer buf = ByteBuffer.wrap(padded);
        int len = buf.getInt();
        if (len < 0 || len > padded.length - 4) throw new IllegalArgumentException("填充里的长度不合法：" + len);
        byte[] out = new byte[len];
        buf.get(out);
        return out;
    }

    // ---------------------------------------------------------------- 加解密

    /** 加密。每次都用新 nonce。 */
    public static Sealed seal(SecretKey key, byte[] aad, byte[] plain) {
        byte[] nonce = newNonce();
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            c.updateAAD(aad);
            return new Sealed(nonce, c.doFinal(pad(plain)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /**
     * 解密。口令不对、AAD 任一字段不对、密文被改过 —— 都抛 {@link javax.crypto.AEADBadTagException}。
     *
     * <p><b>不许把它吞成"返回空"</b>：静默为空会让"服主把别人的密文塞进来"表现成"数据没了"，
     * 玩家不会去查，而那正是要被发现的事（§17.7）。
     */
    public static byte[] unseal(SecretKey key, byte[] aad, Sealed sealed) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, sealed.nonce()));
        c.updateAAD(aad);
        return unpad(c.doFinal(sealed.cipher()));
    }

    /** 口令够不够长（§17.4.4）。 */
    public static boolean passphraseLongEnough(char[] passphrase) {
        return passphrase != null && passphrase.length >= MIN_PASSPHRASE;
    }
}
