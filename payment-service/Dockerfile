# Build context is the repository root (`docker build -f payment-service/Dockerfile .`).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY contracts/pom.xml contracts/pom.xml
COPY identity-service/pom.xml identity-service/pom.xml
COPY catalog-service/pom.xml catalog-service/pom.xml
COPY booking-service/pom.xml booking-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY gateway/pom.xml gateway/pom.xml
RUN mvn -q -pl payment-service -am dependency:go-offline

COPY payment-service/src payment-service/src
RUN mvn -q -pl payment-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S showtime && adduser -S showtime -G showtime
WORKDIR /app
COPY --from=build /workspace/payment-service/target/*.jar /app/app.jar
USER showtime
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
