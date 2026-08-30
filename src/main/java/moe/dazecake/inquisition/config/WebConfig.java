package moe.dazecake.inquisition.config;

import lombok.extern.slf4j.Slf4j;
import moe.dazecake.inquisition.filter.JwtTokenInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.util.Arrays;

@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String[] ALLOWED_METHODS = {"GET", "POST", "PUT", "DELETE", "OPTIONS"};
    private static final String ALLOW_ALL = "*";

    @Resource
    private JwtTokenInterceptor jwtTokenInterceptor;

    /**
     * CORS 总开关。
     * false = 完全不注册跨域映射，仅允许同源访问（最严格）
     * true  = 启用，具体放行范围由 allowed-origins 决定
     */
    @Value("${inquisition.cors.enabled:true}")
    private boolean corsEnabled;

    /**
     * 允许的来源白名单，逗号分隔。
     * 填 *          = 允许所有来源（仅建议本地调试）
     * 填具体域名    = 白名单模式
     * 留空          = 仅允许同源访问
     */
    @Value("${inquisition.cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtTokenInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/v3/api-docs",
                        "/druid"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // C4 修复：默认不再无脑放行所有来源，改为可配置的三态策略
        if (!corsEnabled) {
            log.info("【CORS】 已关闭，仅允许同源访问");
            return;
        }

        String[] origins = resolveOrigins();

        if (origins == null) {
            // 未配置白名单：不注册任何 CORS 映射，由浏览器默认同源策略生效。
            // 注意：不能只设 allowCredentials 而不设来源 —— Spring 的 applyPermitDefaultValues()
            // 会把未设置的来源默认填成 ["*"]，与 allowCredentials 冲突，导致请求时报
            // IllegalArgumentException: When allowCredentials is true, allowedOrigins cannot contain "*"
            log.warn("【CORS】 已启用但未配置 allowed-origins，回退为仅允许同源访问");
            return;
        }

        boolean allowAll = origins.length == 1 && ALLOW_ALL.equals(origins[0]);

        if (allowAll) {
            log.warn("【CORS】 已配置为允许所有来源（*），存在安全风险，仅建议本地调试使用");
        } else {
            log.info("【CORS】 白名单已生效: {}", Arrays.toString(origins));
        }

        var mapping = registry.addMapping("/**")
                .allowedMethods(ALLOWED_METHODS)
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        if (allowAll) {
            // 注意：allowedOrigins("*") 与 allowCredentials(true) 在 Spring 5.3+ 互斥，
            // 需用 allowedOriginPatterns("*") 才能在放行所有来源的同时保留凭证支持
            mapping.allowedOriginPatterns(ALLOW_ALL);
        } else {
            mapping.allowedOrigins(origins);
        }
    }

    /**
     * 解析白名单配置。返回 null 表示未配置（仅同源）。
     */
    private String[] resolveOrigins() {
        if (!StringUtils.hasText(allowedOrigins)) {
            return null;
        }
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        return origins.length == 0 ? null : origins;
    }
}
