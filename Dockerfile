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
COPY eureka-server/src eureka-server/src
COPY api-gateway/src api-gateway/src
COPY user-service/src user-service/src
COPY vehicle-service/src vehicle-service/src
COPY booking-service/src booking-service/src

RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
ARG SERVICE_NAME
WORKDIR /app
COPY --from=build /workspace/${SERVICE_NAME}/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
