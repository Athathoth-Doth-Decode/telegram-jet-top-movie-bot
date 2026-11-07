# syntax=docker/dockerfile:1
FROM docker.io/library/maven:3.9.16-eclipse-temurin-17@sha256:1a352420f7aba21f5ad08df31bab55f74c013fb491f1ae8ab1dd7ff9ed698584 AS build
WORKDIR /home/app
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode --no-transfer-progress verify

FROM docker.io/library/eclipse-temurin:17-jre-noble@sha256:8ee89ca3f9eb6a931a2b2e8b2901a4af3554383f8077558e3631d49bb58cff1d AS runtime
RUN apt-get update && apt-get upgrade -y --no-install-recommends \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /home/app/target/jet-movie-top-bot-1.0-SNAPSHOT.jar /usr/local/lib/jet-movie-top-bot.jar
USER 1000:1000
ENTRYPOINT ["java","-jar","/usr/local/lib/jet-movie-top-bot.jar"]
