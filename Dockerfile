# Build stage: compile and package with the Maven wrapper. Tests run in CI (./mvnw verify), not
# here, because the integration tests need Docker themselves.
FROM eclipse-temurin:21.0.12.1_1-jdk-noble AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    sh mvnw -B -ntp -DskipTests package \
    && cp target/url-shortener-*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Runtime stage: JRE only, non-root, layered for cache-friendly rebuilds.
FROM eclipse-temurin:21.0.12.1_1-jre-noble
RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /application
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER app
ENV TZ=UTC
EXPOSE 8080 8081
# Exec form: the JVM is PID 1 and receives SIGTERM directly for a graceful shutdown.
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "application.jar"]
