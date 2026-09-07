# --- Build stage --------------------------------------------------------
# Separating the pom.xml copy from the src copy lets Docker cache the
# dependency-resolution layer across builds where only source changed.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# --- Runtime stage --------------------------------------------------------
# Tests need a real Postgres connection and aren't run here — this is a
# deploy-time build, not a CI check. See AccessControlTest's Javadoc.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/*.jar app.jar
RUN chown app:app app.jar
USER app

EXPOSE 8080

# 512MB is tight for a JVM; Render's own JAVA_TOOL_OPTIONS env var (if set)
# overrides this default at runtime.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O- http://localhost:${PORT:-8080}/healthz || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
