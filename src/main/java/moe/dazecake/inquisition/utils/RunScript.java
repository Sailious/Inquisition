package moe.dazecake.inquisition.utils;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import moe.dazecake.inquisition.mapper.AccountMapper;
import moe.dazecake.inquisition.mapper.AdminMapper;
import moe.dazecake.inquisition.mapper.DeviceMapper;
import moe.dazecake.inquisition.model.entity.AccountEntity;
import moe.dazecake.inquisition.model.entity.AdminEntity;
import moe.dazecake.inquisition.model.entity.DeviceEntity;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
@Slf4j
public class RunScript implements ApplicationRunner {

    @Resource
    DynamicInfo dynamicInfo;

    @Resource
    AccountMapper accountMapper;

    @Resource
    DeviceMapper deviceMapper;

    @Resource
    AdminMapper adminMapper;

    @Value("${inquisition.secret:}")
    String secret;

    @Value("${inquisition.dev_mode:false}")
    boolean devMode;

    /** 初始管理员账号名，默认 root */
    @Value("${inquisition.admin.init-username:root}")
    String adminInitUsername;

    /**
     * 初始管理员密码。配置后密码不会输出到日志；未配置时才随机生成并打印。
     */
    @Value("${inquisition.admin.init-password:}")
    String adminInitPassword;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("【审判庭初始化】 执行中...");

        // C3 修复：强制校验 JWT secret，禁止弱密钥启动
        validateAndInitSecret();

        // H4 修复：开发模式告警
        if (devMode) {
            log.warn("【安全警告】 dev_mode 已开启，所有 JWT 鉴权将被跳过！请仅在本地调试使用，生产环境严禁开启。");
        }

        File file = new File(System.getProperty("user.dir") + File.separator + "config" + File.separator + "data.json");
        if (file.exists()) {
            log.info("【审判庭初始化】 检测到数据文件，正在读取...");
            Gson gson = new Gson();
            dynamicInfo.load(gson.fromJson(new BufferedReader(new FileReader(file)), MemoryInfo.class));

            log.info("【审判庭初始化】 读取完成");
        } else {
            log.info("【审判庭初始化】 未检测到数据文件，正在初始化...");
            // 检查admin表是否有数据
            List<AdminEntity> adminEntities = adminMapper.selectList(null);
            if (adminEntities.isEmpty()) {
                // C5 修复：移除硬编码的默认弱口令哈希
                // 优先使用环境变量配置的初始密码（不落日志），未配置时才随机生成
                String username = StringUtils.hasText(adminInitUsername) ? adminInitUsername.trim() : "root";
                AdminEntity adminEntity = new AdminEntity();
                adminEntity.setUsername(username);
                adminEntity.setPermission("root");

                boolean useConfigured = StringUtils.hasText(adminInitPassword);
                String initPassword = useConfigured ? adminInitPassword
                        : RandomStringUtils.randomAlphanumeric(24);
                // 注意：BCrypt 只哈希密码本身，不要拼接 username，否则与登录校验不一致
                adminEntity.setPassword(Encoder.BCrypt(initPassword));
                adminMapper.insert(adminEntity);

                log.warn("【安全提示】 已创建初始管理员账号: root，请首次登录后立即修改密码");
                if (useConfigured) {
                    log.warn("【安全提示】 初始密码来源: 环境变量 INQUISITION_ADMIN_INIT_PASSWORD（未写入日志）");
                } else {
                    log.warn("【安全提示】 初始密码（随机生成，仅显示一次）: {}", initPassword);
                    log.warn("【安全警告】 该密码已明文输出到日志，请立即登录修改并清理本条日志");
                }
            }

            var devices = deviceMapper.selectList(
                    Wrappers.<DeviceEntity>lambdaQuery()
                            .eq(DeviceEntity::getDelete, 0)
            );
            devices.forEach(
                    device -> {
                        dynamicInfo.getDeviceStatusMap().put(device.getDeviceToken(), 0);
                        dynamicInfo.getDeviceCounterMap().put(device.getDeviceToken(), 1);
                    }
            );
            for (AccountEntity account : accountMapper.selectList(Wrappers.<AccountEntity>lambdaQuery()
                    .eq(AccountEntity::getDelete, 0)
                    .eq(AccountEntity::getFreeze, 0)
                    .eq(AccountEntity::getTaskType, "daily")
                    .ge(AccountEntity::getExpireTime, LocalDateTime.now())
            )) {
                dynamicInfo.setUserSanZero(account.getId());
            }

        }

        log.info("【审判庭初始化】 初始化完成");
    }

    /**
     * C3 修复：校验并初始化 JWT secret。
     * 要求：配置 inquisition.secret 且不少于32字符，否则拒绝启动。
     */
    private void validateAndInitSecret() {
        if (secret == null || secret.isEmpty()) {
            log.error("【安全错误】 未配置 inquisition.secret，服务拒绝启动。");
            log.error("【安全错误】 请通过环境变量 INQUISITION_SECRET 设置不少于32字符的随机字符串。");
            throw new IllegalStateException("JWT secret is not configured. Set INQUISITION_SECRET env var (>=32 chars).");
        }
        if (secret.length() < 32) {
            log.error("【安全错误】 inquisition.secret 长度不足32字符（当前{}），服务拒绝启动。", secret.length());
            throw new IllegalStateException("JWT secret too short. Must be >= 32 chars.");
        }
        // 弱口令检测
        if ("xxx".equals(secret) || "secret".equalsIgnoreCase(secret) || "123456".equals(secret)) {
            log.error("【安全错误】 检测到弱 secret，服务拒绝启动。");
            throw new IllegalStateException("Weak JWT secret detected. Use a strong random string.");
        }
        // 注意：不再将 secret 打印到日志
        JWTUtils.initSecret(secret);
        Encoder.initAesKey(secret);
        log.info("【审判庭初始化】 JWT secret 已加载（长度 {}）", secret.length());
    }

    @PreDestroy
    public void destroy() {
        log.info("【审判庭关闭】 正在保存数据...");
        Gson gson = new Gson();
        String str = gson.toJson(dynamicInfo.dump());
        try {
            var printWriter = new PrintWriter(System.getProperty("user.dir") + File.separator + "config" + File.separator + "data.json");
            printWriter.write(str);
            printWriter.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        log.info("【审判庭关闭】 数据保存完毕");
        log.info("【审判庭关闭】 服务端已正常关闭");
    }
}
