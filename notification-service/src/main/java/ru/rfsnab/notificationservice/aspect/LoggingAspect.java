package ru.rfsnab.notificationservice.aspect;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Аспект для централизованного логирования вызовов методов в слоях приложения.
 * Логирует входы/выходы из методов Kafka consumers, сервисов.
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    /**
     * Pointcut для Kafka consumers
     */
    @Pointcut("within(ru.rfsnab.notificationservice.service.KafkaConsumerService)")
    public void consumerLayer() {}

    /**
     * Pointcut для всех сервисов
     */
    @Pointcut("within(ru.rfsnab.notificationservice.service..*)")
    public void serviceLayer() {}

    /**
     * Pointcut для всех контроллеров
     */
    @Pointcut("within(ru.rfsnab.notificationservice.controller..*)")
    public void controllerLayer() {}

    /**
     * Логирование входящих Kafka событий
     */
    @Before("consumerLayer()")
    public void logBeforeConsumer(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().toShortString();
        log.info(">>> Kafka event received: {}", methodName);
    }

    /**
     * Логирование обработанных Kafka событий
     */
    @AfterReturning("consumerLayer()")
    public void logAfterConsumer(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().toShortString();
        log.info("<<< Kafka event processed: {}", methodName);
    }

    /**
     * Логирование вызовов сервисов с замером времени выполнения
     */
    @Around("serviceLayer()")
    public Object logAroundService(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();

        log.debug("==> Executing service: {} with args: {}", methodName, Arrays.toString(args));

        long startTime = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long executionTime = System.currentTimeMillis() - startTime;

        log.debug("<== Service completed: {} in {} ms", methodName, executionTime);

        return result;
    }

    /**
     * Логирование входящих запросов в контроллеры
     */
    @Before("controllerLayer()")
    public void logBeforeController(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().toShortString();
        log.info(">>> Incoming request: {}", methodName);
    }

    /**
     * Логирование выходов из контроллеров
     */
    @AfterReturning("controllerLayer()")
    public void logAfterController(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().toShortString();
        log.info("<<< Response from: {}", methodName);
    }
}