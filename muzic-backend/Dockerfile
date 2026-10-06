FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests


FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# TEMPORARY: install OpenSSL for TLS debugging
RUN apk add --no-cache openssl

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# TEMPORARY: test Supabase TLS first, then start Spring
ENTRYPOINT ["sh", "-c", "echo '=== SUPABASE TLS TEST ==='; timeout 15 openssl s_client -starttls postgres -connect aws-0-ca-central-1.pooler.supabase.com:5432 -servername aws-0-ca-central-1.pooler.supabase.com </dev/null 2>&1 || true; echo '=== END TLS TEST ==='; java -jar app.jar"]