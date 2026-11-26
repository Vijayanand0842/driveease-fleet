FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-security/pom.xml common-security/
COPY eureka-server/pom.xml eureka-server/
COPY api-gateway/pom.xml api-gateway/
COPY user-service/pom.xml user-service/
COPY vehicle-service/pom.xml vehicle-service/
COPY booking-service/pom.xml booking-service/
COPY common-security/src common-security/src
COPY vehicle-service/src vehicle-service/src
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*
RUN mvn -B -DskipTests -pl common-security,vehicle-service -am package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/vehicle-service/target/*.jar app.jar
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
