# Inquisition 技术治理文档

本仓库开发、代码审查与合并的**唯一规则依据**。

- 「后端开发工程师」「数据层工程师」「测试工程师」动手前必须先读本文对应章节。
- 「PR 审查工程师」以本文逐条核对，命中即记录 `file:行号`。
- 本文未覆盖之处，保守判定并显式标注「需人工复核」，不得臆断。

代码路径均相对于 `src/main/java/moe/dazecake/inquisition/`。

---

## 1. 技术栈与代码结构

| 项 | 值 |
| --- | --- |
| 语言 / 框架 | Java 11 + Spring Boot 2.7 |
| 持久层 | MyBatis-Plus 3.5.2 + MySQL 8 + Druid |
| 建表方式 | actable 自动建表（**无版本化迁移脚本**） |
| 部署形态 | 单容器，端口 2000 |
| 对外接口 | 88 个 |
| 接口文档 | `doc/api/openapi.json` |

分层与职责：

| 目录 | 职责 |
| --- | --- |
| `controller/` | 只做参数接收与结果返回 |
| `service/` + `service/impl/` | 业务逻辑唯一归宿 |
| `mapper/` | 数据访问，仅 SQL 与映射 |
| `model/` | 实体、VO、DTO、Convert |
| `utils/` | 工具类（含 `Result`、`JWTUtils`、`Encoder`） |
| `config/` | 框架配置（`WebConfig`、`MybatisConfig`、`OpenApiConfig`） |
| `filter/` | 拦截器（`JwtTokenInterceptor`） |
| `annotation/` | 自定义注解 |
| `constant/` | 常量（含 `ResponseCodeConstants`） |

业务域模块 Owner 代号：`AUTH-OWNER`（用户与权限）、`CORE-OWNER`（核心业务）、`DAL-OWNER`（数据访问）、`INTG-OWNER`（第三方对接）、`INFRA-OWNER`（基础设施）。

---

## 2. 分层红线

1. **Controller** 不得写业务判断、不得注入 Mapper、不得直接返回 Entity。
2. **Service** 经 `*Convert` 转 VO 后再对外返回，不得把 Entity 直接透出。
3. **Mapper** 只使用 `#{}` 参数占位，**禁止 `${}` 拼接**（SQL 注入）。
4. 跨层调用只能自上而下，禁止反向依赖。

---

## 3. 量化红线

| 指标 | 上限 |
| --- | --- |
| 单方法行数 | 80 行 |
| 单类行数 | 500 行 |
| 方法参数个数 | 5 个（超出须封装 DTO） |

超出即视为违规，须拆分或重构后再提交。

---

## 4. 响应与异常

1. 统一使用 `utils/Result.java` 的静态工厂返回，不得自行 new 响应体、不得裸返回 Map/String 充当响应。
   可用工厂：`success()`、`success(data, msg)`、`repeatSuccess()`、`isSuccess(flag)`、`paramError()`、`unauthorized()`、`forbidden()`、`notFound()`、`failed()`、`failed(code, msg)`。
2. **禁止裸抛异常到 Controller 之外**，须在 Service 层捕获并转为 `Result`。
3. **禁止吞异常**：至少记录 `log.error("上下文说明", e)`，禁止空 catch 块。

---

## 5. 敏感信息

1. 密钥、URL、Token 一律通过 `@Value("${...}")` 注入，**禁止硬编码**。
2. 日志**禁止**打印 `token`、`password`、`deviceToken`、CDK 明文。
3. 新增配置项须同步至项目根目录的 `application.yml.example` 与部署文档 `doc/FastDeploy.md`。

---

## 6. 并发安全

1. 共享容器使用 `ConcurrentHashMap` / `CopyOnWriteArrayList` 等并发集合。
2. **禁止在 for-each 遍历中 put/remove 同一容器**（会触发 `ConcurrentModificationException`）；应先快照后修改，或改用 `compute`。
3. 共享状态的多线程读写须显式加锁或使用原子类。

---

## 7. 定时任务

1. 新增/修改定时任务须在 PR 描述中说明**触发频率与单轮耗时**（单轮 ≤ 3 秒）。
2. 任务 lambda 体内**必须 try-catch**，否则调度线程会静默停摆且无日志。
3. 涉及 `utils/DynamicScheduleTask.java` 的改动按高危文件处理。

---

## 8. 高危文件清单

以下文件/目录被改动时，**必须单独出 PR、在描述中显式标注、不自行合并**，且须人工会签：

| 路径 | 风险点 |
| --- | --- |
| `annotation/*` | 影响全局切面行为 |
| `filter/JwtTokenInterceptor.java` | 鉴权边界，改错即全站失守 |
| `utils/JWTUtils.java` | Token 签发与校验 |
| `utils/Encoder.java` | 加解密 |
| `service/impl/TaskServiceImpl.java` | 核心任务派发与超时回收 |
| `utils/DynamicScheduleTask.java` | 动态调度，异常会静默停摆 |
| `config/WebConfig.java` | 拦截器与跨域配置 |
| `build.gradle` | 依赖与构建，影响全局 |

即使其余条款全部通过，触及高危文件却未标注/未会签的 PR **不得放行**。

---

## 9. 接口契约

1. 改动对外接口后**必须同步更新** `doc/api/openapi.json`。
2. 破坏性变更——删接口、删字段、改类型、收紧权限——**必须单独出 PR** 并在描述中说明影响范围与迁移方式。
3. 破坏性变更应排在最后单独合并，不与普通改动混在一起。

---

## 10. Schema 变更与数据迁移

本仓库由 actable 自动建表，**没有版本化迁移脚本**，升级失败意味着存量数据损坏。因此：

1. 任何 schema 变更**必须向后兼容、必须可回滚**。
2. PR 必须同时提供**人工 SQL** 与**回滚 SQL**。
3. 加字段须**可空或有默认值**；删字段须先停止读写、保留一个完整版本后再删。
4. 评估 actable 自动建表对既有表的影响，避免意外 DDL；不确定时优先给手写迁移 SQL。
5. 执行 UPDATE / DELETE 前先 SELECT 确认影响行数并说明影响范围。
6. 批量操作注意批次大小与事务边界；SQL 避免无索引大表扫描。

---

## 11. 测试规范

现状：主库 165 个 Java 文件、88 个接口，全仓仅 3 个测试类，`service/impl/TaskServiceImpl.java` 与 `filter/JwtTokenInterceptor.java` 零测试。补测试是常态需求。

1. **隔离外部依赖**：COS、七牛、WxPusher、邮件、外部 HTTP 一律 mock，禁止单测真实调用。
2. **断言质量**：每个测试方法至少 3 个有效断言；**测行为，不测实现细节**。
3. **覆盖率只增不减**：新增代码必须带测试，不允许靠删测试过关。
4. **测试先行**：缺陷修复先写能复现失败的红测试，修复后转绿。
5. 优先补测：`TaskServiceImpl` 派发与超时回收、`JwtTokenInterceptor` 鉴权分支、CDK 核销、定时任务、`Result` 统一响应。
6. 保证 `gradle test` 全绿。

---

## 12. 提交规范

1. 遵循 **Conventional Commits**（`feat` / `fix` / `refactor` / `test` / `docs` / `chore` …）。
2. `scope` 使用业务域或层次：`auth` / `user` / `task` / `device` / `cdk` / `goods` / `dal` / `entity` / `test` / `ci` / `infra`。
3. **每个 commit 必须关联 Issue 号**。
4. 提交前确保编译通过、无新增告警。

---

## 13. 分支与协作约定

1. **集成分支命名**：`dev/issue-{N}`，`{N}` 为 Issue 编号。分支名内嵌 Issue 号，供流水线反解 Issue 以校验审查结论，**不得随意改名**。
2. 同一 Issue 的所有子任务在**同一条集成分支**上开发，最终只出一个 PR。
3. 推送前先 `git pull --rebase`，避免多人协作互相覆盖。
4. 有依赖的子任务按「数据迁移 → 业务实现 → 测试回归」串行；无依赖的可并行。
5. 完工后在 Issue 评论 @「任务调度工程师」回报，由调度师确认全部完成后再进入审查。
6. **审查通过是创建 PR 的唯一前提**，任何角色不得在审查结论出具前创建 PR。
7. 合并需双道同意：「PR 审查工程师」判定通过 **且** 人工 approve，缺一不合并。
8. 集成分支在审查通过后若再有新推送，审查结论自动失效，须重新回报与审查。
