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
     */
    public static String BCrypt(String plain) {
        if (plain == null) {
            throw new IllegalArgumentException("plain must not be null");
        }
        return BCRYPT_ENCODER.encode(plain);
    }

    /**
     * 校验 BCrypt 密码。
     */
    public static boolean BCryptMatches(String plain, String hashed) {
        if (plain == null || hashed == null) {
            return false;
        }
        return BCRYPT_ENCODER.matches(plain, hashed);
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
