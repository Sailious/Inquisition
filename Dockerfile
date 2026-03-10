FROM gradle:7.6-jdk11 AS builder

WORKDIR /build

COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew .

RUN chmod +x gradlew
RUN ./gradlew clean build -x test --no-daemon

FROM openjdk:11-jre-slim

WORKDIR /app

COPY --from=builder /build/build/libs/*.jar /app/Inquisition.jar
COPY src/main/resources/application.yml /app/config/application.yml
COPY src/main/resources/moss-sdk.properties /app/config/moss-sdk.properties

EXPOSE 2000

ENTRYPOINT ["java", "-jar", "-Duser.timezone=Asia/Shanghai", "-Dspring.config.location=/app/config/", "/app/Inquisition.jar"]
