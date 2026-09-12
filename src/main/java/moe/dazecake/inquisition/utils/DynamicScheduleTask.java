package moe.dazecake.inquisition.utils;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import moe.dazecake.inquisition.mapper.AccountMapper;
import moe.dazecake.inquisition.mapper.DeviceMapper;
import moe.dazecake.inquisition.mapper.LogMapper;
import moe.dazecake.inquisition.model.entity.AccountEntity;
import moe.dazecake.inquisition.model.entity.DeviceEntity;
import moe.dazecake.inquisition.model.entity.LogEntity;
import moe.dazecake.inquisition.service.impl.LogServiceImpl;
import moe.dazecake.inquisition.service.impl.MessageServiceImpl;
import moe.dazecake.inquisition.service.impl.TaskServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;

@Slf4j
@Configuration
@EnableScheduling
public class DynamicScheduleTask implements SchedulingConfigurer {

    /** 日志清理的兜底 cron，配置留空或非法时回退使用 */
    private static final String DEFAULT_LOG_CLEAN_CRON = "0 0 3 * * ?";

    /** 日志清理分批删除的默认单批条数 */
    private static final int DEFAULT_LOG_CLEAN_BATCH_SIZE = 1000;

    /** 日志清理单轮默认最多执行的批次数 */
    private static final int DEFAULT_LOG_CLEAN_MAX_BATCHES = 20;

    /**
     * 设备计数器触发"自动移除"的阈值。
     *
     * <p>计算依据：巡检任务每 5 秒执行一次，每次将计数器减 1。
     * 5 天 = 432000 秒，432000 / 5 = 86400 次递减；
     * 计数器从心跳写入的 3 开始递减，因此递减到 -86400 时约为 5 天。
     *
     * <p>注意：必须是负值。计数器是递减的，写成正数永远不会命中（历史遗留 bug）。
     */
    private static final int DEVICE_EXPIRE_COUNT = -86400;

    @Resource
    DynamicInfo dynamicInfo;

    @Resource
    AccountMapper accountMapper;

    @Resource
    DeviceMapper deviceMapper;

    @Resource
    LogMapper logMapper;

    @Resource
    LogServiceImpl logService;

    @Resource
    MessageServiceImpl messageService;

    @Resource
    TaskServiceImpl taskService;

    @Value("${spring.mail.to:}")
    String to;

    @Value("${spring.mail.enable:false}")
    boolean enableMail;

    @Value("${wx-pusher.enable:false}")
    boolean enableWxPusher;

    // 日志定时清理配置
    @Value("${inquisition.log.clean.enabled:false}")
    boolean logCleanEnabled;

    @Value("${inquisition.log.clean.retention-days:30}")
    int logRetentionDays;

    @Value("${inquisition.log.clean.cron:0 0 3 * * ?}")
    String logCleanCron;

    @Value("${inquisition.log.clean.batch-size:1000}")
    int logCleanBatchSize;

    @Value("${inquisition.log.clean.max-batches:20}")
    int logCleanMaxBatches;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        //队列巡检
        taskRegistrar.addTriggerTask(
                () -> {
                    //log.info("正在巡检队列: " + LocalDateTime.now().toLocalTime());
                    //检查等待队列中是否存在重复项，若存在则删除多余的重复项
                    // 用 removeIf 替代 clear + addAll：CopyOnWriteArrayList 的 removeIf 是原子操作
                    //（加锁后一次性替换底层数组），读者只会看到旧数组或新数组，不会看到中间的空队列；
                    // 且无重复项时不产生任何写操作
                    var seen = new HashSet<Long>();
                    dynamicInfo.getWaitUserList().removeIf(id -> !seen.add(id));
                },
                triggerContext -> new CronTrigger("0 */1 * * * *").nextExecutionTime(triggerContext)
        );
        //理智刷新
        taskRegistrar.addTriggerTask(
                () -> {
                    //log.info("正在刷新用户理智: " + LocalDateTime.now().toLocalTime());
                    taskService.calculatingSan();
                },
                triggerContext -> new CronTrigger("0 */6 * * * *").nextExecutionTime(triggerContext)
        );
        //设备离线监控
        taskRegistrar.addTriggerTask(
                () -> {
                    // 本轮判定为超时、需移除的设备，先记录下来，遍历结束后统一处理
                    ArrayList<String> expiredTokens = new ArrayList<>();

                    for (java.util.Map.Entry<String, Integer> count : dynamicInfo.getDeviceCounterMap().entrySet()) {

                        var token = count.getKey();

                        // 计数器递减：用 computeIfPresent 保证"读-改-写"原子。
                        // 原先的 --num; put(...) 会与设备心跳线程（每 5 秒 put(token, 3)）相互覆盖，
                        // 导致心跳被吞掉、设备被误判离线
                        Integer num = dynamicInfo.getDeviceCounterMap().computeIfPresent(token, (k, v) -> v - 1);
                        if (num == null) {
                            // 已被其它线程移除，跳过
                            continue;
                        }

                        if (num == 0) {
                            dynamicInfo.getDeviceStatusMap().put(token, 0);
//                            log.warn("设备离线: " + token);
                        } else if (num == -60) {
                            //重连超时提示
                            var device = deviceMapper.selectOne(
                                    Wrappers.<DeviceEntity>lambdaQuery()
                                            .eq(DeviceEntity::getDeviceToken, token)
                            );

                            //记录日志
                            logService.logWarn("设备离线", "设备名称: " + device.getDeviceName() + "\n" +
                                    "设备token: " + device.getDeviceToken() + "\n");

                            //邮件通知
                            messageService.pushAdmin("[审判庭] 设备离线", "设备名称: " + device.getDeviceName() + "\n"
                                    + "设备token: " + device.getDeviceToken() + "\n"
                                    + "时间: " + LocalDateTime.now() + "\n");

                        } else if (num == DEVICE_EXPIRE_COUNT) {
                            //设备离线约5天，仅记录待移除，避免遍历过程中结构性修改集合
                            expiredTokens.add(token);
                        }
                    }

                    // 移除离线约5天的设备：此时已退出遍历，可安全做结构性修改
                    for (String token : expiredTokens) {
                        dynamicInfo.getDeviceStatusMap().remove(token);
                        dynamicInfo.getDeviceCounterMap().remove(token);

                        var device = deviceMapper.selectOne(
                                Wrappers.<DeviceEntity>lambdaQuery()
                                        .eq(DeviceEntity::getDeviceToken, token)
                        );
                        if (device == null) {
                            // 数据库中已无对应记录（可能已被手动删除），
                            // 内存态已清理，跳过即可，避免 NPE 中断后续设备的处理
                            continue;
                        }
                        device.setDelete(1);
                        deviceMapper.updateById(device);

                        //记录日志
                        logService.logWarn("设备移除", "设备名称: " + device.getDeviceName() + "\n" +
                                "设备token: " + device.getDeviceToken() + "\n");

                        //邮件通知
                        messageService.pushAdmin("[审判庭] 设备移除", "设备名称: " + device.getDeviceName() + "\n"
                                + "设备token: " + device.getDeviceToken() + "\n"
                                + "时间: " + LocalDateTime.now() + "\n");
                    }
                },
                triggerContext -> new CronTrigger("0/5 * * * * ?").nextExecutionTime(triggerContext)
        );
        //任务超时检测
        taskRegistrar.addTriggerTask(
                () -> {
                    if (dynamicInfo.getActive()) {
                        //log.info("任务超时检测");
                        LocalDateTime nowTime = LocalDateTime.now();
                        int num = 0;
                        synchronized (dynamicInfo.getWorkUserList()) {
                            for (Long worker : dynamicInfo.getWorkUserList()) {
                                // 直接取过期时间判空，取代 containsKey + get 两步写法：
                                // 两步之间存在竞态窗口，期间条目可能已被移除导致 get 返回 null
                                var expireTime = dynamicInfo.getWorkUserExpireTime(worker);
                                if (expireTime == null) {
                                    continue;
                                }
                                if (expireTime.isBefore(nowTime)) {
                                    //记录日志
                                    logService.logWarn("任务超时", "");
                                    taskService.forceHaltTask(worker);
                                    num++;
                                }
                            }
                        }
                        if (num > 0) {
                            log.info("【审判庭】 已处理超时任务数: " + num);
                        }
                    }
                },
                triggerContext -> new CronTrigger("0 0/5 * * * ?").nextExecutionTime(triggerContext)
        );
        //账号过期检测
        taskRegistrar.addTriggerTask(
                () -> {
                    log.info("【审判庭】 账号过期检测");
                    var finalTime = LocalDateTime.now().plusDays(7);
                    var accountList = accountMapper.selectList(Wrappers.<AccountEntity>lambdaQuery()
                            .lt(AccountEntity::getExpireTime, finalTime)
                            .gt(AccountEntity::getExpireTime, LocalDateTime.now())
                            .eq(AccountEntity::getDelete, 0));
                    accountList.forEach(
                            (account) -> {
                                log.info("【临期账号】: " + account.getName() + "\t" + account.getAccount());
                                var msg = "您的托管账号将于" + account.getExpireTime()
                                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "过期，记得及时续费哦。";

                                messageService.push(account, "【明日方舟】托管续费提醒", msg);
                            }
                    );
                },
                triggerContext -> new CronTrigger("0 0 20 * * ?").nextExecutionTime(triggerContext)
        );
        //账号冻结检测
        taskRegistrar.addTriggerTask(
                () -> {
                    log.info("【审判庭】 账号冻结检测");
                    var accountList = accountMapper.selectList(Wrappers.<AccountEntity>lambdaQuery()
                            .gt(AccountEntity::getExpireTime, LocalDateTime.now())
                            .eq(AccountEntity::getFreeze, 1)
                            .eq(AccountEntity::getDelete, 0));
                    accountList.forEach(
                            (account) -> {
                                log.info("【冻结账号】: " + account.getName() + "\t" + account.getAccount());
                                var msg = "您的账号仍处于冻结状态，若非手动冻结请及时检查账号状态，避免浪费账号托管时长";

                                messageService.push(account, "【明日方舟】账号冻结提醒", msg);

                            }
                    );
                },
                triggerContext -> new CronTrigger("0 0 20 * * ?").nextExecutionTime(triggerContext)
        );
        //每日刷新次数更新
        taskRegistrar.addTriggerTask(
                () -> {
                    log.info("【审判庭】 每日刷新次数更新");
                    var accountList = accountMapper.selectList(Wrappers.<AccountEntity>lambdaQuery()
                            .le(AccountEntity::getRefresh, 0)
                            .eq(AccountEntity::getDelete, 0)
                            .ge(AccountEntity::getExpireTime, LocalDateTime.now())
                    );
                    accountList.forEach(
                            (account) -> {
                                account.setRefresh(1);
                                accountMapper.updateById(account);
                            }
                    );
                },
                triggerContext -> new CronTrigger("0 0 0 * * ?").nextExecutionTime(triggerContext)
        );
        //异常账号检测
        taskRegistrar.addTriggerTask(
                () -> {
                    log.info("【异常账号检测】 检测开始");
                    var accountList = accountMapper.selectList(Wrappers.<AccountEntity>lambdaQuery()
                            .eq(AccountEntity::getFreeze, 0)
                            .eq(AccountEntity::getDelete, 0)
                            .ge(AccountEntity::getExpireTime, LocalDateTime.now())
                    );
                    accountList.forEach(
                            (account) -> {
                                if (!dynamicInfo.getUserSanInfoMap().containsKey(account.getId())) {
                                    log.info("【异常账号检测】 异常账号: " + account.getAccount() + " " + account.getAccount());
                                    dynamicInfo.setUserSan(account.getId(), 135, 135);
                                }
                            }
                    );
                    log.info("【异常账号检测】 已完成所有异常账号自动检修");
                },
                triggerContext -> new CronTrigger("0 0 4 * * ?").nextExecutionTime(triggerContext)
        );
        //日志定时清理
        String cleanCron = resolveCleanCron(logCleanCron);
        taskRegistrar.addTriggerTask(
                () -> {
                    if (logCleanEnabled) {
                        try {
                            cleanExpiredLogs(logRetentionDays, logCleanBatchSize, logCleanMaxBatches);
                        } catch (Exception e) {
                            log.error("【日志清理】 执行失败", e);
                        }
                    }
                },
                triggerContext -> new CronTrigger(cleanCron).nextExecutionTime(triggerContext)
        );
    }

    /**
     * 分批物理清理过期日志。
     *
     * <p>分批的原因：log 表写入频繁，单次大范围删除会形成长事务并长时间占用调度线程，
     * 导致同线程上的高频巡检任务（每 5 秒的设备离线监控、每 6 秒的理智刷新）停摆，
     * 进而误报设备离线并触发告警推送。因此限制单批条数与单轮批次数，
     * 本轮未清理完的部分留待下一轮调度继续。
     *
     * @param retentionDays 日志保留天数，必须大于 0，否则跳过清理
     * @param batchSize     单批删除条数，小于等于 0 时回退为 {@link #DEFAULT_LOG_CLEAN_BATCH_SIZE}
     * @param maxBatches    单轮最多执行的批次数，小于等于 0 时回退为 {@link #DEFAULT_LOG_CLEAN_MAX_BATCHES}
     * @return 本轮实际删除的总条数
     */
    int cleanExpiredLogs(int retentionDays, int batchSize, int maxBatches) {
        // retention-days 为 0 等价于清空整表，必须拦截
        if (retentionDays <= 0) {
            log.warn("【日志清理】 retention-days 配置非法: {}，必须大于 0，已跳过本次清理", retentionDays);
            return 0;
        }

        int size = batchSize > 0 ? batchSize : DEFAULT_LOG_CLEAN_BATCH_SIZE;
        int batches = maxBatches > 0 ? maxBatches : DEFAULT_LOG_CLEAN_MAX_BATCHES;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

        int total = 0;
        for (int i = 0; i < batches; i++) {
            int deleted = logMapper.delete(Wrappers.<LogEntity>lambdaQuery()
                    .lt(LogEntity::getTime, cutoff)
                    .last("LIMIT " + size));
            total += deleted;
            // 本批未填满说明已无符合条件的数据，无需再查下一批
            if (deleted < size) {
                break;
            }
        }
        log.info("【日志清理】 本轮清理 {} 条早于 {} 的日志", total, cutoff);
        return total;
    }

    /**
     * 校验日志清理的 cron 表达式，留空或非法时回退默认值。
     *
     * <p>cron 在任务注册阶段就会被解析，非法表达式会直接导致应用启动失败，
     * 因此这里提前兜底，避免一个配置错误拖垮整个应用启动。
     */
    private String resolveCleanCron(String cron) {
        if (cron == null || cron.trim().isEmpty()) {
            log.warn("【日志清理】 cron 配置为空，已回退默认值: {}", DEFAULT_LOG_CLEAN_CRON);
            return DEFAULT_LOG_CLEAN_CRON;
        }
        try {
            new CronTrigger(cron);
            return cron;
        } catch (IllegalArgumentException e) {
            log.warn("【日志清理】 cron 表达式非法: {}，已回退默认值: {}", cron, DEFAULT_LOG_CLEAN_CRON);
            return DEFAULT_LOG_CLEAN_CRON;
        }
    }
}
