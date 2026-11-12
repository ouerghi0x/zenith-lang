# ==============================================================================
# Zenith Studio & Compiler Backend — Production Container
# Used with Cloudflare Pages + Cloudflare Workers / Containers
# ==============================================================================
FROM eclipse-temurin:21-jdk-alpine

# Install Maven & curl for live Spring Boot & JUnit execution inside Studio
RUN apk add --no-cache maven curl bash

WORKDIR /app

# Copy compiled Zenith CLI JAR and example schemas
COPY bin/zenith.jar /app/bin/zenith.jar
COPY zenith-exemples /app/zenith-exemples
COPY src/main/resources/studio /app/src/main/resources/studio

# Expose Studio port (3000) and default Spring Boot target port (8081)
EXPOSE 3000 8081

# Default environment variables
ENV PORT=3000
ENV ZENITH_PRO=false

# Start Zenith Studio Server
CMD ["sh", "-c", "java -jar /app/bin/zenith.jar studio /app/zenith-exemples/nexus-commerce/schema.zen --port ${PORT:-3000}"]

