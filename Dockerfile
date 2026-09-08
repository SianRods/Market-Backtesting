# ===============================
#  Build stage (Java 21)
# ===============================
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw --batch-mode dependency:go-offline

COPY src ./src
RUN ./mvnw --batch-mode clean verify

# ===============================
#  Runtime stage (Java 21)
# ===============================
FROM eclipse-temurin:21-jre-alpine

# Hugging Face requires running as a non-root user (user ID 1000)
RUN addgroup -S appgroup && adduser -S -u 1000 appuser -G appgroup
USER 1000

WORKDIR /app

COPY --from=build --chown=appuser:appgroup /app/target/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=7860

# Hugging Face Spaces exposes port 7860 by default.
EXPOSE 7860

ENTRYPOINT ["java", "-jar", "app.jar"]
