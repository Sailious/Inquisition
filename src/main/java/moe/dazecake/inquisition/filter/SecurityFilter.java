package moe.dazecake.inquisition.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 应用层 WAF 过滤器：在请求进入 DispatcherServlet 之前拦截自动化扫描。
 *
 * <p>背景：生产日志单日出现 532 次针对 .env、.azure/accessTokens.json 等敏感文件的
 * 路径穿越探测。这类请求此前由 Spring 的 {@code ResourceHttpRequestHandler} 拦下，
 * 但要走完整路由并逐条告警。本过滤器前置到 Servlet 层，命中即返回 403。
 *
 * <p>设计约束（避免误伤正常业务）：
 * <ul>
 *   <li>只检查 URI 的 path 部分，不检查 query string —— 业务参数中的 ".." 不会被误杀</li>
 *   <li>不读取请求体 —— 不影响 base64 图片上传，且无额外 IO 开销</li>
 *   <li>放行 OPTIONS —— 跨域预检不带业务语义，拦截会导致前端所有跨域写操作失败</li>
 *   <li>响应体不回显命中的路径，避免给攻击者反馈探测结果</li>
 * </ul>
 *
 * <p>注册方式：直接以 {@code @Component} + {@code @Order} 声明，由 Spring Boot 自动注册
 * 并置于过滤链最前端。这里刻意不采用 FilterRegistrationBean 手动 new 实例的方式 ——
 * 手动创建的对象不经过 populateBean，本类的 {@code @Value} 配置不会生效。
 *
 * <p>注意：畸形请求（如把 TLS ClientHello 打到 HTTP 端口）在 Tomcat 协议层就被拒绝，
 * 根本到不了这里，应用层无法拦截，需靠边界 WAF 或日志级别抑制。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SecurityFilter extends OncePerRequestFilter {

    /** 路径穿越的解码后形态 */
    private static final String TRAVERSAL_MARK = "..";

    /**
     * 敏感文件/目录特征，均取自生产日志中真实出现的探测目标。
     * 统一小写比较。
     */
    private static final List<String> DENIED_KEYWORDS = Arrays.asList(
            ".env",
            ".git",
            ".svn",
            ".aws",
            ".azure",
            ".htpasswd",
            ".htaccess",
            ".ds_store",
            "web.config",
            "id_rsa",
            "accesstokens.json",
            "wp-login",
            "wp-admin",
            "phpmyadmin"
    );

    /** 总开关。默认开启；排障时可临时关闭。 */
    @Value("${inquisition.security.waf.enabled:true}")
    private boolean enabled;

    /**
     * 是否信任 X-Forwarded-For 获取客户端 IP。
     * 默认 false —— 该头可伪造；仅当部署在可信反向代理之后，由运维显式开启。
     */
    @Value("${inquisition.security.waf.trust-proxy:false}")
    private boolean trustProxy;

    /** 命中规则时是否在 403 响应上补 CORS 头，便于前端区分"被拦截"与"网络故障" */
    @Value("${inquisition.security.waf.cors-on-deny:true}")
    private boolean corsOnDeny;

    /** 复用 CORS 白名单，用于 403 响应回显合法来源 */
    @Value("${inquisition.cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        // 跨域预检必须放行，否则前端所有跨域写操作都会失败
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String rawUri = request.getRequestURI();
        if (rawUri == null || rawUri.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        String uri = rawUri.toLowerCase(Locale.ROOT);
        String decoded = decodeOnce(uri);

        String hit = matchRule(uri, decoded);
        if (hit != null) {
            deny(request, response, hit, rawUri);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 匹配拦截规则，返回命中的特征串；未命中返回 null。
     *
     * @param uri     原始 URI（已小写）
     * @param decoded 解码后的 URI（已小写），用于覆盖 %2e%2e 等编码绕过
     */
    private String matchRule(String uri, String decoded) {
        // 路径穿越：既看原始形态，也看解码后形态
        if (uri.contains(TRAVERSAL_MARK) || decoded.contains(TRAVERSAL_MARK)) {
            return TRAVERSAL_MARK;
        }
        for (String keyword : DENIED_KEYWORDS) {
            if (uri.contains(keyword) || decoded.contains(keyword)) {
                return keyword;
            }
        }
        return null;
    }

    /**
     * 只做一次 URL 解码，用于识别 %2e%2e、%2f 等编码变体。
     * 解码失败时退回原始字符串，不做二次解码（避免双重编码绕过）。
     */
    private String decodeOnce(String uri) {
        try {
            return URLDecoder.decode(uri, StandardCharsets.UTF_8.name()).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return uri;
        }
    }

    /**
     * 拒绝请求：403 + 极简响应体，并记录客户端 IP 与 UA 便于追踪。
     */
    private void deny(HttpServletRequest request, HttpServletResponse response, String hit, String rawUri)
            throws IOException {
        String clientIp = resolveClientIp(request);
        String ua = request.getHeader("User-Agent");

        log.warn("【WAF】 已拦截疑似扫描请求: rule={}, ip={}, method={}, uri={}, ua={}",
                hit, clientIp, request.getMethod(), rawUri, ua);

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");

        // 仅对白名单内的来源回显 CORS 头，避免等于放行所有来源
        if (corsOnDeny) {
            String origin = request.getHeader("Origin");
            if (origin != null && isOriginAllowed(origin)) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                response.setHeader("Vary", "Origin");
                response.setHeader("Access-Control-Allow-Credentials", "true");
            }
        }

        // 不回显命中特征与原始路径，避免向攻击者泄露探测结果
        response.getWriter().write("{\"code\":403,\"msg\":\"请求被安全策略拦截\",\"data\":null}");
    }

    /**
     * 解析客户端 IP。默认不信任 X-Forwarded-For（可被伪造），
     * 仅在 trust-proxy=true 时取其首跳。
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (trustProxy) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                return xff.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isEmpty()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 判断来源是否在 CORS 白名单内。未配置白名单时不回显任何来源。
     */
    private boolean isOriginAllowed(String origin) {
        if (allowedOrigins == null || allowedOrigins.trim().isEmpty()) {
            return false;
        }
        for (String item : allowedOrigins.split(",")) {
            if (origin.equalsIgnoreCase(item.trim())) {
                return true;
            }
        }
        return false;
    }
}
