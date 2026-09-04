# =============================================================
# Inquisition 工程师 NPC 运行环境镜像
# -------------------------------------------------------------
# 在 CNB NPC 官方运行配方（cnb-cli + skills）之上叠加：
#   1) Java 11 工具链（项目 sourceCompatibility = 11 / Spring Boot 2.7）
#   2) 预热后的 Gradle 7.4.1 发行版 + 本项目依赖缓存（/root/.gradle）
# 使「后端开发工程师 / 数据层工程师 / 测试工程师」在任务中可直接执行
# ./gradlew compileJava / test，省去每次任务里先下载工具链与依赖的时间。
#
# 镜像链路：.cnb/npc/** push 到 main -> .cnb.yml 的 npc-runtime-image
#           流水线自动 build & push（tag: npc-java）-> NPC 事件引用该镜像。
# 参考：https://docs.cnb.cool/zh/build/npc.md
# =============================================================

# ---- 阶段 0：JDK 11（供预热与最终镜像复用）----
FROM eclipse-temurin:11-jdk AS jdk

# ---- 阶段 1：预热 Gradle 发行版 + 项目依赖 ----
FROM node:22-bookworm-slim AS gradle-deps
COPY --from=jdk /opt/java/openjdk /opt/java
ENV JAVA_HOME=/opt/java \
    PATH=/opt/java/bin:${PATH}
# 解析依赖只需构建脚本，无需业务源码
COPY gradlew gradle build.gradle settings.gradle ./
RUN chmod +x gradlew \
    && (./gradlew --no-daemon --console=plain dependencies \
        || echo "WARN: gradle 依赖预热失败，镜像仍可用（首次任务会在线下载）")

# ---- 阶段 2：最终运行环境 ----
FROM node:22-bookworm-slim

# NPC 运行时必备组件（官方配方：cnb-cli + skills）
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates git git-lfs curl jq ripgrep \
    && rm -rf /var/lib/apt/lists/* \
    && git lfs install \
    && npm install -g @cnbcool/cnb-cli skills \
    && npx skills add https://cnb.cool/cnb/skills/cnb-skill.git -g -y

# Java 11 工具链
COPY --from=jdk /opt/java/openjdk /opt/java
ENV JAVA_HOME=/opt/java \
    PATH=/opt/java/bin:${PATH}

# 预置 Gradle 缓存（发行版 + 依赖 jar），首次任务免下载
COPY --from=gradle-deps /root/.gradle /root/.gradle

# 校验工具链就绪
RUN java -version \
    && test -d /root/.gradle/wrapper/dists/gradle-7.4.1-bin
