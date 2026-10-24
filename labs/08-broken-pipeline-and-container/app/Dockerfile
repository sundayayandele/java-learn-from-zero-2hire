FROM maven:latest
WORKDIR /app
COPY . .
RUN mvn package
EXPOSE 8080
CMD java -jar target/rates-api-1.0.0.jar
