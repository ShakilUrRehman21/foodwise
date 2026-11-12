# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
# Dependency cache survives rebuilds. (dependency:go-offline is avoided: it also tries to fetch optional extras.)
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -ntp -DskipTests package && cp target/foodwise-backend-*.jar /app.jar

# ---- runtime ----
FROM eclipse-temurin:21-jre-noble
# Tesseract 5 (Ubuntu 24.04) + English language data for local receipt OCR (Roadmap UD-006: bundled in the image)
RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 foodwise \
 && mkdir -p /data/uploads && chown -R foodwise /data
COPY --from=build /app.jar /app/app.jar
USER foodwise
ENV SPRING_PROFILES_ACTIVE=prod \
    SCAN_STORAGE_DIR=/data/uploads \
    JAVA_OPTS="-XX:+UseG1GC -Xmx512m -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
