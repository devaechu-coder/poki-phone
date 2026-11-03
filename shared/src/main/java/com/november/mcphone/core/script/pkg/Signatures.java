package com.november.mcphone.core.script.pkg;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Locale;
import java.util.Set;

/**
 * Ed25519 签名与验签（施工方案 §12.2、§12.3）。<b>纯 JDK，不分版本，不引第三方。</b>
 *
 * <h2>签名能做的与不能做的（§12.1）</h2>
 *
 * 它<b>不防病毒</b>。它只证明"这份字节是这个密钥签的"。真正挡住毒的是
 * §3.4 的包内容白名单（{@code .class} / {@code .jar} / {@code .so} / {@code .exe} …
 * 一律拒绝整包，经典意义的病毒结构上不可能）与 §16 的沙箱。
 * <b>任何文案里都不许出现"已签名 = 安全"。</b>
 *
 * <p>签名真正给的是四件事：完整性、同一作者、可吊销、信任可传播。
 *
 * <h2>域分隔符</h2>
 *
 * <pre>sig = Ed25519_Sign(priv, "mcphone-sig-v1\0" || packageDigest)</pre>
 *
 * 与摘要那边的 {@code "mcphone-pkg-v1\0"} <b>必须不同</b> ——
 * 否则某一处的哈希能被当作另一处的签名输入。{@code PackageDigest.of} 返回的是十六进制字符串，
 * 这里签的就是它的 UTF-8 字节。
 *
 * <h2>实测（JDK 17，1.20.1 那一支的目标版本）</h2>
 *
 * <pre>公钥 X.509 44 字节 / 私钥 PKCS8 48 字节 / 签名 64 字节 / 改一字节验签为 false</pre>
 */
public final class Signatures {

    private Signatures() {
    }

    /** 认得的算法名。<b>不认识就拒，不许回退成"未签名"</b>（§12.4：那是两档不同的状态）。 */
    public static final String ALG = "ed25519";

    /** 签名输入的域分隔符。与 {@code PackageDigest} 的那个不同，见类注释。 */
    private static final byte[] DOMAIN = "mcphone-sig-v1\0".getBytes(StandardCharsets.UTF_8);

    /** 指纹取公钥 SHA-256 的前几个字节（§12.2）。 */
    public static final int FINGERPRINT_BYTES = 8;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /**
     * 小阶公钥的 y 值（§12.2 的补充判据）。
     *
     * <p>Ed25519 上阶整除 8 的点一共 8 个，去掉 x 的符号位之后只剩这 5 个 y。
     * <b>JDK 的 Ed25519 不查这个</b>：拿恒等点当公钥、签名取 {@code 0x01||0x00*63}，
     * 对任意消息验签恒为 true —— 不持任何私钥就能造出「验签通过」的包
     * （JDK 17 与 21 实测各 500/500）。少了这一层，§12.4 的
     * 「INVALID 是唯一硬拒绝」对这类包永远不成立。
     */
    private static final Set<BigInteger> SMALL_ORDER_Y = Set.of(
            new BigInteger("0", 16),
            new BigInteger("1", 16),
            new BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffec", 16),
            new BigInteger("05fc536d880238b13933c6d305acdfd5f098eff289f4c345b027b2c28f95e826", 16),
            new BigInteger("7a03ac9277fdc74ec6cc392cfa53202a0f67100d760b3cba4fd84d3d706a17c7", 16));

    /**
     * 这把公钥是不是落在小阶子群里。<b>是就一律当验不过</b>，见 {@link #SMALL_ORDER_Y}。
     *
     * <p>取 y 而不是比对 32 字节编码：x 的符号位有两种取值，同一个点两种写法，
     * 比字节会漏掉一半。非规范编码（y >= p）由 JDK 自己在 initVerify 时拒。
     */
    public static boolean smallOrder(PublicKey key) {
        if (!(key instanceof EdECPublicKey ed)) return false;
        return SMALL_ORDER_Y.contains(ed.getPoint().getY());
    }

    // ---------------------------------------------------------------- 密钥

    /** 新生成一对。 */
    public static KeyPair generate() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("这个 JRE 没有 Ed25519（要 JDK 15+）", e);
        }
    }

    /** X.509 编码的公钥字节 → 公钥。 */
    public static PublicKey publicKey(byte[] x509) {
        PublicKey key;
        try {
            key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(x509));
        } catch (GeneralSecurityException e) {
            throw PackageError.of(PackageError.Code.E_SIG_BAD_KEY, "公钥读不出来：" + e.getMessage());
        }
        if (smallOrder(key)) {
            throw PackageError.of(PackageError.Code.E_SIG_BAD_KEY, "公钥落在小阶子群里");
        }
        return key;
    }

    /** PKCS8 编码的私钥字节 → 私钥。 */
    public static PrivateKey privateKey(byte[] pkcs8) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (GeneralSecurityException e) {
            throw PackageError.of(PackageError.Code.E_SIG_BAD_KEY, "私钥读不出来：" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 指纹

    /**
     * 作者指纹：{@code SHA-256(公钥 X.509 编码)} 的前 {@link #FINGERPRINT_BYTES} 字节，
     * 写成 {@code xxxx-xxxx-xxxx-xxxx}。
     *
     * <p>分四组是<b>给人念、给人比对</b>用的 —— 作者在自己的页面上贴指纹，玩家一眼对得上。
     */
    public static String fingerprint(byte[] x509) {
        byte[] h = sha256(x509);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < FINGERPRINT_BYTES; i++) {
            if (i > 0 && i % 2 == 0) sb.append('-');
            sb.append(HEX[(h[i] >> 4) & 0xf]).append(HEX[h[i] & 0xf]);
        }
        return sb.toString();
    }

    public static String fingerprint(PublicKey key) {
        return fingerprint(key.getEncoded());
    }

    // ---------------------------------------------------------------- 签与验

    /** 签一个包摘要。{@code digest} 是 {@code PackageDigest.of} 的返回值。 */
    public static byte[] sign(PrivateKey priv, String digest) {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(priv);
            s.update(DOMAIN);
            s.update(digest.getBytes(StandardCharsets.UTF_8));
            return s.sign();
        } catch (GeneralSecurityException e) {
            throw PackageError.of(PackageError.Code.E_SIG_BAD_KEY, "签不出来：" + e.getMessage());
        }
    }

    /** 验一个包摘要的签名。<b>任何异常都当验不过</b>，不往上抛 —— 验签失败是一种正常结果。 */
    public static boolean verify(PublicKey pub, String digest, byte[] sig) {
        // publicKey() 已经拦过一道；这里是给直接拿着 PublicKey 进来的调用方兜底
        if (smallOrder(pub)) return false;
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(pub);
            s.update(DOMAIN);
            s.update(digest.getBytes(StandardCharsets.UTF_8));
            return s.verify(sig);
        } catch (GeneralSecurityException | RuntimeException e) {
            return false;
        }
    }

    /** 算法名认不认得。大小写不敏感 —— 别让一个 {@code "Ed25519"} 被当成不认识的。 */
    public static boolean knownAlg(String alg) {
        return alg != null && ALG.equals(alg.toLowerCase(Locale.ROOT));
    }

    static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("这个 JRE 没有 SHA-256", e);
        }
    }
}
