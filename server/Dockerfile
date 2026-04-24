FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :server:buildFatJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/server/build/libs/server-all.jar /app/server.jar
VOLUME ["/app/data"]
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/server.jar"]
CMD ["serve", "--port", "8080", "--db", "/app/data/ucon.sqlite"]
