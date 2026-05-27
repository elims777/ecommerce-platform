FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Не запускаем от root
RUN addgroup -S spring && adduser -S spring -G spring

# Создаём папку для логов и временных файлов обмена с 1С
RUN mkdir -p /app/logs /app/1c-exchange && chown -R spring:spring /app/logs /app/1c-exchange

USER spring:spring

# JAR будет скопирован из CI после сборки
COPY target/*.jar app.jar

# JVM настройки для контейнера
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dnetworkaddress.cache.ttl=0"

EXPOSE 8085

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]