# ============================================================================
# RecoverAI worker — same artifact as the API, run in EVENT_DISPATCH_MODE=kafka
# (recovery + audit + metrics consumers, DB-backed scheduler for demo fallback).
# ============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY apps/api/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY apps/api/src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S recoverai && adduser -S -G recoverai recoverai
USER recoverai
WORKDIR /app
COPY --from=build /build/target/recoverai-api-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar", \
  "--recoverai.event-dispatch-mode=kafka", "--server.port=8080"]
