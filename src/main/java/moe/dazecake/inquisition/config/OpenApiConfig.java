package moe.dazecake.inquisition.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class OpenApiConfig {

        private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

        @Bean
        public OpenAPI customOpenAPI() {
                return new OpenAPI()
                                .info(new Info()
                                                .title("Inquisition API")
                                                .version("1.3.1")
                                                .description("## 明日方舟托管系统API文档\n\n" +
                                                                "### 功能特性\n" +
                                                                "- 账号管理\n" +
                                                                "- 任务执行\n" +
                                                                "- 设备管理\n" +
                                                                "- 多渠道通知（微信、QQ、邮件）\n" +
                                                                "- 数据统计\n\n" +
                                                                "### 认证方式\n" +
                                                                "使用JWT Bearer Token进行身份认证。\n\n" +
                                                                "### 默认账号\n" +
                                                                "- 管理员账号: `root`\n" +
                                                                "- 管理员密码: `123456`")
                                                .contact(new Contact()
                                                                .name("Sailious")
                                                                .url("https://github.com/AegirTech/Inquisition")
                                                                .email("contact@example.com"))
                                                .license(new License()
                                                                .name("MIT License")
                                                                .url("https://opensource.org/licenses/MIT")))
                                .servers(Arrays.asList(
                                                new Server().url("http://localhost:2000").description("本地开发环境"),
                                                new Server().url("https://api.sailwertech.top").description("生产环境")))
                                .externalDocs(new ExternalDocumentation()
                                                .description("GitHub仓库")
                                                .url("https://github.com/AegirTech/Inquisition"))
                                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                                .components(new Components()
                                                .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                                                new SecurityScheme()
                                                                                .name(SECURITY_SCHEME_NAME)
                                                                                .type(SecurityScheme.Type.HTTP)
                                                                                .scheme("bearer")
                                                                                .bearerFormat("JWT")
                                                                                .description("请输入JWT Token，格式：Bearer {token}。\n\n"
                                                                                                +
                                                                                                "获取Token方式：\n" +
                                                                                                "1. 管理员登录: POST /adminLogin\n"
                                                                                                +
                                                                                                "2. 用户登录: POST /userLogin")));
        }
}
