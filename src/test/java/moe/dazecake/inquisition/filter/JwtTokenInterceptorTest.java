package moe.dazecake.inquisition.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import moe.dazecake.inquisition.annotation.Login;
import moe.dazecake.inquisition.annotation.ProKey;
import moe.dazecake.inquisition.mapper.ProUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 鉴权失败响应体契约测试。
 *
 * <p>前端依赖响应体中的 {@code code} 与 {@code msg} 区分"鉴权失败/被 WAF 拦截/网络故障"，
 * 因此这里把状态码与文案固化下来：任何文案调整都会让本测试失败，
 * 以此强制同步通知前端，避免静默破坏契约。
 */
class JwtTokenInterceptorTest {

    private static final Gson GSON = new Gson();

    /** 与 SecurityFilter 中 WAF 拦截文案保持一致，用于校验两者不会混淆 */
    private static final String WAF_BLOCKED_MSG = "请求被安全策略拦截";

    private JwtTokenInterceptor interceptor;
    private ProUserMapper proUserMapper;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter body;

    @BeforeEach
    void setUp() throws Exception {
        proUserMapper = mock(ProUserMapper.class);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        interceptor = new JwtTokenInterceptor();
        setField(interceptor, "proUserMapper", proUserMapper);
        setField(interceptor, "objectMapper", new ObjectMapper());
        setField(interceptor, "devMode", false);
    }

    @Test
    @DisplayName("管理员接口未携带 token：返回 401 且响应体为 null 而非空")
    void adminWithoutToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        assertFalse(interceptor.preHandle(request, response, handler("adminOnly")));

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json;charset=UTF-8");

        JsonObject json = GSON.fromJson(body.toString(), JsonObject.class);
        assertEquals(401, json.get("code").getAsInt());
        assertEquals("登录状态已失效，请重新登录", json.get("msg").getAsString());
        assertTrue(json.get("data").isJsonNull());
    }

    @Test
    @DisplayName("管理员接口携带非法 token：同样返回 401 与可读文案")
    void adminWithInvalidToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer not-a-jwt");

        assertFalse(interceptor.preHandle(request, response, handler("adminOnly")));

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertEquals("登录状态已失效，请重新登录", msgOf());
    }

    @Test
    @DisplayName("ProKey 校验不通过：返回 403，且文案与 WAF 拦截文案不同")
    void proKeyMismatch() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer some-pro-key");
        when(proUserMapper.selectOne(any())).thenReturn(null);

        assertFalse(interceptor.preHandle(request, response, handler("proKeyOnly")));

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);

        JsonObject json = GSON.fromJson(body.toString(), JsonObject.class);
        assertEquals(403, json.get("code").getAsInt());
        assertEquals("ProKey 无效或权限不足", json.get("msg").getAsString());
        // 前端以文案区分"鉴权失败"与"被安全策略拦截"，两者一旦相同就会误判
        assertNotEquals(WAF_BLOCKED_MSG, json.get("msg").getAsString());
    }

    @Test
    @DisplayName("401 文案与 WAF 拦截文案不同，前端才能区分两类失败")
    void unauthorizedMsgDiffersFromWaf() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        interceptor.preHandle(request, response, handler("adminOnly"));

        assertNotEquals(WAF_BLOCKED_MSG, msgOf());
    }

    private String msgOf() {
        return GSON.fromJson(body.toString(), JsonObject.class).get("msg").getAsString();
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        TestController controller = new TestController();
        return new HandlerMethod(controller, TestController.class.getMethod(methodName));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * 仅用于提供带鉴权注解的 HandlerMethod，不参与实际调用。
     */
    static class TestController {

        @Login
        public void adminOnly() {
        }

        @ProKey
        public void proKeyOnly() {
        }
    }
}
