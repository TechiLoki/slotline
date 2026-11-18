FROM node:22-alpine AS web
WORKDIR /web
COPY web/package*.json ./
RUN npm ci --ignore-scripts
COPY web/ ./
RUN npm run build && npm test

FROM maven:3.9-eclipse-temurin-21 AS java-source
WORKDIR /app
COPY pom.xml ./
COPY src ./src
COPY --from=web /web/dist/ ./src/main/resources/www/

FROM java-source AS tests
CMD ["mvn", "--batch-mode", "verify"]

FROM java-source AS build
RUN mvn --batch-mode verify

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN groupadd --gid 10001 slotline && useradd --uid 10001 --gid slotline --no-create-home slotline
WORKDIR /app
COPY --from=build /app/target/slotline.jar ./slotline.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=5 CMD ["java","-jar","/app/slotline.jar","--health"]
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/slotline.jar"]
