FROM eclipse-temurin:25-jdk

WORKDIR /app

COPY . .

RUN chmod +x ./mvnw
RUN ./mvnw -B clean package -DskipTests
RUN cp target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]