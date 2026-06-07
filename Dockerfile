FROM sbtscala/scala-sbt:eclipse-temurin-21.0.8_9_1.12.11_2.13.18

WORKDIR /app

COPY . .

EXPOSE 9000

CMD ["sbt", "run"]
