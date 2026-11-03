package com.november.mcphone.core.script.pkg;

import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;

/**
 * {@code META/rotate.json}：作者换密钥的声明（施工方案 §12.6）。
 *
 * <pre>{ "format":1, "alg":"ed25519", "oldFingerprint":"9579-…", "newPubkey":"&lt;base64&gt;", "sig":"&lt;base64&gt;" }</pre>
 *
 * <h2>必须自带旧密钥的签名</h2>
 *
 * 不然任何人都能塞一个"我换钥匙了"进去，把自己的公钥接到别人的信任上 ——
 * 那就是把 TOFU 整个作废。所以：<b>旧公钥验过这份声明，且旧指纹已经被信任，才自动接受新指纹</b>。
 * 旧公钥验不过就拒绝轮换并明说原因，不静默忽略。
 *
 * <p>签的是 {@code "mcphone-rotate-v1\0" || oldFingerprint || newPubkey}，
 * 域分隔符与包签名、包摘要都不同 —— 三处的哈希互相不能当输入用。
 */
public record RotateManifest(int format, String alg, String oldFingerprint,
                             byte[] newPubkey, byte[] sig) {

    public static final int FORMAT = 1;

    private static final byte[] DOMAIN = "mcphone-rotate-v1\0".getBytes(StandardCharsets.UTF_8);

    public static RotateManifest parse(byte[] json) {
        JsonObject root = SigManifest.object(json);

        int format = SigManifest.intField(root, "format");
        if (format != FORMAT) throw PackageError.of(PackageError.Code.E_SIG_BAD_FORMAT, format);

        String alg = SigManifest.stringField(root, "alg");
        if (!Signatures.knownAlg(alg)) throw PackageError.of(PackageError.Code.E_SIG_UNKNOWN_ALG, alg);

        return new RotateManifest(format, alg,
                SigManifest.stringField(root, "oldFingerprint"),
                SigManifest.base64Field(root, "newPubkey"),
                SigManifest.base64Field(root, "sig"));
    }

    /** 新指纹。轮换成功之后信任就转到它身上。 */
    public String newFingerprint() {
        return Signatures.fingerprint(newPubkey);
    }

    /** 要签/要验的那串字节。 */
    public static byte[] payload(String oldFingerprint, byte[] newPubkey) {
        byte[] fp = oldFingerprint.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[DOMAIN.length + fp.length + newPubkey.length];
        System.arraycopy(DOMAIN, 0, out, 0, DOMAIN.length);
        System.arraycopy(fp, 0, out, DOMAIN.length, fp.length);
        System.arraycopy(newPubkey, 0, out, DOMAIN.length + fp.length, newPubkey.length);
        return out;
    }

    /** 用旧公钥验这份声明。<b>旧公钥从信任库里取，不是从包里取</b> —— 从包里取等于自己证明自己。 */
    public boolean verify(byte[] oldPubkeyX509) {
        if (!oldFingerprint.equals(Signatures.fingerprint(oldPubkeyX509))) return false;
        try {
            PublicKey old = Signatures.publicKey(oldPubkeyX509);
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(old);
            s.update(payload(oldFingerprint, newPubkey));
            return s.verify(sig);
        } catch (GeneralSecurityException | RuntimeException e) {
            return false;
        }
    }

    /** 签一份。作者换设备时用旧密钥跑一次。 */
    public static byte[] sign(java.security.PrivateKey oldPriv, String oldFingerprint, byte[] newPubkey) {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(oldPriv);
            s.update(payload(oldFingerprint, newPubkey));
            return s.sign();
        } catch (GeneralSecurityException e) {
            throw PackageError.of(PackageError.Code.E_SIG_ROTATE_BAD, "签不出来：" + e.getMessage());
        }
    }
}
