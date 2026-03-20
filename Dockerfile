FROM maven:3.8-eclipse-temurin-21 as builder

WORKDIR /build

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code
COPY src ./src

# Build application
RUN mvn clean package -DskipTests

# Production image
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /build/target/*.jar app.jar

# MongoDB connection defaults (overridden by docker-compose environment section)
ENV MONGODB_URI=mongodb://mongodb:27017
ENV MONGODB_DATABASE=newyork
ENV MONGODB_COLLECTION=restaurants

# Expose port
EXPOSE 8080

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
