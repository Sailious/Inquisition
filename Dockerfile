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
COPY --from=builder /app/build/libs/*.jar /Inquisition.jar

# 云托管平台通过 PORT 环境变量指定监听端口，未设置时默认 2000
ENV PORT=2000
EXPOSE 2000

# 配置通过环境变量注入（SPRING_DATASOURCE_* 等），不打进镜像
ENTRYPOINT ["sh", "-c", "java -jar -Duser.timezone=Asia/Shanghai -Dserver.port=${PORT} /Inquisition.jar"]
