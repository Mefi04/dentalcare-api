FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S dentalcare && adduser -S dentalcare -G dentalcare
COPY --from=build --chown=dentalcare:dentalcare /workspace/target/dentalcare-api.jar app.jar
USER dentalcare
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
