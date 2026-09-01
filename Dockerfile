FROM openjdk:11-jre-slim

COPY build/libs/*.jar /Inquisition.jar
COPY src/main/resources/application.yml /config/application.yml

EXPOSE 2000

# M6 修复：创建非root用户运行Java进程
RUN groupadd -r appuser && useradd -r -g appuser -d /home/appuser -m appuser \
    && mkdir -p /home/appuser/config \
    && chown -R appuser:appuser /home/appuser
USER appuser
WORKDIR /home/appuser

ENTRYPOINT ["java", "-jar", "-Duser.timezone=Asia/Shanghai", "/Inquisition.jar"]
