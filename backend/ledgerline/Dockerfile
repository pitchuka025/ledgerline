FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/ledgerline/pom.xml ./pom.xml
COPY backend/ledgerline/src ./src
RUN mvn -B package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/ledgerline-0.0.1-SNAPSHOT.jar ./app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
