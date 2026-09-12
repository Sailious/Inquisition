package moe.dazecake.inquisition.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * SecurityFilter 的规则自测。
 *
 * <p>覆盖两类场景：真实攻击特征必须被拦，正常业务请求必须放行。
 * 后者尤其重要——WAF 误杀造成的故障远比扫描本身严重。
 */
class SecurityFilterTest {

    private SecurityFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SecurityFilter();
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "trustProxy", false);
        ReflectionTestUtils.setField(filter, "corsOnDeny", true);
        ReflectionTestUtils.setField(filter, "allowedOrigins", "https://ark.sailwertech.top");
    }

    /**
     * 执行一次过滤。
     *
     * @return 响应；chain 被执行（放行）时其 request 不为 null
     */
    private MockHttpServletResponse exec(String method, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        // 真实容器的 getRequestURI() 不含 query string，这里同样拆开设置，
        // 否则 "?keyword=.." 会被误当作 path 参与规则匹配
        int q = uri.indexOf('?');
        if (q >= 0) {
            request.setRequestURI(uri.substring(0, q));
            request.setQueryString(uri.substring(q + 1));
        } else {
            request.setRequestURI(uri);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, response, chain);
        // 放行时 MockFilterChain 会记录请求，据此区分"放行"与"拦截"
        if (chain.getRequest() != null) {
            response.setStatus(HttpServletResponse.SC_OK);
        }
        return response;
    }

    @Test
    @DisplayName("正常业务路径应放行")
    void shouldAllowNormalRequest() throws Exception {
        assertEquals(200, exec("GET", "/user/login").getStatus());
        assertEquals(200, exec("POST", "/log/uploadImage").getStatus());
        assertEquals(200, exec("GET", "/v3/api-docs").getStatus());
    }

    @Test
    @DisplayName("路径穿越探测 .env 应拦截")
    void shouldBlockEnvTraversal() throws Exception {
        // 生产日志中真实出现的形态
        assertEquals(403, exec("GET", "/media../.env").getStatus());
        assertEquals(403, exec("GET", "/static../.env").getStatus());
        assertEquals(403, exec("GET", "/css../.env").getStatus());
    }

    @Test
    @DisplayName("敏感文件探测应拦截")
    void shouldBlockSensitiveFile() throws Exception {
        assertEquals(403, exec("GET", "/.git/config").getStatus());
        assertEquals(403, exec("GET", "/static../.azure/accessTokens.json").getStatus());
        assertEquals(403, exec("GET", "/.aws/credentials").getStatus());
        assertEquals(403, exec("GET", "/wp-login.php").getStatus());
    }

    @Test
    @DisplayName("URL 编码绕过应拦截")
    void shouldBlockEncodedTraversal() throws Exception {
        assertEquals(403, exec("GET", "/%2e%2e%2f.env").getStatus());
        assertEquals(403, exec("GET", "/static..%2f.env").getStatus());
    }

    @Test
    @DisplayName("OPTIONS 预检必须放行，否则前端跨域写操作会失败")
    void shouldAllowOptionsPreflight() throws Exception {
        assertEquals(200, exec("OPTIONS", "/user/login").getStatus());
        assertEquals(200, exec("OPTIONS", "/log/uploadImage").getStatus());
    }

    @Test
    @DisplayName("query string 中的 .. 不应误杀业务参数")
    void shouldNotBlockQueryString() throws Exception {
        assertEquals(200, exec("GET", "/user/search?keyword=../etc/passwd").getStatus());
    }

    @Test
    @DisplayName("关闭开关时全部放行")
    void shouldAllowAllWhenDisabled() throws Exception {
        ReflectionTestUtils.setField(filter, "enabled", false);
        assertEquals(200, exec("GET", "/media../.env").getStatus());
    }

    @Test
    @DisplayName("被拦截时不应回显命中路径，避免泄露探测结果")
    void shouldNotLeakPathInResponseBody() throws Exception {
        var response = exec("GET", "/media../.env");
        String body = response.getContentAsString();
        assertEquals(403, response.getStatus());
        assertNotNull(body, "响应体不应为空");
        assertEquals(false, body.contains(".env"), "响应体不应包含被探测的路径");
        assertEquals(false, body.contains("rule"), "响应体不应包含命中规则名");
    }

    @Test
    @DisplayName("白名单内的来源应回写 CORS 头，便于前端识别 403")
    void shouldAddCorsHeaderForAllowedOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/media../.env");
        request.addHeader("Origin", "https://ark.sailwertech.top");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertEquals(403, response.getStatus());
        assertEquals("https://ark.sailwertech.top", response.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("不在白名单的来源不应回写 CORS 头，避免等于放行所有来源")
    void shouldNotAddCorsHeaderForUnknownOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/media../.env");
        request.addHeader("Origin", "https://evil.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertEquals(403, response.getStatus());
        assertNull(response.getHeader("Access-Control-Allow-Origin"));
    }
}
