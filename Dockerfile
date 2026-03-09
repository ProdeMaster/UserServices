# Stage 1: Build the application
FROM maven:3.9.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY --from=build /app/target/UserServices-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081

LABEL version="0.1"
LABEL description="Application corresponding to the user-service the ProdeMaster project"
LABEL org.opencontainer.java.version="17"
LABEL org.opencontainer.Spring.version="3.4.*"
LABEL org.opencontainer.mvn.version="3.9.9"

ENTRYPOINT ["java", "-jar", "app.jar"]
