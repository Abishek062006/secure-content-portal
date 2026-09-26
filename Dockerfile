# Backend image for AWS (ECS Fargate). Build from the repo root:  docker build -t gradientnova-api .
# Multi-stage: Maven builds the jar, a slim JRE runs it. ffmpeg is installed because uploaded lesson videos are cut into
# adaptive-streaming (HLS) renditions in the background.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B -DskipTests package && mv target/secure-content-portal-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app

ENV SPRING_PROFILES_ACTIVE=prod \
    STORAGE_PROVIDER=s3 \
    STORAGE_CREATE_BUCKET=false \
    STORAGE_PATH_STYLE=false \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 CMD curl -fsS http://localhost:8080/healthz || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
