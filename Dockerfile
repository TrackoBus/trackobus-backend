# --- Stage 1: Build the JAR ---
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

# Copy Maven wrapper and configuration
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Give execute permission to Maven wrapper
RUN chmod +x mvnw

# Download dependencies offline (helps speed up rebuilding)
RUN ./mvnw dependency:go-offline

# Copy application source and build the JAR (skipping tests)
COPY src ./src
RUN ./mvnw clean package -DskipTests

# --- Stage 2: Runtime Environment ---
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Copy built JAR from stage 1
COPY --from=build /app/target/TrackoBus-0.0.1-SNAPSHOT.jar app.jar

# Expose port and run
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
