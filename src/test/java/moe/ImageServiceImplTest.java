package moe;

import moe.dazecake.inquisition.constant.ResponseCodeConstants;
import moe.dazecake.inquisition.service.impl.ImageServiceImpl;
import moe.dazecake.inquisition.utils.Result;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ImageServiceImpl 的纯单元测试。
 *
 * <p>所有存储开关默认关闭（通过 new 直接实例化，无 Spring 注入），
 * 因此 uploadImage 应返回 failed("未配置任何存储服务")，
 * 不触发任何真实 I/O。
 */
public class ImageServiceImplTest {

    @Test
    public void testUploadImageWhenNoStorageConfigured() {
        ImageServiceImpl imageService = new ImageServiceImpl();

        Result<String> result = imageService.uploadImage("data:image/png;base64,aW1hZ2VkYXRh");

        assertEquals(ResponseCodeConstants.FAIL, result.getCode());
        assertEquals("未配置任何存储服务", result.getMsg());
    }

    @Test
    public void testUploadImageWhenBase64Empty() {
        ImageServiceImpl imageService = new ImageServiceImpl();

        Result<String> result = imageService.uploadImage("");

        assertEquals(ResponseCodeConstants.FAIL, result.getCode());
        assertEquals("图片内容为空", result.getMsg());
    }

    /**
     * 通过反射测试 Data URI 前缀去除逻辑。
     */
    @Test
    public void testStripDataUriPrefix() throws Exception {
        ImageServiceImpl imageService = new ImageServiceImpl();

        Method stripMethod = ImageServiceImpl.class.getDeclaredMethod("stripDataUriPrefix", String.class);
        stripMethod.setAccessible(true);

        // 带 data URI 前缀的 base64 应被去除前缀
        String withPrefix = "data:image/png;base64,aGVsbG8=";
        Object stripped = stripMethod.invoke(imageService, withPrefix);
        assertEquals("aGVsbG8=", stripped);

        // 不带前缀的 base64 应原样返回
        String withoutPrefix = "aGVsbG8=";
        Object stripped2 = stripMethod.invoke(imageService, withoutPrefix);
        assertEquals("aGVsbG8=", stripped2);

        // 不匹配 data: 模式但包含逗号的字符串不应被误处理
        String weirdString = "some,base64data";
        Object stripped3 = stripMethod.invoke(imageService, weirdString);
        assertEquals("some,base64data", stripped3);
    }
}
