# Build stage
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# Runtime: distroless, no shell, runs as a non-root user
FROM gcr.io/distroless/java21-debian13:nonroot
WORKDIR /app
COPY --from=build /src/target/fightcamp.jar app.jar
USER nonroot
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
