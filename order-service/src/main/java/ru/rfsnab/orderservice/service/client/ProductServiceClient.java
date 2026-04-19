package ru.rfsnab.orderservice.service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.orderservice.exception.ProductNotFoundException;
import ru.rfsnab.orderservice.exception.ServiceUnavailableException;
import ru.rfsnab.orderservice.models.dto.product.ProductDto;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * Клиент для взаимодействия с product-service.
 * Получает информацию о товарах для обогащения корзины и валидации заказов.
 */
@Service
@Slf4j
public class ProductServiceClient {

    private final RestTemplate restTemplate;
    private final String productServiceUrl;

    public ProductServiceClient(RestTemplate restTemplate,
                                @Value("${services.product.url}") String productServiceUrl) {
        this.restTemplate = restTemplate;
        this.productServiceUrl = productServiceUrl;
    }

    public ProductDto getProduct(Long productId) {
        try {
            return restTemplate.getForObject(
                    productServiceUrl + "/api/v1/products/{id}",
                    ProductDto.class,
                    productId
            );
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductNotFoundException("Product not found " + productId);
        } catch (Exception e) {
            log.error("Product service unavailable: {}", e.getMessage());
            throw new ServiceUnavailableException("Product service unavailable");
        }
    }

    public Map<Long, ProductDto> getProducts(Set<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }

        List<CompletableFuture<ProductDto>> futures = productIds.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return getProduct(id);
                    } catch (ProductNotFoundException e) {
                        return null;
                    }
                }))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ProductDto::id, Function.identity()));
    }
}
