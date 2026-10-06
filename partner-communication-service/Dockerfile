FROM eclipse-temurin:22-jdk AS builder
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon :partner-communication-service:bootJar

FROM eclipse-temurin:22-jre
COPY --from=builder /src/partner-communication-service/build/libs/*.jar /app.jar
EXPOSE 8082
USER 1000:1000
ENTRYPOINT ["java", "-jar", "/app.jar"]
