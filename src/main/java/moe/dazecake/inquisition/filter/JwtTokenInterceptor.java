package moe.dazecake.inquisition.filter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import moe.dazecake.inquisition.annotation.Login;
import moe.dazecake.inquisition.annotation.ProKey;
import moe.dazecake.inquisition.annotation.ProUserLogin;
import moe.dazecake.inquisition.annotation.UserLogin;
import moe.dazecake.inquisition.annotation.UserOrProUserLogin;
import moe.dazecake.inquisition.mapper.ProUserMapper;
import moe.dazecake.inquisition.model.entity.ProUserEntity;
import moe.dazecake.inquisition.utils.JWTUtils;
import moe.dazecake.inquisition.utils.Result;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;

@Slf4j
@Component
public class JwtTokenInterceptor implements HandlerInterceptor {

    /** 登录失效响应文案。前端据此区分鉴权失败与网络故障，调整需同步通知前端 */
    private static final String LOGIN_EXPIRED_MSG = "登录状态已失效，请重新登录";

    /** ProKey 校验失败响应文案。不得与 WAF 拦截文案相同，否则前端无法区分 */
    private static final String PRO_KEY_INVALID_MSG = "ProKey 无效或权限不足";

    @Resource
    private ProUserMapper proUserMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Value("${inquisition.dev_mode:false}")
    private boolean devMode;

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
            @NotNull Object handler) throws IOException {

        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 开发模式跳过jwt检查
        if (devMode) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.length() > 7) {
            token = token.substring(7);
        }

        HandlerMethod method = (HandlerMethod) handler;

        // ProKey验证
        var proKey = method.getMethod().getAnnotation(ProKey.class);
        if (proKey != null) {
            var proUser = proUserMapper.selectOne(
                    Wrappers.<ProUserEntity>lambdaQuery()
                            .eq(ProUserEntity::getAuthorization, token)
                            .eq(ProUserEntity::getPermission, "pro"));
            if (proUser == null) {
                writeAuthError(response, HttpServletResponse.SC_FORBIDDEN, PRO_KEY_INVALID_MSG);
                return false;
            }
        }

        // 管理员登陆验证
        var login = method.getMethod().getAnnotation(Login.class);
        if (login != null) {
            if (JWTUtils.verifyToken(token) && Objects.equals(JWTUtils.getType(Objects.requireNonNull(token)),
                    "admin")) {
                return true;
            } else {
                writeAuthError(response, HttpServletResponse.SC_UNAUTHORIZED, LOGIN_EXPIRED_MSG);
                return false;
            }
        }

        // 高级用户登陆验证
        var proUserLogin = method.getMethod().getAnnotation(ProUserLogin.class);
        if (proUserLogin != null) {
            if (JWTUtils.verifyToken(token) && Objects.equals(JWTUtils.getType(Objects.requireNonNull(token)),
                    "proUser")) {
                return true;
            } else {
                writeAuthError(response, HttpServletResponse.SC_UNAUTHORIZED, LOGIN_EXPIRED_MSG);
                return false;
            }
        }

        // 用户登陆验证
        var userLogin = method.getMethod().getAnnotation(UserLogin.class);
        if (userLogin != null) {
            if (JWTUtils.verifyToken(token) && Objects.equals(JWTUtils.getType(Objects.requireNonNull(token)),
                    "user")) {
                return true;
            } else {
                writeAuthError(response, HttpServletResponse.SC_UNAUTHORIZED, LOGIN_EXPIRED_MSG);
                return false;
            }
        }

        // 用户或代理登陆验证
        var userOrProUserLogin = method.getMethod().getAnnotation(UserOrProUserLogin.class);
        if (userOrProUserLogin != null) {
            if (JWTUtils.verifyToken(token)) {
                String type = JWTUtils.getType(Objects.requireNonNull(token));
                if ("user".equals(type) || "proUser".equals(type)) {
                    return true;
                }
            }
            writeAuthError(response, HttpServletResponse.SC_UNAUTHORIZED, LOGIN_EXPIRED_MSG);
            return false;
        }

        return true;
    }

    /**
     * 写出与全站一致的标准响应体。
     *
     * <p>此前仅设置状态码而不写响应体，前端无法从 {@code data.msg} 取到失败原因，
     * 只能靠状态码猜测，最终被归类成"网络错误"。
     * 这里保持 HTTP 状态码不变，仅补充 {@link Result} 结构，
     * 兼容既有按状态码处理的逻辑，同时让前端能拿到可读文案。
     *
     * @param httpStatus 与业务语义一致的 HTTP 状态码（401 未登录 / 403 无权限）
     * @param msg        提示文案，须与 WAF 拦截文案区分，避免前端误判
     */
    private void writeAuthError(HttpServletResponse response, int httpStatus, String msg) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.failed(httpStatus, msg));
    }
}
