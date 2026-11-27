FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY api-gateway/pom.xml api-gateway/
COPY defense-orchestrator-service/pom.xml defense-orchestrator-service/
COPY schedule-optimizer-service/pom.xml schedule-optimizer-service/
COPY registrar-clearance-service/pom.xml registrar-clearance-service/
COPY registrar-clearance-service/src registrar-clearance-service/src

RUN --mount=type=cache,id=juryopt-m2,target=/root/.m2,sharing=locked \
    mvn -B -pl registrar-clearance-service -am package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=build --chown=appuser:appgroup /workspace/registrar-clearance-service/target/registrar-clearance-service-*.jar /app/app.jar

USER appuser
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]