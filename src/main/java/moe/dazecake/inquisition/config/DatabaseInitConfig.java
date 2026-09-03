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
            if (datasourceName.isEmpty()) {
                throw new IllegalArgumentException("数据库名不能为空");
            }
            if (datasourceName.contains("`")) {
                throw new IllegalArgumentException("非法数据库名: " + datasourceName);
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
