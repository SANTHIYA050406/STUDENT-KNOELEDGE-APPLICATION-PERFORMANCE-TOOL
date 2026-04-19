FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src

RUN mvn -f backend/pom.xml -DskipTests package

FROM jetty:11-jre17

# Deploy under "/" context.
COPY --from=build /app/backend/target/student-knowledge-api-1.0.0.war /var/lib/jetty/webapps/ROOT.war

# Railway routes traffic to the port in $PORT. Jetty reads jetty.http.port.
EXPOSE 8080
CMD ["sh", "-c", "java -jar /usr/local/jetty/start.jar jetty.http.port=${PORT:-8080} jetty.http.host=0.0.0.0"]
