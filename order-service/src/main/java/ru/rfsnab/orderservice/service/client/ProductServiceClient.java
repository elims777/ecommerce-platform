package ru.rfsnab.orderservice.service.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
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
@RequiredArgsConstructor
public class ProductServiceClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${services.product.url}")
    private String productServiceUrl;

    /**
     * Получение товара по ID.
     *
     * @param productId идентификатор товара
     * @return данные о товаре
     * @throws ProductNotFoundException если товар не найден
     * @throws ServiceUnavailableException если product-service недоступен
     */
    public ProductDto getProduct(Long productId){
        try{
            return webClientBuilder.build()
                    .get()
                    .uri(productServiceUrl + "/api/v1/products/{id}", productId)
                    .retrieve()
                    .bodyToMono(ProductDto.class)
                    .block();
        } catch (WebClientResponseException.NotFound e){
            throw new ProductNotFoundException("Product not found " + productId);
        } catch (Exception e){
            throw new ServiceUnavailableException("Product service unavailable");
        }
    }

    /**
     * Получение нескольких товаров по ID.
     * Запросы выполняются параллельно через CompletableFuture.
     * Товары, которые не найдены, игнорируются.
     *
     * @param productIds множество идентификаторов товаров
     * @return Map: productId → ProductDto
     */
    public Map<Long, ProductDto> getProducts(Set<Long> productIds){
        if(productIds.isEmpty()){
            return Map.of();
        }

        // Параллельные запросы для каждого товара
        List<CompletableFuture<ProductDto>> futures = productIds.stream()
                .map(id-> CompletableFuture.supplyAsync(()-> {
                    try{
                        return getProduct(id);
                    } catch (ProductNotFoundException e){
                        return null; //товар не найден, пропускаем, потом отфильтруем null
                    }
                }))
                .toList();

        // Собираем результаты, фильтруем null
        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ProductDto::id, Function.identity()));
    }
    
}
