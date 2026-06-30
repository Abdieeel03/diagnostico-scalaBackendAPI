FROM sbtscala/scala-sbt:eclipse-temurin-21.0.8_9_1.12.11_2.13.18 AS builder

WORKDIR /app

COPY project project
COPY build.sbt .
RUN sbt update

COPY . .

RUN sbt stage

FROM eclipse-temurin:21-jre

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/target/universal/stage .

ENV JAVA_OPTS="-Dplay.http.secret.key=UY4Bm9zPx7Lq2Vk8Rt5Nw3Jf6Hd1CsAe0GyObnMpXiQuSvWaKc"

EXPOSE 9000

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:9000/health || exit 1

CMD ["bin/diagnostico-scalabackendapi"]
