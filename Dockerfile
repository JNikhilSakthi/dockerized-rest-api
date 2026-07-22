# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Stage 1: build - compiles the app with Maven inside a throwaway container.
# The host does not need Maven or a JDK installed at all; only Docker.
# ---------------------------------------------------------------------------
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace

# Copy only the POM first so Docker can cache the downloaded dependency layer
# and skip re-downloading them whenever just the source code changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests \
    && cp target/dockerized-rest-api.jar target/app.jar

# ---------------------------------------------------------------------------
# Stage 2: runtime - a minimal JRE-only image; the Maven build tooling and
# source tree from stage 1 are discarded, keeping the final image small and
# free of build-time attack surface.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runtime

# Run as a non-root, unprivileged user rather than the image default of root.
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app

COPY --from=build /workspace/target/app.jar app.jar
RUN chown spring:spring app.jar
USER spring:spring

EXPOSE 8080

# Container-level healthcheck backed by Spring Boot Actuator; docker-compose
# uses this same signal to gate dependent services with "condition: service_healthy".
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/liveness | grep -q '"UP"' || exit 1

# Keep JVM memory bounded to the container's cgroup limits (set via compose
# "mem_limit"/deploy.resources) and allow overriding extra flags at run time.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -XX:MaxRAMPercentage=75.0 -jar app.jar"]
