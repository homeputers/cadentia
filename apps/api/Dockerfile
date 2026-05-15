FROM maven:3.9.10-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY apps/api/pom.xml apps/api/pom.xml
COPY apps/api/src apps/api/src
RUN mvn -pl apps/api -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/apps/api/target/cadentia-api-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
