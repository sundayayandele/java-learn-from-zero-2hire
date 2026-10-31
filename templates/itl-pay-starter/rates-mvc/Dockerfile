# Built by `./mvnw package` first; see ../README.md. Non-root, JRE only.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY target/*.jar app.jar
USER 10001
# Kubernetes overrides this through the same variable to match the pod's memory limit (topic 12.2)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
