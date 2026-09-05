package moe.dazecake.inquisition;

import com.qiniu.http.Response;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import moe.dazecake.inquisition.constant.ResponseCodeConstants;
import moe.dazecake.inquisition.service.impl.ImageServiceImpl;
import moe.dazecake.inquisition.utils.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/**
 * 七牛私有空间签名下载 URL 的回归测试。
 *
 * <p>背景缺陷（#32）：私有空间上传成功后仅拼接裸公开 URL（domain + key），
 * 七牛 CDN 鉴权模块返回 403，图片无法加载。修复后应通过
 * {@code auth.privateDownloadUrl} 生成带 {@code e}/{@code token} 参数的临时下载链接。
 *
 * <p>测试隔离策略：七牛 SDK 的鉴权签名（uploadToken / privateDownloadUrl）均为
 * 纯本地 HMAC 计算，不发网络；唯一发网络的 {@code UploadManager.put} 通过
 * mockConstruction 拦截，杜绝真实上传。COS / CHFS / 邮件等外部依赖均未启用。
 */
@ExtendWith(MockitoExtension.class)
class ImageServiceQiniuUrlTest {

    private static final String PNG_BASE64 =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ"
            + "AAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    private ImageServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new ImageServiceImpl();
        // 仅启用七牛，OSS 与 CHFS 均关闭，保证 uploadImage 走入七牛分支
        setField(service, "ossEnable", false);
        setField(service, "chfsEnable", false);
        setField(service, "qiniuEnable", true);
        setField(service, "qiniuAccessKey", "unit-test-ak");
        // 离线签名密钥：仅用于本地 HMAC 计算，无需真实有效（服务端不参与）
        setField(service, "qiniuSecretKey",
                "unit-test-sk-unit-test-sk-unit-test-sk-unit-test-sk");
        setField(service, "qiniuBucket", "inquisition-log");
        setField(service, "qiniuDomain", "qiniuoss.example.com");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = ImageServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    /** 反射写入 public final 实例字段（如七牛 Response.error），用于构造失败态响应。 */
    private static void setFinalField(Object target, String name, Object value) throws Exception {
        Field f = Response.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    /**
     * 缺陷回归：上传成功后必须返回带签名的临时下载链接（含 e/token），
     * 而非裸公开 URL。修复前（main 分支裸拼接）本用例应当失败。
     */
    @Test
    @DisplayName("私有空间上传成功后返回带签名下载链接而非裸URL")
    void uploadShouldReturnSignedUrlWhenPrivateBucket() {
        // 拦截 UploadManager 的真实网络上传，模拟上传成功并返回 putRet.key
        try (MockedConstruction<UploadManager> ignored =
                     mockConstruction(UploadManager.class, (mock, ctx) -> {
                         Response okResp = mock(Response.class);
                         when(okResp.isOK()).thenReturn(true);
                         DefaultPutRet putRet = new DefaultPutRet();
                         putRet.key = "1788550499179.png";
                         when(okResp.jsonToObject(DefaultPutRet.class)).thenReturn(putRet);
                         when(mock.put(any(byte[].class), anyString(), anyString()))
                                 .thenReturn(okResp);
                     })) {

            Result<String> result = service.uploadImage(PNG_BASE64);

            // 断言1：返回成功态
            assertEquals(ResponseCodeConstants.SUCCESS, result.getCode());
            // 断言2：返回带签名参数的下载链接（含 e= 过期时间与 token= 鉴权签名）
            assertNotNull(result.getData());
            String url = result.getData();
            assertTrue(url.contains("?e="), "私有空间 URL 应携带签名过期参数 e，实际: " + url);
            assertTrue(url.contains("&token="), "私有空间 URL 应携带鉴权参数 token，实际: " + url);
            // 断言3：签名 URL 不是裸公开 URL（裸 URL 会被 CDN 拦截返 403）
            assertFalse(url.equals("https://qiniuoss.example.com/1788550499179.png"),
                    "不应返回无签名的裸公开 URL");
            // 断言4：签名基于原公开 URL 前缀生成
            assertTrue(url.startsWith("https://qiniuoss.example.com/1788550499179.png"));
        }
    }

    /**
     * 上传失败（SDK 返回非 200）时应返回 failed 而非成功且无 URL。
     */
    @Test
    @DisplayName("七牛上传响应非200时返回失败")
    void uploadShouldFailWhenResponseNotOk() {
        try (MockedConstruction<UploadManager> ignored =
                     mockConstruction(UploadManager.class, (mock, ctx) -> {
                         Response badResp = mock(Response.class);
                         when(badResp.isOK()).thenReturn(false);
                         // Response.error 为 public final 字段，须反射注入
                         setFinalField(badResp, "error", "up: 503 service unavailable");
                         when(mock.put(any(byte[].class), anyString(), anyString()))
                                 .thenReturn(badResp);
                     })) {

            Result<String> result = service.uploadImage(PNG_BASE64);

            assertEquals(ResponseCodeConstants.FAIL, result.getCode());
            assertNotNull(result.getMsg());
            assertTrue(result.getMsg().contains("七牛云上传失败"));
        }
    }

    /**
     * 七牛配置缺失任一关键项时，应在发起任何上传前直接返回 failed。
     */
    @Test
    @DisplayName("七牛配置不完整时返回failed且不触发上传")
    void uploadShouldRejectWhenConfigIncomplete() throws Exception {
        // 覆盖基础配置后，扣掉 bucket（复用构造拦截以捕获是否真的发起 put）
        setField(service, "qiniuBucket", "");

        try (MockedConstruction<UploadManager> mocked =
                     mockConstruction(UploadManager.class)) {

            Result<String> result = service.uploadImage(PNG_BASE64);

            // 配置缺失必然失败，且返回提示明确
            assertEquals(ResponseCodeConstants.FAIL, result.getCode());
            assertEquals("七牛云存储配置不完整，请检查 accessKey、secretKey、bucket、domain 是否已配置",
                    result.getMsg());
            // 配置不完整时不允许触碰 UploadManager（即不发网络上传）
            assertEquals(0, mocked.constructed().size(),
                    "配置不完整时不应创建 UploadManager 发起上传");
        }
    }

    /**
     * domain 缺省协议前缀时，应自动补齐 https 并用于签名 URL 拼接。
     */
    @Test
    @DisplayName("domain无协议前缀时自动补https生成签名URL")
    void uploadShouldAddHttpsPrefixWhenDomainMissingScheme() throws Exception {
        setField(service, "qiniuDomain", "qiniuoss.example.com");

        try (MockedConstruction<UploadManager> ignored =
                     mockConstruction(UploadManager.class, (mock, ctx) -> {
                         Response okResp = mock(Response.class);
                         when(okResp.isOK()).thenReturn(true);
                         DefaultPutRet putRet = new DefaultPutRet();
                         putRet.key = "100000.png";
                         when(okResp.jsonToObject(DefaultPutRet.class)).thenReturn(putRet);
                         when(mock.put(any(byte[].class), anyString(), anyString()))
                                 .thenReturn(okResp);
                     })) {

            Result<String> result = service.uploadImage(PNG_BASE64);

            assertEquals(ResponseCodeConstants.SUCCESS, result.getCode());
            assertNotNull(result.getData());
            // 补齐 https 前缀后再拼 key 并签名
            assertTrue(result.getData().startsWith("https://qiniuoss.example.com/100000.png"));
        }
    }

    // ---------- T-5：七牛签名 URL 有效期配置化回归测试（#32） ----------

    /** 解析签名 URL 中 e= 过期时间戳（Unix 秒）。 */
    private static long extractExpireSecs(String signedUrl) {
        Matcher m = Pattern.compile("[?&]e=(\\d+)").matcher(signedUrl);
        assertTrue(m.find(), "签名 URL 应含 e= 过期时间戳: " + signedUrl);
        return Long.parseLong(m.group(1));
    }

    /**
     * 断言 URL 的 e= 过期时间戳对齐「被测方法执行窗口」+ 指定有效期。
     *
     * <p>privateDownloadUrl 内部以 System.currentTimeMillis()/1000 + expireSeconds
     * 计算 e=，因此真实 e 落在 [nowBefore, nowAfter] 快照区间 + expire 之内。
     */
    private static void assertExpireAlignsToWindow(String signedUrl, long expireSeconds,
                                                   long nowSecBefore, long nowSecAfter) {
        long actual = extractExpireSecs(signedUrl);
        long low = nowSecBefore + expireSeconds;
        long high = nowSecAfter + expireSeconds;
        assertTrue(actual >= low && actual <= high,
                "e= 过期时间戳 " + actual + " 应在 [" + low + ", " + high + "]（有效期 " + expireSeconds + "s），URL: " + signedUrl);
    }

    /**
     * 自定义有效期：注入 qiniuUrlExpireSeconds 后，签名 URL 的 e= 必须反映
     * 该自定义值（此处 1 小时），而非默认 30 天魔法数。
     */
    @Test
    @DisplayName("自定义有效期(1小时)反映到签名URL的e=过期时间戳")
    void uploadShouldReflectCustomUrlExpireSeconds() throws Exception {
        setField(service, "qiniuUrlExpireSeconds", 3600L);

        try (MockedConstruction<UploadManager> ignored =
                     mockConstruction(UploadManager.class, (mock, ctx) -> {
                         Response okResp = mock(Response.class);
                         when(okResp.isOK()).thenReturn(true);
                         DefaultPutRet putRet = new DefaultPutRet();
                         putRet.key = "1788550499179.png";
                         when(okResp.jsonToObject(DefaultPutRet.class)).thenReturn(putRet);
                         when(mock.put(any(byte[].class), anyString(), anyString()))
                                 .thenReturn(okResp);
                     })) {

            long nowSecBefore = System.currentTimeMillis() / 1000;
            Result<String> result = service.uploadImage(PNG_BASE64);
            long nowSecAfter = System.currentTimeMillis() / 1000;

            assertEquals(ResponseCodeConstants.SUCCESS, result.getCode());
            assertNotNull(result.getData());
            // 1 小时（3600s）有效期：e = now + 3600，而非默认 30 天（2592000s）
            assertExpireAlignsToWindow(result.getData(), 3600L, nowSecBefore, nowSecAfter);
            // 自定义 1 小时 ≠ 默认 30 天，排除落到默认魔法数
            long e = extractExpireSecs(result.getData());
            assertFalse(e > nowSecAfter + 2592000L - 1,
                    "自定义有效期应生效，不应落到默认 30 天窗口");
        }
    }

    /**
     * 默认值：未配置 url-expire-seconds 时（字段保持声明默认 30 天），
     * e= 应对齐 now + 2592000（30 天），与 COS 分支行为一致，无行为倒退。
     */
    @Test
    @DisplayName("默认有效期(30天)反映到签名URL的e=过期时间戳")
    void uploadShouldUseDefaultThirtyDaysWhenUnset() {
        // 默认字段即 DEFAULT_QINIU_URL_EXPIRE_SECONDS（2592000），不 set，模拟未配置
        try (MockedConstruction<UploadManager> ignored =
                     mockConstruction(UploadManager.class, (mock, ctx) -> {
                         Response okResp = mock(Response.class);
                         when(okResp.isOK()).thenReturn(true);
                         DefaultPutRet putRet = new DefaultPutRet();
                         putRet.key = "1788550499179.png";
                         when(okResp.jsonToObject(DefaultPutRet.class)).thenReturn(putRet);
                         when(mock.put(any(byte[].class), anyString(), anyString()))
                                 .thenReturn(okResp);
                     })) {

            long nowSecBefore = System.currentTimeMillis() / 1000;
            Result<String> result = service.uploadImage(PNG_BASE64);
            long nowSecAfter = System.currentTimeMillis() / 1000;

            assertEquals(ResponseCodeConstants.SUCCESS, result.getCode());
            assertNotNull(result.getData());
            // 默认 2592000s（30 天），与 COS 分支 30 天对齐
            assertExpireAlignsToWindow(result.getData(), 2592000L, nowSecBefore, nowSecAfter);
        }
    }

    /**
     * 非法值兜底：注入 ≤0 的 url-expire-seconds（此处 0 与 -1）时，
     * resolveUrlExpireSeconds() 应回退默认 30 天，而不是用 0/负有效期产生立即过期链接。
     */
    @Test
    @DisplayName("有效期配置为非法值(<=0)时回退默认30天")
    void uploadShouldFallbackToDefaultWhenExpireInvalid() throws Exception {
        for (long invalid : new long[]{0L, -1L}) {
            setField(service, "qiniuUrlExpireSeconds", invalid);

            try (MockedConstruction<UploadManager> ignored =
                         mockConstruction(UploadManager.class, (mock, ctx) -> {
                             Response okResp = mock(Response.class);
                             when(okResp.isOK()).thenReturn(true);
                             DefaultPutRet putRet = new DefaultPutRet();
                             putRet.key = "1788550499179.png";
                             when(okResp.jsonToObject(DefaultPutRet.class)).thenReturn(putRet);
                             when(mock.put(any(byte[].class), anyString(), anyString()))
                                     .thenReturn(okResp);
                         })) {

                long nowSecBefore = System.currentTimeMillis() / 1000;
                Result<String> result = service.uploadImage(PNG_BASE64);
                long nowSecAfter = System.currentTimeMillis() / 1000;

                assertEquals(ResponseCodeConstants.SUCCESS, result.getCode());
                assertNotNull(result.getData());
                // 非法值应回退默认 30 天
                assertExpireAlignsToWindow(result.getData(), 2592000L, nowSecBefore, nowSecAfter);
            }
        }
    }

}
