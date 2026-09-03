package moe;

import moe.dazecake.inquisition.constant.ResponseCodeConstants;
import moe.dazecake.inquisition.service.impl.ImageServiceImpl;
import moe.dazecake.inquisition.utils.Result;
import org.junit.jupiter.api.Test;

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
}
