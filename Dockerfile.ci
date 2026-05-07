# JAR is pre-built by CI and passed via the build context (target/ directory).
# This avoids Maven Central rate-limiting during Docker builds.
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Install curl for health check (not in Alpine by default)
RUN apk add --no-cache curl

# Non-root user — Alpine BusyBox syntax (NOT useradd which does not exist on Alpine)
RUN addgroup -S appuser && adduser -S appuser -G appuser

ARG GIT_SHA=unknown

COPY target/*.jar app.jar

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
