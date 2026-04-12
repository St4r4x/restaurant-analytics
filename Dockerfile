FROM maven:3.9-eclipse-temurin-25 as builder

WORKDIR /build

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code
COPY src ./src

# Build application
RUN mvn clean package -DskipTests

# Production image — JRE-only Alpine (smaller than JDK; non-root user for security)
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Non-root user — Alpine BusyBox syntax (NOT useradd which does not exist on Alpine)
RUN addgroup -S appuser && adduser -S appuser -G appuser

# Copy JAR from builder
COPY --from=builder /build/target/*.jar app.jar

# MongoDB connection defaults (overridden by docker-compose environment section)
ENV MONGODB_URI=mongodb://mongodb:27017
ENV MONGODB_DATABASE=newyork
ENV MONGODB_COLLECTION=restaurants

LABEL org.opencontainers.image.source=https://github.com/St4r4x/restaurant-analytics

# Expose port
EXPOSE 8080

# Run as non-root user (must appear after COPY to avoid permission issues on copied files)
USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
