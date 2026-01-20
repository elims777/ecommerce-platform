FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Не запускаем от root
RUN addgroup -S spring && adduser -S spring -G spring

# Создаём папку для логов с правами для spring
RUN mkdir -p /app/logs && chown spring:spring /app/logs

USER spring:spring

# JAR будет скопирован из CI после сборки
COPY target/*.jar app.jar

# JVM настройки для контейнера
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8082

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]