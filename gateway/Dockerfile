# Build context is the repository root (`docker build -f gateway/Dockerfile .`).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY contracts/pom.xml contracts/pom.xml
COPY identity-service/pom.xml identity-service/pom.xml
COPY catalog-service/pom.xml catalog-service/pom.xml
COPY booking-service/pom.xml booking-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY gateway/pom.xml gateway/pom.xml
RUN mvn -q -pl gateway -am dependency:go-offline

COPY gateway/src gateway/src
RUN mvn -q -pl gateway -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S showtime && adduser -S showtime -G showtime
WORKDIR /app
COPY --from=build /workspace/gateway/target/*.jar /app/app.jar
USER showtime
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
