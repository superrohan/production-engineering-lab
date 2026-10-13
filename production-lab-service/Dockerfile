# ---- Build stage: full JDK + Maven, discarded after the build ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies in their own layer so source-only changes reuse the cache.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests run in the CI test stage, not during the image build.
RUN mvn -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/app.jar extract --layers --launcher --destination target/extracted

# ---- Runtime stage: JRE only, non-root ----
FROM eclipse-temurin:21-jre-alpine

# Fixed numeric UID/GID so Kubernetes runAsNonRoot can verify the user.
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app -H app

WORKDIR /app
# Least-frequently changing layers first: a code change only rebuilds the last layer.
COPY --from=build /build/target/extracted/dependencies/ ./
COPY --from=build /build/target/extracted/spring-boot-loader/ ./
COPY --from=build /build/target/extracted/snapshot-dependencies/ ./
COPY --from=build /build/target/extracted/application/ ./

USER 10001:10001
EXPOSE 8080

# Size the heap from the container memory limit; exit on OOM so Kubernetes restarts the pod
# instead of leaving a half-broken JVM running. Override at runtime via JAVA_TOOL_OPTIONS.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Exec form: java runs as PID 1 and receives SIGTERM directly, triggering graceful shutdown.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
