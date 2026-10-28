# -----------------------------------------------------------------------------
# Multi-Stage Dockerfile for ShopSphere
# Generated automatically by Zenith DSL v1.0.0
# -----------------------------------------------------------------------------

# 1. Build Stage
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# 2. Runtime Stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S zenith && adduser -S zenith -G zenith
USER zenith:zenith
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "app.jar"]
