# Built by `./mvnw package` first; see ../README.md. Non-root, JRE only.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY target/*.jar app.jar
USER 10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
