FROM eclipse-temurin:25.0.2_10-jdk

WORKDIR /app

COPY target/*.jar app.jar

ENV USER_NAME="Nouran ElHadad"
ENV ID="55-0969"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]