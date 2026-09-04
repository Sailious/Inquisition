# =============================================================
# Inquisition 工程师 NPC 运行环境镜像
# -------------------------------------------------------------
# 目标：让「后端开发工程师 / 数据层工程师 / 测试工程师」在任务中
#       能直接执行 ./gradlew compileJava / test / bootJar 验证代码，
#       并尽可能快（镜像内预置 依赖缓存 + Gradle 发行版 + 工具链）。
#
# 技术要点：
#   1) 基于 eclipse-temurin:11-jdk（项目 sourceCompatibility = 11 / Spring Boot 2.7）
#   2) Node 20 + CNB NPC 官方运行配方（@cnbcool/cnb-cli + skills）
#   3) 系统级 Gradle 7.4.1（/opt/gradle，含软链），同时用 ./gradlew 预热
#      wrapper 发行版与「本仓库真实依赖缓存」(/root/.gradle)，首次任务免下载
#   4) 腾讯云 Maven 镜像加速（Gradle init.d 全局脚本 + ~/.m2/settings.xml 兜底）
#   5) 每步验证 + 失败不致命（仅告警），保证镜像一定能构建出来
#
# 镜像链路：.cnb/npc/** push 到 main -> .cnb.yml 的 npc-runtime-image
#           流水线自动 build & push（tag: npc-java）-> NPC 事件引用该镜像。
# 参考：https://docs.cnb.cool/zh/build/npc.md
# =============================================================

FROM eclipse-temurin:11-jdk

# ---- 系统基础工具 ----
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        git \
        git-lfs \
        curl \
        wget \
        unzip \
        jq \
        ripgrep \
        openssh-client \
        default-mysql-client \
    && rm -rf /var/lib/apt/lists/* \
    && git lfs install \
    # NPC 在容器内以 clone 下来的仓库为工作区，uid 可能与镜像内用户不同，
    # 预置 safe.directory 通配，避免 git 报 dubious ownership 拒绝读写
    && git config --system --add safe.directory '*'

# ---- Node.js 20（cnb-cli / skills 运行时依赖）----
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

# ---- CNB CLI 与 Skills（NPC 官方运行配方）----
RUN npm install -g @cnbcool/cnb-cli skills \
    && npx skills add https://cnb.cool/cnb/skills/cnb-skill.git -g -y

# ---- 系统级 Gradle 7.4.1（与项目 gradle-wrapper 版本一致）----
# 优先从腾讯云镜像下载（构建节点位于腾讯云，更快更稳），失败回退官方源
ENV GRADLE_VERSION=7.4.1 \
    GRADLE_HOME=/opt/gradle
RUN curl -fsSL --connect-timeout 10 --max-time 300 \
        "https://mirrors.cloud.tencent.com/gradle/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
    || curl -fsSL --connect-timeout 10 --max-time 300 \
        "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && mv /opt/gradle-${GRADLE_VERSION} ${GRADLE_HOME} \
    && rm /tmp/gradle.zip \
    && ln -s ${GRADLE_HOME}/bin/gradle /usr/local/bin/gradle

# ---- Gradle 全局 init 脚本：腾讯云 Maven 镜像（对 ./gradlew 同样生效）----
RUN mkdir -p ~/.gradle/init.d && cat > ~/.gradle/init.d/tencent-repos.gradle << 'GRADLE_EOF'
allprojects {
    repositories {
        maven { url 'https://mirrors.cloud.tencent.com/nexus/repository/maven-public' }
        maven { url 'https://mirrors.cloud.tencent.com/nexus/repository/spring' }
        maven { url 'https://mirrors.cloud.tencent.com/nexus/repository/gradle-plugin' }
        maven { url 'https://mirrors.cloud.tencent.com/nexus/repository/maven-central' }
        mavenCentral()
    }
    buildscript {
        repositories {
            maven { url 'https://mirrors.cloud.tencent.com/nexus/repository/maven-public' }
            maven { url 'https://mirrors.cloud.tencent.com/nexus/repository/gradle-plugin' }
            mavenCentral()
        }
    }
}
GRADLE_EOF

# ---- ~/.m2/settings.xml 兜底（个别经 Maven 解析的场景使用）----
RUN mkdir -p ~/.m2 && cat > ~/.m2/settings.xml << 'MAVEN_EOF'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <mirror>
      <id>tencent-maven-public</id>
      <mirrorOf>central</mirrorOf>
      <name>腾讯云公共仓库</name>
      <url>https://mirrors.cloud.tencent.com/nexus/repository/maven-public</url>
    </mirror>
    <mirror>
      <id>tencent-spring</id>
      <mirrorOf>spring</mirrorOf>
      <name>腾讯云 Spring 仓库</name>
      <url>https://mirrors.cloud.tencent.com/nexus/repository/spring</url>
    </mirror>
    <mirror>
      <id>tencent-gradle-plugin</id>
      <mirrorOf>gradle-plugin</mirrorOf>
      <name>腾讯云 Gradle 插件仓库</name>
      <url>https://mirrors.cloud.tencent.com/nexus/repository/gradle-plugin</url>
    </mirror>
  </mirrors>
  <profiles>
    <profile>
      <id>tencent</id>
      <repositories>
        <repository>
          <id>central</id>
          <url>https://mirrors.cloud.tencent.com/nexus/repository/maven-public</url>
        </repository>
      </repositories>
      <pluginRepositories>
        <pluginRepository>
          <id>central</id>
          <url>https://mirrors.cloud.tencent.com/nexus/repository/maven-public</url>
        </pluginRepository>
      </pluginRepositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>tencent</activeProfile>
  </activeProfiles>
</settings>
MAVEN_EOF

# ---- 预热「本仓库真实依赖缓存」----
# 拷贝仓库的构建脚本执行依赖解析：同时完成 gradle-wrapper 发行版下载、
# 本项目全部依赖（Spring Boot 2.7 / MyBatis-Plus / actable / cos / 七牛 / mapstruct 等）与
# spring-boot 插件的缓存写入 /root/.gradle，任务中首次 ./gradlew 即离线级速度。
# 预热失败不致命（仅告警），镜像仍可用，任务首次会在线补齐。
COPY gradlew gradle build.gradle settings.gradle /tmp/prewarm/
RUN cd /tmp/prewarm \
    && chmod +x gradlew \
    && (timeout 600 ./gradlew --no-daemon --console=plain dependencies || \
        echo "WARN: gradle 依赖预热失败，镜像仍可用（首次任务会在线下载）") \
    && cd / && rm -rf /tmp/prewarm

# ---- 构建期自检（均不致命，避免镜像因瞬时网络问题构建失败）----
RUN echo "==> JDK 版本:" && java -version 2>&1 | head -1 \
    && echo "==> Gradle 版本:" && gradle --version 2>&1 | grep -E "Gradle|JVM:" \
    && echo "==> Node 版本:" && node -v \
    && test -d /root/.gradle/wrapper/dists/gradle-7.4.1-bin \
        && echo "==> gradle wrapper 发行版已预热 ✓" \
        || echo "WARN: wrapper 发行版未预热（首次 ./gradlew 会在线下载）" \
    && (curl -fsSL --connect-timeout 10 --max-time 30 -o /dev/null \
            https://mirrors.cloud.tencent.com/nexus/repository/maven-public/ \
        && echo "==> 腾讯云 Maven 镜像可达 ✓" \
        || echo "WARN: 腾讯云 Maven 镜像暂不可达，将回退 build.gradle 中声明的仓库")

# 默认工作目录
WORKDIR /workspace

# 默认命令
CMD ["/bin/bash"]
