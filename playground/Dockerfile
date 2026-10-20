# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./pom.xml
COPY src/main ./src/main
COPY playground/build-library.sh ./build-library.sh
RUN --mount=type=secret,id=proxy_ca --mount=type=cache,target=/root/.m2 sh ./build-library.sh
COPY playground/Playground.java ./Playground.java
RUN mkdir -p /playground/classes && \
    javac --add-modules jdk.httpserver -cp 'target/classes:target/runtime-deps/*' \
      -d /playground/classes Playground.java && \
    cp -r target/classes/. /playground/classes/ && \
    cp -r target/runtime-deps /playground/deps

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --chown=10001:10001 --from=build /playground/ ./
COPY --chown=10001:10001 playground/index.html ./index.html
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "--add-modules", "jdk.httpserver", "--add-opens=java.base/java.lang=ALL-UNNAMED", "-cp", "/app/classes:/app/deps/*", "Playground"]
