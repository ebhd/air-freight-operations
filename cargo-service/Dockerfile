FROM eclipse-temurin:22-jdk AS builder
WORKDIR /src
ARG VITE_KEYCLOAK_URL=http://localhost:8180
ARG VITE_KEYCLOAK_REALM=air-freight
ARG VITE_KEYCLOAK_CLIENT_ID=air-freight-frontend
COPY . .
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon :cargo-service:bootJar

FROM eclipse-temurin:22-jre
COPY --from=builder /src/cargo-service/build/libs/*.jar /app.jar
EXPOSE 8081
USER 1000:1000
ENTRYPOINT ["java", "-jar", "/app.jar"]
