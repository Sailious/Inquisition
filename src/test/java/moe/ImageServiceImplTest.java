package moe;

import moe.dazecake.inquisition.service.impl.ImageServiceImpl;
import moe.dazecake.inquisition.utils.Result;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class ImageServiceImplTest {

    /**
     * 测试带 Data URI 前缀的 base64 图片内容在未配置任何存储服务时
     * 能正确进入主流程并返回合理的错误提示。
     */
    @Test
    public void testUploadImageWithoutStorage() {
        ImageServiceImpl imageService = new ImageServiceImpl();
        Result<String> result = imageService.uploadImage(
                "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
        );
        // 所有存储服务默认未启用时，应返回未配置提示
        assertNotNull(result);
        assertEquals("未配置任何存储服务", result.getMsg());
        assertFalse(result.getCode() == 200);
    }

    /**
     * 测试空图片返回失败。
     */
    @Test
    public void testUploadEmptyImage() {
        ImageServiceImpl imageService = new ImageServiceImpl();
        Result<String> result = imageService.uploadImage("");
        assertNotNull(result);
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
