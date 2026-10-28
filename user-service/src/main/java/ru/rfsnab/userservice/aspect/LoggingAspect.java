package ru.rfsnab.userservice.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Аспект для централизованного логирования вызовов методов в слоях приложения.
 * Логирует входы/выходы из методов контроллеров, сервисов и репозиториев.
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    /**
     * Pointcut для всех контроллеров
     */
    @Pointcut("within(ru.rfsnab.userservice.controllers..*)")
    public void controllerLayer() {}

    /**
     * Pointcut для всех сервисов
     */
    @Pointcut("within(ru.rfsnab.userservice.services..*)")
    public void serviceLayer() {}

    /**
     * Pointcut для всех репозиториев
     */
    @Pointcut("within(ru.rfsnab.userservice.repository..*)")
    public void repositoryLayer() {}

    /**
     * Pointcut для GlobalExceptionHandler
     */
    @Pointcut("execution(* ru.rfsnab.userservice.exceptions.GlobalExceptionHandler.*(..))")
    public void exceptionHandlerLayer() {}

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

    /**
     * Логирование вызовов сервисов с замером времени выполнения
     */
    @Around("serviceLayer() && !execution(* *..setPasswordHash(..))")
    public Object logAroundService(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();

        long startTime = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long executionTime = System.currentTimeMillis() - startTime;

        log.debug("<== Service completed: {} in {} ms", methodName, executionTime);

        return result;
    }

    /**
     * Логирование вызовов репозиториев
     */
    @Before("repositoryLayer()")
    public void logBeforeRepository(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().toShortString();
        log.debug("--- DB query: {}", methodName);
    }

    /**
     * Логирование результатов из репозиториев
     */
    @AfterReturning("repositoryLayer()")
    public void logAfterRepository(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().toShortString();
        log.debug("--- DB result: {}", methodName);
    }

    /**
     * Логирование обработки исключений
     */
    @Before("exceptionHandlerLayer()")
    public void logBeforeExceptionHandler(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();

        if (args.length > 0 && args[0] instanceof Exception ex) {
            log.warn(">>> Handling exception in: {} - Exception type: {}",
                    methodName, ex.getClass().getSimpleName());
        }
    }

    /**
     * Логирование завершения обработки исключений
     */
    @AfterReturning("exceptionHandlerLayer()")
    public void logAfterExceptionHandler(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().toShortString();
        log.warn("<<< Exception handled by: {}", methodName);
    }
}
