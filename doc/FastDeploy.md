# 快速部署

出于易用性考虑，Inquisition 提供了docker部署服务，您可以使用docker快速部署 Inquisition 和所需要的 MySQL 环境，免去了环境配置的麻烦，本文档将提供基于`Ubuntu 20.04`的完整快速部署流程，`Windows`环境请使用 [Docker Desktop](https://www.docker.com/get-started/) ，通过本文，您可以实现快速在服务器上配置Inquisition

## Docker安装

快速部署服务依赖于 docker

```shell
# 一键安装命令
curl -sSL https://get.daocloud.io/docker | sh
```

## MySQL安装

由于docker容器自动销毁，我们需要将数据库文件挂载到本地

```shell
# 创建本地挂载路径
mkdir -p /usr/local/mysql/conf
mkdir -p /usr/local/mysql/data
mkdir -p /usr/local/mysql/logs
```

设置配置文件

```shell
vim /usr/local/mysql/conf/my.cnf
```

按`i`进入编辑模式，将以下内容粘贴进去

```
[client]
default-character-set = utf8mb4

[mysqld]
pid-file        = /var/run/mysqld/mysqld.pid
socket          = /var/run/mysqld/mysqld.sock
datadir         = /var/lib/mysql
secure-file-priv= NULL
# Disabling symbolic-links is recommended to prevent assorted security risks
symbolic-links=0

# Custom config should go here
# 字符集
character_set_server=utf8
collation-server=utf8_general_ci

# 是否对sql语句大小写敏感，1表示不敏感
lower_case_table_names = 1

# 最大连接数
max_connections = 1000

# Innodb缓存池大小
innodb_buffer_pool_size = 4G

# 表文件描述符的缓存大小
table_open_cache_instances=1
table_open_cache=2000
table_definition_cache=2000

!includedir /etc/mysql/conf.d/
```

按`Esc`，输入`:wq`保存

创建多容器通信网络

```shell
docker network create aegirtech-net
```

拉取数据库镜像

```shell
docker run -p 3306:3306 --name inquisition-mysql \
--network aegirtech-net \
--network-alias inquisition-mysql \
-v /usr/local/mysql/conf/my.cnf:/etc/mysql/my.cnf \
-v /usr/local/mysql/logs:/logs \
-v /usr/local/mysql/data/mysql:/var/lib/mysql \
-e MYSQL_ROOT_PASSWORD=123456 \
-d mysql:8 \
--character-set-server=utf8mb4 \
--collation-server=utf8mb4_general_ci \
--default_authentication_plugin=mysql_native_password
```

至此，数据库配置已经全部完成

## Inquisition安装

同样使用docker，仅使用默认配置的话，只需要执行

```shell
docker run -d -p 2000:2000 --name inquisition --network aegirtech-net dazecake/inquisition:latest
```

访问`http://服务器IP:2000/swagger-ui/index.html`检查是否部署成功

同时可以在`管理员登陆接口`使用默认账号登陆检查是否成功连接至数据库

默认账号: `root`

默认密码: `123456`

成功将返回的响应

```json
{
  "code": 200,
  "msg": "login success",
  "data": {
    "token": "xxxxxxxxxxxxxxx"
  }
}
```

如果以上测试均成功，恭喜你，你已经正确的安装了Inquisition与其所需依赖的数据库

## 进阶

### 前端部署

单纯的后端部署完成后无法正常使用，您需要部署前端以获取图形化的操作界面

Inquisition 的前端实现为 [IberiaEye 伊比利亚之眼](https://github.com/AegirTech/IberiaEye)


### 自定义配置

默认的docker容器使用默认配置，如果您需要开启邮件推送或修改其他任意设置，需要进行[目录挂载](https://docker.easydoc.net/doc/81170005/cCewZWoN/kze7f0ZR)

创建自定义配置文件 [配置文件参考](https://github.com/AegirTech/Inquisition/blob/main/src/main/resources/application.yml)

```shell
vim /usr/local/inquisition/config/application.yml
```

编辑并保存，停止原先运行的容器，增加启动参数

```shell
docker run -d -p 2000:2000 --name inquisition --network aegirtech-net \
-v /usr/local/inquisition/config:/config \
dazecake/inquisition:latest
```

此时 Inquisition 将以自定义配置运行

### 升级

```shell
# 停止并删除旧容器
docker stop inquisition
docker rm inquisition

# 更新容器
docker pull dazecake/inquisition:latest

# 重新运行
docker run -d -p 2000:2000 --name inquisition --network aegirtech-net \
-v /usr/local/inquisition/config:/config \
dazecake/inquisition:latest
```

### 日志定时清理

日志表（`log`）会随托管任务持续增长，内置了定时清理任务，默认每天凌晨 3 点**物理删除**超过保留天数的日志。

> 注意：该操作是**不可逆的物理删除**，且**默认关闭**。请确认留存合规要求后，再显式设置 `INQUISITION_LOG_CLEAN_ENABLED=true` 开启。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `INQUISITION_LOG_CLEAN_ENABLED` | `false` | 是否启用，默认关闭 |
| `INQUISITION_LOG_RETENTION_DAYS` | `30` | 保留天数，**必须大于 0**；为 0 或负数时任务跳过执行 |
| `INQUISITION_LOG_CLEAN_CRON` | `0 0 3 * * ?` | 执行周期，留空或非法时回退默认值并打印警告 |
| `INQUISITION_LOG_CLEAN_BATCH_SIZE` | `1000` | 单批删除条数 |
| `INQUISITION_LOG_CLEAN_MAX_BATCHES` | `20` | 单轮最多批次数，剩余留待下一轮 |

#### 存量库请先手工创建索引

清理语句形如 `DELETE FROM log WHERE time < ? LIMIT 1000`，若 `time` 列无索引将退化为全表扫描，形成长事务并阻塞日志写入。

实体已声明 `@Index`，actable 生成的索引名为 **`actable_idx_time`**。对于已积累大量数据的存量库，请**先**在低峰期手工创建同名索引，再部署新版本——否则 actable 会在**应用启动阶段**执行 `CREATE INDEX`，大表上可能导致启动长时间阻塞。

```sql
-- 执行前确认没有长事务占用 log 表的 MDL 锁
SELECT trx_id, trx_started, trx_state, LEFT(trx_query, 120)
FROM information_schema.innodb_trx ORDER BY trx_started LIMIT 10;

-- MySQL 8 支持 INPLACE + LOCK=NONE，全程不阻塞 DML
ALTER TABLE `log` ADD INDEX `actable_idx_time` (`time`), ALGORITHM=INPLACE, LOCK=NONE;

-- 确认索引已生效
SHOW INDEX FROM `log` WHERE Key_name = 'actable_idx_time';
```

索引名**必须**与上述保持一致：actable 在 `update` 模式下会删除库中已存在、但实体未声明的 `actable_idx_` 前缀索引。手工预先创建同名索引后，actable 检测到已存在，既不会重建也不会删除。

若希望 actable 完全不介入索引管理，可改用非 `actable_idx_` 前缀的名称（如 `idx_log_time`），actable 不会识别也不会删除它，代价是新环境需按上述步骤手工创建。

#### 部署约束

清理任务与本应用其它定时任务共用 Spring 默认调度线程（单线程），因此单轮清理受 `MAX_BATCHES` 限制，避免阻塞每 5 秒的设备离线监控和每 6 秒的理智刷新。若部署多个实例，各实例会并发清理同一区间，建议仅单实例部署，或只在其中一个实例上开启该任务。