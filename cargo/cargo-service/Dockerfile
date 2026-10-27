FROM eclipse-temurin:22-jdk AS builder
WORKDIR /src
ARG VITE_KEYCLOAK_URL=http://localhost:8180
ARG VITE_KEYCLOAK_REALM=air-freight
ARG VITE_KEYCLOAK_CLIENT_ID=cargo-frontend
ENV VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL \
    VITE_KEYCLOAK_REALM=$VITE_KEYCLOAK_REALM \
    VITE_KEYCLOAK_CLIENT_ID=$VITE_KEYCLOAK_CLIENT_ID
COPY . .
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon :cargo:cargo-service:bootJar

FROM eclipse-temurin:22-jre
COPY --from=builder /src/cargo/cargo-service/build/libs/*.jar /app.jar
EXPOSE 8081
USER 1000:1000
ENTRYPOINT ["java", "-jar", "/app.jar"]
