# Build with the Gradle wrapper, then ship only the jar on a JRE.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy the wrapper and build scripts first so dependency resolution is cached
# independently of source changes.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app

# Run unprivileged.
RUN useradd --system --create-home --shell /usr/sbin/nologin chatter
USER chatter

COPY --from=build --chown=chatter:chatter /workspace/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
