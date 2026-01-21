package ru.rfsnab.productservice.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aspect
@Component
public class LoggingAspect {
    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    /**
     * Pointcut для всех методов в Controllers
     */
    @Pointcut("execution(* ru.rfsnab.productservice.controller..*(..))")
    public void controllerLayer(){}

    /**
     * Pointcut для всех методов в Services
     */
    @Pointcut("execution(* ru.rfsnab.productservice.service..*(..))")
    public void serviceLayer(){}

    /**
     * Pointcut для всех методов в Repositories
     */
    @Pointcut("execution(* ru.rfsnab.productservice.repository..*(..))")
    public void repositoryLayer(){}

    /**
     * Pointcut для GlobalExceptionHandler
     */
    @Pointcut("execution(* ru.rfsnab.productservice.exception.GlobalExceptionHandler.*(..))")
    public void exceptionHandlerLayer() {}

    /**
     * Логирование перед выполнением метода контроллера
     */
    @Before("controllerLayer()")
    public void logBeforController(JoinPoint joinPoint){
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();
        log.info(">>> Incoming request: {} with args: {}", methodName, args);
    }

    /**
     * Логирование после успешного выполнения метода контроллера
     */
    @AfterReturning(pointcut = "controllerLayer()", returning = "result")
    public void logAfterController(JoinPoint joinPoint, Object result){
        String methodName = joinPoint.getSignature().toShortString();
        log.info("<<< Response from: {} with result type: {}", methodName, result);
    }

    /**
     * Логирование после исключения в контроллере
     */
    @AfterThrowing(pointcut = "controllerLayer()", throwing = "exception")
    public void logAfterThrowingController(JoinPoint joinPoint, Throwable exception){
        String methodName = joinPoint.getSignature().toShortString();
        log.error("!!! Exception in controller: {} - Error: {}", methodName, exception.getMessage());
    }

    /**
     * Логирование вокруг выполнения метода сервиса
     */
    @Around("serviceLayer()")
    public Object logAroundService(ProceedingJoinPoint joinPoint) throws Throwable{
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();

        log.debug("==> Executing service: {} whith args: {}", methodName, args);

        long startTime = System.currentTimeMillis();

        try{
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;
            log.debug("<== Service completed: {} in {} ms", methodName, executionTime);
            return result;
        } catch (Exception ex){
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("<!> Service failed: {} after {} ms - Error: {}", methodName, executionTime, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Логирование перед выполнением метода репозитория
     */
    @Before("repositoryLayer()")
    public void logBeforRepository(JoinPoint joinPoint){
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();
        log.debug("--- DB query: {} whith params: {}", methodName, args);
    }

    /**
     * Логирование после успешного выполнения метода репозитория
     */
    @AfterReturning(pointcut = "repositoryLayer()", returning = "result")
    public void logAfterRepository(JoinPoint joinPoint, Object result){
        String methodName = joinPoint.getSignature().toShortString();
        String resultInfo = result !=null ? result.getClass().getSimpleName() : "null";

        log.debug("--- DB result: {} returned: {}", methodName, resultInfo);
    }

    /**
     * Логирование обработки исключений в GlobalExceptionHandler
     */
    @Around("exceptionHandlerLayer()")
    public Object logAroundExceptionHandlerLayer(ProceedingJoinPoint joinPoint) throws Throwable{
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();

        Throwable exception = null;
        for(Object arg: args){
            if(arg instanceof Throwable){
                exception = (Throwable) arg;
                break;
            }
        }

        if(exception !=null){
            log.warn(">>> Handling exception in: {} - Exception type: {} - Message: {}",
                    methodName,
                    exception.getClass().getSimpleName(),
                    exception.getMessage());
        }

        Object result = joinPoint.proceed();

        log.warn("<<< Exception handled by: {}", methodName);
        return result;
    }
}
