package ru.rfsnab.orderservice.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Аспект для централизованного логирования вызовов методов.
 * Controller — INFO (входящие запросы и ответы)
 * Service — DEBUG (выполнение бизнес-логики + время)
 * Repository — DEBUG (запросы к БД)
 * ExceptionHandler — WARN (обработка ошибок)
 */
@Aspect
@Component
public class LoggingAspect {
    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    // ==================== Pointcuts ====================

    @Pointcut("execution(* ru.rfsnab.orderservice.controller..*(..))")
    public void controllerLayer() {}

    @Pointcut("execution(* ru.rfsnab.orderservice.service..*(..))")
    public void serviceLayer() {}

    @Pointcut("execution(* ru.rfsnab.orderservice.repository..*(..))")
    public void repositoryLayer() {}

    @Pointcut("execution(* ru.rfsnab.orderservice.exception.GlobalExceptionHandler.*(..))")
    public void exceptionHandlerLayer() {}

    // ==================== Controller Layer (INFO) ====================

    @Before("controllerLayer()")
    public void logBeforeController(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().toShortString();

        log.info(">>> Incoming request: {}", methodName);
    }

    @AfterReturning(pointcut = "controllerLayer()", returning = "result")
    public void logAfterController(JoinPoint joinPoint, Object result) {
        String methodName = joinPoint.getSignature().toShortString();
        String resultInfo = result != null ? result.getClass().getSimpleName() : "null";

        log.info("<<< Response from: {} with result type: {}",
                methodName, resultInfo);
    }

    // ==================== Service Layer (DEBUG + время) ====================

    @Around("serviceLayer()")
    public Object logAroundService(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();

        log.debug("==> Executing service: {}", methodName);

        long startTime = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long executionTime = System.currentTimeMillis() - startTime;

        log.debug("<== Service completed: {} in {}ms",
                methodName, executionTime);

        return result;
    }

    // ==================== Repository Layer (DEBUG) ====================

    @Before("repositoryLayer()")
    public void logBeforeRepository(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().toShortString();

        log.debug("--- DB query: {}", methodName);
    }

    @AfterReturning(pointcut = "repositoryLayer()", returning = "result")
    public void logAfterRepository(JoinPoint joinPoint, Object result) {
        String methodName = joinPoint.getSignature().toShortString();
        String resultInfo = result != null ? result.getClass().getSimpleName() : "null";

        log.debug("--- DB result: {} returned: {}", methodName, resultInfo);
    }

    // ==================== Exception Handler (WARN) ====================

    @Around("exceptionHandlerLayer()")
    public Object logAroundExceptionHandler(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();

        // Извлекаем исключение из аргументов
        Throwable exception = null;
        for (Object arg : args) {
            if (arg instanceof Throwable throwable) {
                exception = throwable;
                break;
            }
        }

        if (exception != null) {
            log.warn(">>> Handling exception in: {} - Exception type: {}",
                    methodName,
                    exception.getClass().getSimpleName());
        }

        Object result = joinPoint.proceed();

        log.warn("<<< Exception handled by: {}", methodName);

        return result;
    }
}
