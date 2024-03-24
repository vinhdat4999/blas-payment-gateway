FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY target/*.jar app.jar
COPY BlasSecretKey.p12 BlasSecretKey.p12
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
