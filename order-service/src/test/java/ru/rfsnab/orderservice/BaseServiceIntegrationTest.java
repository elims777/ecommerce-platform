package ru.rfsnab.orderservice;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rfsnab.orderservice.service.client.ProductServiceClient;
import ru.rfsnab.orderservice.service.client.UserServiceClient;

/**
 * Базовый класс для интеграционных тестов сервисов.
 * Единый @MockitoBean гарантирует, что Spring переиспользует один контекст.
 */
public abstract class BaseServiceIntegrationTest extends BaseIntegrationTest{
    @MockitoBean
    protected ProductServiceClient productServiceClient;

    @MockitoBean
    protected UserServiceClient userServiceClient;
}
