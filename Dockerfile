FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S cafenet && adduser -S cafenet -G cafenet
WORKDIR /opt/cafenet
COPY --from=build /src/target/cafenet.jar app.jar
USER cafenet
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
