FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY api-gateway/pom.xml api-gateway/
COPY defense-orchestrator-service/pom.xml defense-orchestrator-service/
COPY schedule-optimizer-service/pom.xml schedule-optimizer-service/
COPY registrar-clearance-service/pom.xml registrar-clearance-service/
COPY api-gateway/src api-gateway/src

RUN --mount=type=cache,id=juryopt-m2,target=/root/.m2,sharing=locked \
    mvn -B -pl api-gateway -am package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=build --chown=appuser:appgroup /workspace/api-gateway/target/api-gateway-*.jar /app/app.jar

USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]