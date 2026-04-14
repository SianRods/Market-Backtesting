# ===============================
#  Build stage (Java 21)
# ===============================
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

# ===============================
#  Runtime stage (Java 21)
# ===============================
FROM eclipse-temurin:21-jre-alpine

# Hugging Face requires running as a non-root user (user ID 1000)
RUN addgroup -S appgroup && adduser -S -u 1000 appuser -G appgroup
USER 1000

WORKDIR /app

COPY --from=build --chown=appuser:appgroup /app/target/*.jar app.jar

# Hugging Face Spaces exposes port 7860
EXPOSE 7860

ENTRYPOINT ["java","-Dserver.port=7860","-jar","app.jar"]
