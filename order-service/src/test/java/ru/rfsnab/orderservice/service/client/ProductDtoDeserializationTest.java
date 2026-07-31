package ru.rfsnab.orderservice.service.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import ru.rfsnab.orderservice.models.dto.product.ProductDto;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Гарантия, что корзина и заказ получают ИМЕННО акционную цену: product-service отдаёт
 * в price/wholesalePrice уже пересчитанные значения, а поля акции (oldPrice, isSale и др.)
 * не должны ронять десериализацию ProductDto, у которого их нет.
 */
@DisplayName("ProductDto — десериализация ответа product-service с полями акции")
class ProductDtoDeserializationTest {

    /** Ответ product-service для товара со скидкой −15% (базовая цена 100 → акционная 85). */
    private static final String RESPONSE_WITH_SALE = """
            {
              "id": 42,
              "name": "Ботинки ФТК",
              "price": 85.00,
              "wholesalePrice": 102.00,
              "oldPrice": 100.00,
              "oldWholesalePrice": 120.00,
              "isSale": true,
              "saleMarkupPercent": -15,
              "ownSale": false,
              "ownSaleMarkupPercent": null,
              "stockQuantity": 5,
              "isActive": true,
              "externalId": "FTK-1",
              "sku": "ART-1",
              "unitOfMeasure": "шт",
              "categoryExternalId": "cat-1",
              "parentProductId": null,
              "images": [],
              "attributes": [],
              "displayOrder": 0
            }
            """;

    @Test
    @DisplayName("RestTemplate-конвертер разбирает ответ и берёт акционную цену")
    void deserializesSaleResponseTakingSalePrice() throws Exception {
        // тот же конвертер, что использует RestTemplate в ProductServiceClient
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        ObjectMapper mapper = converter.getObjectMapper();

        ProductDto dto = mapper.readValue(RESPONSE_WITH_SALE.getBytes(StandardCharsets.UTF_8), ProductDto.class);

        assertThat(converter.canRead(ProductDto.class, MediaType.APPLICATION_JSON)).isTrue();
        assertThat(dto.price()).isEqualByComparingTo("85.00");
        assertThat(dto.wholesalePrice()).isEqualByComparingTo("102.00");
        assertThat(dto.name()).isEqualTo("Ботинки ФТК");
    }
}
