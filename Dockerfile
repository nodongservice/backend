FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 런타임 권한 최소화를 위해 전용 계정을 사용한다.
RUN groupadd -r bridgework && useradd -r -g bridgework bridgework

ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} /app/app.jar
COPY resources/reference /app/resources/reference

RUN chown -R bridgework:bridgework /app
USER bridgework

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
