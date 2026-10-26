# ============================================================================
# RecoverAI API — multi-stage production container
# Runtime: Eclipse Temurin JRE 21 (small), non-root user, healthcheck.
# ============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY apps/api/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY apps/api/src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 recoverai && adduser -S -u 10001 -G recoverai recoverai
USER recoverai
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/recoverai-api-0.1.0.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=6 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
