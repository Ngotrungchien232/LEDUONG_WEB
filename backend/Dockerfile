# Multi-stage build for Java 17 Spring Boot with Gradle
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app

# Copy gradle wrapper & configs
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

# Download dependencies
RUN ./gradlew dependencies --no-daemon || true

# Copy source code and build JAR
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Minimal runtime image
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-Dserver.port=8080", "-jar", "app.jar"]
