FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests


FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy Supabase CA certificate
COPY certs/prod-ca-2021.crt /tmp/prod-ca-2021.crt

# Add Supabase CA to Java's trusted certificates
RUN keytool -importcert \
    -trustcacerts \
    -alias supabase-prod-ca \
    -file /tmp/prod-ca-2021.crt \
    -cacerts \
    -storepass changeit \
    -noprompt \
    && rm /tmp/prod-ca-2021.crt

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]