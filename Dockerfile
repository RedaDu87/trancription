# Étape 1 : Build du projet
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Étape 2 : Image finale exécutable
FROM eclipse-temurin:21-jdk-alpine

# Installer ffmpeg
RUN apk add --no-cache ffmpeg

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]
