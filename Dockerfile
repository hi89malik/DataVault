# Multi-stage Dockerfile for DataVault Java 21 Spring Boot Server
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy POM files and source code
COPY pom.xml .
COPY datavault-server/pom.xml datavault-server/
COPY datavault-cli/pom.xml datavault-cli/
COPY datavault-server/src datavault-server/src
COPY datavault-cli/src datavault-cli/src

# Build JAR package
RUN mvn clean package -DskipTests

# Runtime stage using lightweight Java 21 JRE
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/datavault-server/target/datavault-server-1.0.0.jar app.jar

EXPOSE 8989
ENTRYPOINT ["java", "-jar", "app.jar"]
