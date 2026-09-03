package moe.dazecake.inquisition.utils;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * 密码编码工具。
 *
 * 注意：MD5 已不推荐用于密码存储，仅保留用于非安全场景（如签名）和旧数据兼容。
 * 管理员/代理密码使用 BCrypt（见 {@link #BCrypt(String)}）。
 * 游戏账号密码需可逆（需明文发送给游戏服务器登录），使用 AES 加密存储（见 {@link #encrypt}/{@link #decrypt}）。
 */
public class Encoder {

    /**
     * BCrypt 算法最大有效密码长度（字节）。
     * <p>CVE-2025-22228 缓解：BCrypt 仅使用前 72 字节，超过该长度的部分被忽略，
     * 导致仅前 72 字节一致的超长密码可被错误接受。应用层强制限制密码≤72 字节可消除该漏洞。
     */
    public static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private static final BCryptPasswordEncoder BCRYPT_ENCODER = new BCryptPasswordEncoder(10);
    private static final String AES_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String AES_KEY_ALGORITHM = "AES";
    private static final int IV_LENGTH = 16;

    /** AES 密钥，由 RunScript 启动时注入（不少于32字符） */
    private static byte[] aesKey;

    /**
     * 启动时由 RunScript 注入 AES 密钥（复用 inquisition.secret）。
     */
    public static void initAesKey(String secret) {
        // 取 SHA-256 的前16字节作为 AES-128 密钥
        byte[] hash = DigestUtils.sha256(secret);
        aesKey = new byte[16];
        System.arraycopy(hash, 0, aesKey, 0, 16);
    }

    /**
     * 使用 BCrypt 哈希密码（推荐用于管理员/代理密码存储）。
     *
     * @throws IllegalArgumentException 当密码超过 {@link #BCRYPT_MAX_PASSWORD_BYTES} 字节时抛出，
     *         以阻止绕过 CVE-2025-22228 的超长密码入库
     */
    public static String BCrypt(String plain) {
        if (plain == null) {
            throw new IllegalArgumentException("plain must not be null");
        }
        requireBcryptPasswordValid(plain);
        return BCRYPT_ENCODER.encode(plain);
    }

    /**
     * 校验 BCrypt 密码。
     *
     * <p>若输入密码超过 {@link #BCRYPT_MAX_PASSWORD_BYTES} 字节，直接返回 false。
     * 原因：BCrypt 只处理前 72 字节，超长密码会被截断比较产生误判（CVE-2025-22228）。
     * 返回 false 可让调用方回退到 MD5 等旧算法校验。
     */
    public static boolean BCryptMatches(String plain, String hashed) {
        if (plain == null || hashed == null) {
            return false;
        }
        if (plain.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            return false;
        }
        return BCRYPT_ENCODER.matches(plain, hashed);
    }

    /**
     * 判断密码是否可安全用于 BCrypt（不超过 72 字节）。
     */
    public static boolean isBcryptPasswordValid(String plain) {
        return plain != null
                && plain.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_PASSWORD_BYTES;
    }

    /**
     * 强制校验密码长度可用于 BCrypt。
     *
     * @throws IllegalArgumentException 密码超过 {@link #BCRYPT_MAX_PASSWORD_BYTES} 字节时抛出
     */
    private static void requireBcryptPasswordValid(String plain) {
        if (!isBcryptPasswordValid(plain)) {
            throw new IllegalArgumentException(
                    "密码不能超过 " + BCRYPT_MAX_PASSWORD_BYTES + " 字节（当前 "
                            + plain.getBytes(StandardCharsets.UTF_8).length + " 字节）");
        }
    }

    /**
     * MD5 哈希（不推荐用于密码存储，仅用于签名等非安全场景）。
     */
    public static String MD5(String str) {
        return Hex.encodeHexString(DigestUtils.md5(str), true);
    }

    /**
     * AES 加密（用于游戏账号密码的可逆存储）。
     * 返回 Base64(IV + 密文)。
     */
    public static String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        if (aesKey == null) {
            throw new IllegalStateException("AES key not initialized");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(aesKey, AES_KEY_ALGORITHM),
                    new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.encodeBase64String(combined);
        } catch (Exception e) {
            throw new RuntimeException("AES encrypt failed", e);
        }
    }

    /**
     * AES 解密（对应 {@link #encrypt}）。
     * 若输入不是加密格式（旧明文数据），则原样返回以兼容。
     */
    public static String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            return encrypted;
        }
        if (aesKey == null) {
            throw new IllegalStateException("AES key not initialized");
        }
        byte[] combined;
        try {
            combined = Base64.decodeBase64(encrypted);
        } catch (Exception e) {
            // 非 Base64，视为旧明文数据
            return encrypted;
        }
        if (combined.length <= IV_LENGTH) {
            // 可能是未加密的旧数据（明文），直接返回
            return encrypted;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            byte[] encryptedData = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, encryptedData, 0, encryptedData.length);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(aesKey, AES_KEY_ALGORITHM),
                    new IvParameterSpec(iv));
            byte[] decrypted = cipher.doFinal(encryptedData);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 解密失败，可能是旧明文数据，原样返回
            return encrypted;
        }
    }
}
