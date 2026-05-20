FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_OPTS=""
COPY --from=build /workspace/target/msr-amis-api-0.0.1-SNAPSHOT.jar /app/msr-amis-api.jar

EXPOSE 8090

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/msr-amis-api.jar"]
