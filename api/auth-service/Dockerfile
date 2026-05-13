FROM maven:3.9.12-amazoncorretto-21-alpine AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN sed -i 's/\r$//' mvnw \
    && chmod +x mvnw \
    && ./mvnw -B -Dmaven.test.skip=true dependency:go-offline

COPY src ./src

RUN ./mvnw -B -Dmaven.test.skip=true package

FROM maven:3.9.12-amazoncorretto-21-alpine AS runtime

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build /workspace/target/*.jar /app/app.jar

RUN chown spring:spring /app/app.jar

USER spring

ENV JAVA_OPTS=""
ENV SPRING_PROFILES_ACTIVE=local

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-local}"]
