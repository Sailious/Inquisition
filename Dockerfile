# 云托管源码构建用：云端执行完整构建
FROM gradle:7.6-jdk11 AS builder
WORKDIR /app
COPY build.gradle settings.gradle ./
# 先下载依赖（利用层缓存）
RUN gradle dependencies --no-daemon -q || true
COPY src ./src
# 打包并排除 application.yml（敏感配置不进镜像）
RUN gradle bootJar --no-daemon -q

FROM openjdk:11-jre-slim
RUN apt-get update && apt-get install -y --no-install-recommends socat \
    && rm -rf /var/lib/apt/lists/*
COPY --from=builder /app/build/libs/*.jar /Inquisition.jar

EXPOSE 2000

# socat 立即监听 2000 应答健康检查，并转发到 Java 的 2001 端口
# 解决平台健康探针（InitialDelay 过短）在 Spring 启动期间杀死容器的问题
ENTRYPOINT ["sh", "-c", "socat TCP-LISTEN:2000,fork,reuseaddr TCP:127.0.0.1:2001 & exec java -jar -Duser.timezone=Asia/Shanghai -Dserver.port=2001 /Inquisition.jar"]
