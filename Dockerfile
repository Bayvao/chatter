# Build with the Gradle wrapper, then ship only the jar on a JRE.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy the wrapper and build scripts first so dependency resolution is cached
# independently of source changes.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle

# A Windows checkout can hand us CRLF line endings and no exec bit. CRLF makes
# the kernel look for an interpreter named "/bin/sh\r", which fails as exit 127.
# .gitattributes prevents this for fresh clones; this keeps existing working
# copies building too.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Warm the dependency cache separately from the sources. Failures here are not
# fatal (the real build below reports them), but they are logged rather than
# silenced so a broken step is diagnosable.
RUN ./gradlew --no-daemon dependencies || true

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
