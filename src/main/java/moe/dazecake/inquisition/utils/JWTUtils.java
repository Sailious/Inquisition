package moe.dazecake.inquisition.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import moe.dazecake.inquisition.model.entity.AccountEntity;
import moe.dazecake.inquisition.model.entity.AdminEntity;
import moe.dazecake.inquisition.model.entity.ProUserEntity;

import java.util.Date;

public class JWTUtils {

    /**
     * JWT签名密钥，由RunScript在启动时校验并注入。
     * 安全要求：不少于32字符的随机字符串，禁止使用默认弱值。
     */
    private static String SECRET;

    /** token 有效期：30 天 */
    private static final long EXPIRATION = 1000L * 60 * 60 * 24 * 30;

    /**
     * 启动时由 RunScript 注入已校验的密钥。
     */
    public static void initSecret(String secret) {
        SECRET = secret;
    }

    public static String generateTokenForAdmin(AdminEntity adminEntity) {
        JWTCreator.Builder builder = JWT.create();
        builder.withClaim("id", adminEntity.getId())
                .withClaim("username", adminEntity.getUsername())
                .withClaim("permission", adminEntity.getPermission())
                .withClaim("type", "admin")
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION));
        return builder.sign(Algorithm.HMAC256(SECRET));
    }

    public static String generateTokenForUser(AccountEntity accountEntity) {
        JWTCreator.Builder builder = JWT.create();
        builder.withClaim("id", accountEntity.getId())
                .withClaim("account", accountEntity.getAccount())
                .withClaim("type", "user")
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION));
        return builder.sign(Algorithm.HMAC256(SECRET));
    }

    public static String generateTokenForProUser(ProUserEntity proUserEntity) {
        JWTCreator.Builder builder = JWT.create();
        builder.withClaim("id", proUserEntity.getId())
                .withClaim("username", proUserEntity.getUsername())
                .withClaim("type", "proUser")
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION));
        return builder.sign(Algorithm.HMAC256(SECRET));
    }

    public static boolean verifyToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        try {
            JWT.require(Algorithm.HMAC256(SECRET)).build().verify(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 Authorization 头中解析用户ID（去除 "Bearer " 前缀）。
     */
    public static Long getId(String token) {
        if (token == null) {
            throw new IllegalArgumentException("token must not be null");
        }
        return JWT.decode(token.substring(7)).getClaim("id").asLong();
    }

    public static String getAccount(String token) {
        if (token == null) {
            throw new IllegalArgumentException("token must not be null");
        }
        return JWT.decode(token.substring(7)).getClaim("account").asString();
    }

    /**
     * 注意：此方法接收的 token 不含 "Bearer " 前缀。
     */
    public static String getType(String token) {
        if (token == null) {
            throw new IllegalArgumentException("token must not be null");
        }
        return JWT.decode(token).getClaim("type").asString();
    }
}
