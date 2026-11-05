FROM eclipse-temurin:22-jdk AS builder
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon :message-formatter-service:bootJar

FROM eclipse-temurin:22-jre
COPY --from=builder /src/message-formatter-service/build/libs/*.jar /app.jar
EXPOSE 8084
USER 1000:1000
ENTRYPOINT ["java", "-jar", "/app.jar"]
