package ru.rfsnab.authservice.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Аспект для централизованного логирования вызовов методов в слоях приложения.
 * Логирует входы/выходы из методов контроллеров, сервисов и репозиториев.
 */
@Component
@Aspect
public class LoggingAspect {
    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    /**
     * Pointcut для всех контроллеров
     */
    @Pointcut("within(ru.rfsnab.authservice.controllers..*)")
    public void controllerLayer() {}

    /**
     * Pointcut для всех сервисов
     */
    @Pointcut("within(ru.rfsnab.authservice.service..*)")
    public void serviceLayer() {}

    /**
     * Pointcut для utils (JWTService, OAuth2LoginSuccessHandler, etc.)
     */
    @Pointcut("within(ru.rfsnab.authservice.utils..*)")
    public void utilsLayer() {}

    /**
     * Pointcut для GlobalExceptionHandler
     */
    @Pointcut("execution(* ru.rfsnab.authservice.exceptions.GlobalExceptionHandler.*(..))")
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
     * Логирование вызовов utils (JWTService, RoleExtractor, etc.)
     */
    @Before("utilsLayer()")
    public void logBeforeUtils(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();
        log.debug("--- Executing util: {} with params: {}", methodName, Arrays.toString(args));
    }

    /**
     * Логирование результатов из utils
     */
    @AfterReturning(pointcut = "utilsLayer()", returning = "result")
    public void logAfterUtils(JoinPoint joinPoint, Object result) {
        String methodName = joinPoint.getSignature().toShortString();
        String resultType = result != null ? result.getClass().getSimpleName() : "null";
        log.debug("--- Util completed: {} returned: {}", methodName, resultType);
    }

    /**
     * Логирование обработки исключений
     */
    @Before("exceptionHandlerLayer()")
    public void logBeforeExceptionHandler(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();

        if (args.length > 0 && args[0] instanceof Exception) {
            Exception ex = (Exception) args[0];
            log.warn(">>> Handling exception in: {} - Exception type: {} - Message: {}",
                    methodName, ex.getClass().getSimpleName(), ex.getMessage());
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
