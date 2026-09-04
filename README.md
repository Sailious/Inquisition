# Inquisition 审判庭

明日方舟速通云控后端

## 什么是 Inquisition 审判庭

Inquisition 是一款基于 [ArkLights](https://github.com/tkkcc/ArkLights)
开发的云控后端程序，是集成了多账号多终端负载均衡，数据管理，任务分发，消息推送，日志等等功能于一体的强大后端管理程序，使用
Inquisition 可以轻松管理数百个账号与设备。

## 组织架构

![](https://fastly.jsdelivr.net/gh/DazeCake/image-host/blogaeirtech_structure.png)

## 前端实现

[伊比利亚之眼](https://github.com/AegirTech/IberiaEye)

## 如何使用

请参考 [快速部署](doc/FastDeploy.md) 文档

一般用户可以使用由项目组提供支持的的付费 [在线托管服务](https://ark.aegirtech.com/)
，此服务将会提供最优先甚至领先于release版本的服务与支持，且拥有代理用户系统，使用在线托管服务亦是对开发的支持。代理接入请联系`contact@aegirtech.com`

## 问题反馈

请使用`docker logs inquisition`查询并携带完整日志与截图，使用md格式提交issue，欢迎任何有价值的issue。

## 免责声明

本项目仅供学习交流使用，任何使用了本项目的行为均与本项目无关，使用本项目造成的一切后果均由使用者自行承担。
## NPC 智能助手

本仓库已接入 CNB NPC 智能助手「Inquisition助手」。

- 使用方式：在 Issue 或 PR 评论区 @ 助手（点击「召唤助手」按钮或在评论中 @ ），即可召唤助手处理任务，如问题排查、代码评审、文档解答等。
- 配置文件：
  - `.cnb/settings.yml` — NPC 角色人设、按钮与默认角色
  - `.cnb.yml` — 触发事件流水线（Issue/PR 评论触发）
  - `.cnb/npc/Dockerfile` — 工程师 NPC 运行环境镜像（Java 11 / Gradle 工具链）
- 工程师 NPC（后端/数据层/测试/任务调度/PR 审查）运行在仓库自建环境镜像（tag：`npc-java`）中，可直接在本仓执行 `./gradlew` 编译与测试；`.cnb/npc/**` 变更推送 `main` 后由 `.cnb.yml` 的 `npc-runtime-image` 流水线自动重建镜像。与项目自身的 `Dockerfile`（Java 部署镜像）互不影响。

## NPC 制造工具

仓库另接入「NPC 制造工具」（`npc/create-npc`），如需创建或调整更多 NPC 角色，在 Issue 中 @ 它即可。
