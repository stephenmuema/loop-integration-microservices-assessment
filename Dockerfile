# syntax=docker/dockerfile:1

# ---- Stage 1: build the jar ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Cache dependencies first for faster incremental builds
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---- Stage 2: minimal runtime ----
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# Run as a non-root user
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /workspace/target/country-info-service-*.jar app.jar

EXPOSE 8080

# Container-aware heap sizing + fast random for startup
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
