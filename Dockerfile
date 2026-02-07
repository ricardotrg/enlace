# syntax=docker/dockerfile:1

# ---------- Build stage ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Leverage Docker layer cache: download deps before copying sources
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as non-root
RUN addgroup --system app ; adduser --system --ingroup app app
USER app

# Spring Boot listens on 8080 by default
EXPOSE 8080

# Copy the built jar (Spring Boot executable jar)
COPY --from=build /workspace/target/*.jar /app/app.jar

# Profile is expected to be provided via env var (SPRING_PROFILES_ACTIVE)
ENTRYPOINT ["java","-jar","/app/app.jar"]
