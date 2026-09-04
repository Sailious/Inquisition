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
}
