# ---------- stage 1: build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copied first so the dependency layer is cached and only re-runs when pom.xml
# changes, not on every source edit.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B


# ---------- stage 2: run ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# Documentation only. Render sets PORT and the app binds it via
# server.port=${PORT:8080}; locally that default gives 8080.
EXPOSE 8080

# MaxRAMPercentage keeps the heap inside the container limit — the JVM otherwise
# sizes itself from the host, which gets it OOM-killed on Render's free tier.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
