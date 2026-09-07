FROM openjdk:11-jre-slim

COPY build/libs/*.jar /Inquisition.jar
COPY application.yml.example /config/application.yml

EXPOSE 2000

# M6 修复：创建非root用户运行Java进程
RUN groupadd -r appuser && useradd -r -g appuser -d /home/appuser -m appuser \
    && mkdir -p /home/appuser/config /config \
    && chown -R appuser:appuser /home/appuser /config
USER appuser
WORKDIR /home/appuser

# /config 为镜像内置配置及文档约定的挂载点；
# WORKDIR 为 /home/appuser，Spring Boot 默认只查 ./config/，
# 需显式将 /config 追加为配置来源（optional：目录不存在时不报错）
ENTRYPOINT ["java", "-jar", "-Duser.timezone=Asia/Shanghai", "/Inquisition.jar", "--spring.config.additional-location=optional:/config/"]
