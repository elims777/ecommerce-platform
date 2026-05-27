package ru.rfsnab.productservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.repository.ProductRepository;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductWholesalePriceTest {

    @Autowired MockMvc mockMvc;
    @Autowired ProductRepository repo;

    @Test
    @WithMockUser
    void getProduct_returnsBothPrices() throws Exception {
        Product p = repo.save(Product.builder()
                .name("Тест товар").slug("test-wholesale-product")
                .price(new BigDecimal("1000.00"))
                .wholesalePrice(new BigDecimal("800.00"))
                .stockQuantity(10).isActive(true).isFeatured(false)
                .build());

        mockMvc.perform(get("/api/v1/products/" + p.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(1000.00))
                .andExpect(jsonPath("$.wholesalePrice").value(800.00));
    }

    @Test
    @WithMockUser
    void getProduct_wholesalePriceNull_returnsNull() throws Exception {
        Product p = repo.save(Product.builder()
                .name("Тест товар 2").slug("test-no-wholesale-product")
                .price(new BigDecimal("500.00"))
                .stockQuantity(5).isActive(true).isFeatured(false)
                .build());

        mockMvc.perform(get("/api/v1/products/" + p.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(500.00))
                .andExpect(jsonPath("$.wholesalePrice").isEmpty());
    }
}
