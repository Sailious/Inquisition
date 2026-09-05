package moe.dazecake.inquisition.config;

import com.alibaba.druid.pool.DruidDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

@Configuration
@Primary
public class DatabaseInitConfig {
    /** MySQL 标识符（数据库名）最大长度，超过该长度数据库本身也会拒绝。 */
    private static final int MAX_DATABASE_NAME_LENGTH = 64;

    private final Logger log = LoggerFactory.getLogger(DatabaseInitConfig.class);

    @Value("${spring.datasource.url}")
    private String datasourceUrl;
    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;
    @Value("${spring.datasource.username}")
    private String username;
    @Value("${spring.datasource.password}")
    private String password;

    @Bean
    public DataSource dataSource() {
        DruidDataSource datasource = new DruidDataSource();

        datasource.setUrl(datasourceUrl);
        datasource.setUsername(username);
        datasource.setPassword(password);
        datasource.setDriverClassName(driverClassName);

        try {
            Class.forName(driverClassName);

            String url01 = datasourceUrl.substring(0, datasourceUrl.indexOf("?"));
            String url02 = url01.substring(0, url01.lastIndexOf("/"));
            String datasourceName = url01.substring(url01.lastIndexOf("/") + 1);

            // 校验数据库名合法：反引号已被用于包裹标识符，只需拦截反引号本身即可防止 SQL 逃逸。
            // 其余合法 MySQL 标识符字符（含连字符、点号、中文等）均放行。
            //
            // 说明：CREATE DATABASE 属 DDL，标识符无法使用 PreparedStatement 参数绑定，
            // 业界标准做法是「反引号包裹 + 严格白名单/黑名单校验」，此处即采用该方案。
            // 数据源来自部署配置（spring.datasource.url 中的 DB_NAME 环境变量），
            // 并非终端用户可控输入；以下校验为纵深防御，同时消除静态扫描告警。
            if (datasourceName.isEmpty()) {
                throw new IllegalArgumentException("数据库名不能为空");
            }
            // 反引号是唯一能逃逸出反引号包裹的字符，必须拒绝
            if (datasourceName.contains("`")) {
                throw new IllegalArgumentException("非法数据库名: " + datasourceName);
            }
            if (datasourceName.length() > MAX_DATABASE_NAME_LENGTH) {
                throw new IllegalArgumentException(
                        "数据库名长度超过上限 " + MAX_DATABASE_NAME_LENGTH + " 字符");
            }
            // 拒绝控制字符：避免换行/回车截断语句结构或污染日志输出
            for (int i = 0; i < datasourceName.length(); i++) {
                if (Character.isISOControl(datasourceName.charAt(i))) {
                    throw new IllegalArgumentException("数据库名不能包含控制字符");
                }
            }

            // 连接已经存在的数据库（如 mysql），在其中创建目标数据库。
            // 使用 try-with-resources 确保连接与语句被正确关闭。
            try (Connection connection = DriverManager.getConnection(url02, username, password);
                 Statement statement = connection.createStatement()) {

                statement.executeUpdate("create database if not exists `" + datasourceName + "` default character set " +
                        "utf8mb4 COLLATE utf8mb4_0900_ai_ci");
                log.info("【审判庭初始化】 创建数据库成功");
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("数据库驱动加载失败: " + driverClassName, e);
        } catch (Exception e) {
            throw new IllegalStateException("数据库初始化失败", e);
        }

        return datasource;
    }
}
