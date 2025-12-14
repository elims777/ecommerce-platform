package ru.rfsnab.productservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.productservice.exception.BusinessException;
import ru.rfsnab.productservice.exception.CategoryNotFoundException;
import ru.rfsnab.productservice.exception.ProductNotFoundException;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.repository.CategoryRepository;
import ru.rfsnab.productservice.repository.ProductRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SlugGeneratorService slugGenerator;

    /***
     * Подсчет товаров в категории
     * @param parentId
     * @return
     */
    public long countByCategoryId(Long parentId){
        return productRepository.countByCategoryId(parentId);
    }

    /**
     * Создать товар
     * category_id может быть NULL
     */
    @Transactional
    public Product createProduct(Product product){
        log.info("Создание продукта: {}", product.getName());

        String baseSlug = slugGenerator.generatedSlug(product.getName());
        String uniqSlug = generateUniqueSlug(baseSlug);
        product.setSlug(uniqSlug);

        if(product.getCategory() != null){
            Category category = categoryRepository.findById(product.getCategory().getId())
                    .orElseThrow(() -> new CategoryNotFoundException("Категория c id "+ product.getCategory().getId() +
                            " не найдена: "));
            product.setCategory(category);
        }

        Product saved = productRepository.save(product);
        log.info("Продукт создан с id={}, slug={}", saved.getId(), product.getSlug());

        return saved;
    }

    /**
     * Обновить товар
     */
    @Transactional
    public Product updateProduct(Long id, Product updatedProduct){
        log.info("Обновление продукта с id=", id);

        Product existing = productRepository.findById(id)
                .orElseThrow(()-> new ProductNotFoundException("Продукт с id=" + id+ " не найден"));

        existing.setName(updatedProduct.getName());
        existing.setDescription(updatedProduct.getDescription());
        existing.setShortDescription(updatedProduct.getShortDescription());
        existing.setPrice(updatedProduct.getPrice());
        existing.setStockQuantity(updatedProduct.getStockQuantity());
        existing.setIsActive(updatedProduct.getIsActive());
        existing.setIsFeatured(updatedProduct.getIsFeatured());

        if (updatedProduct.getCategory() != null) {
            Category category = categoryRepository.findById(updatedProduct.getCategory().getId())
                    .orElseThrow(() -> new CategoryNotFoundException(updatedProduct.getCategory().getId()));
            existing.setCategory(category);
        } else {
            existing.setCategory(null);
        }

        Product saved = productRepository.save(existing);
        log.info("Продукт с id={} обновлен ", saved.getId());

        return saved;
    }

    /**
     * Сохранить или обновить товар
     * Если id существует → обновляем, если нет → создаем
     */
    @Transactional
    public Product saveOrUpdate(Long id, Product product) {
        if (id != null && productRepository.existsById(id)) {
            return updateProduct(id, product);
        } else {
            return createProduct(product);
        }
    }

    /**
     * Получить товар по ID
     */
    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    /**
     * Получить товар по slug
     */
    public Product getProductBySlug(String slug) {
        return productRepository.findBySlug(slug)
                .orElseThrow(() -> new ProductNotFoundException(slug));
    }

    /**
     * Получить товары по категории
     */
    public List<Product> getProductsByCategory(Long categoryId) {
        log.debug("Getting products for category id={}", categoryId);

        if (!categoryRepository.existsById(categoryId)) {
            throw new CategoryNotFoundException(categoryId);
        }

        return productRepository.findByCategoryId(categoryId);
    }

    /**
     * Поиск товаров по названию
     */
    public List<Product> searchProducts(String query) {
        log.debug("Searching products with query: {}", query);
        return productRepository.searchByName(query);
    }

    /**
     * Получить рекомендуемые товары
     */
    public List<Product> getFeaturedProducts() {
        return productRepository.findFeatured();
    }

    /**
     * Получить активные товары
     */
    public List<Product> getActiveProducts() {
        return productRepository.findAllActive();
    }

    /**
     * Пагинация товаров
     */
    public Page<Product> getProductsPage(Pageable pageable) {
        return productRepository.findByIsActiveTrue(pageable);
    }

    /**
     * Пагинация товаров по категории
     */
    public Page<Product> getProductsByCategoryPage(Long categoryId, Pageable pageable) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new CategoryNotFoundException(categoryId);
        }

        return productRepository.findByCategoryIdAndIsActiveTrue(categoryId, pageable);
    }

    /**
     * Увеличить количество на складе
     */
    @Transactional
    public Product increaseStock(Long productId, Integer quantity) {
        log.info("Increasing stock for product id={}, quantity={}", productId, quantity);

        if (quantity <= 0) {
            throw new BusinessException("Количество должно быть положительным");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        product.setStockQuantity(product.getStockQuantity() + quantity);
        Product saved = productRepository.save(product);

        log.info("Stock increased for product id={}, new stock={}", productId, saved.getStockQuantity());
        return saved;
    }

    /**
     * Уменьшить количество на складе
     */
    @Transactional
    public Product decreaseStock(Long productId, Integer quantity) {
        log.info("Decreasing stock for product id={}, quantity={}", productId, quantity);

        if (quantity <= 0) {
            throw new BusinessException("Количество должно быть положительным");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (product.getStockQuantity() < quantity) {
            throw new BusinessException(
                    "Недостаточно товара на складе. Доступно: " + product.getStockQuantity()
            );
        }

        product.setStockQuantity(product.getStockQuantity() - quantity);
        Product saved = productRepository.save(product);

        log.info("Stock decreased for product id={}, new stock={}", productId, saved.getStockQuantity());
        return saved;
    }

    /**
     * Установить количество на складе
     */
    @Transactional
    public Product setStock(Long productId, Integer quantity) {
        log.info("Setting stock for product id={}, quantity={}", productId, quantity);

        if (quantity < 0) {
            throw new BusinessException("Количество не может быть отрицательным");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        product.setStockQuantity(quantity);
        Product saved = productRepository.save(product);

        log.info("Stock set for product id={}, stock={}", productId, saved.getStockQuantity());
        return saved;
    }

    /**
     * Проверить доступность товара
     */
    public boolean checkAvailability(Long productId, Integer requestedQuantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        return product.getStockQuantity() >= requestedQuantity;
    }

    /**
     * Активировать товар
     */
    @Transactional
    public Product activateProduct(Long productId) {
        log.info("Activating product id={}", productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        product.setIsActive(true);
        Product saved = productRepository.save(product);

        log.info("Product activated id={}", productId);
        return saved;
    }

    /**
     * Деактивировать товар
     */
    @Transactional
    public Product deactivateProduct(Long productId) {
        log.info("Deactivating product id={}", productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        product.setIsActive(false);
        Product saved = productRepository.save(product);

        log.info("Product deactivated id={}", productId);
        return saved;
    }

    /**
     * Обновить категорию товара с валидацией
     */
    @Transactional
    public Product updateProductCategory(Long productId, Long categoryId) {
        log.info("Updating category for product id={}, new category id={}", productId, categoryId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new CategoryNotFoundException(categoryId));

            // Валидация: категория должна быть leaf (без детей)
            if (categoryRepository.existsByParentId(categoryId)) {
                throw new BusinessException(
                        "Товары можно добавлять только в конечные категории (без подкатегорий)"
                );
            }

            product.setCategory(category);
        } else {
            product.setCategory(null);
        }

        Product saved = productRepository.save(product);
        log.info("Category updated for product id={}", productId);

        return saved;
    }

    /**
     * Генерация уникального slug
     */
    private String generateUniqueSlug(String baseSlug) {
        String slug = baseSlug;
        int counter = 1;

        while (productRepository.existsBySlug(slug)) {
            counter++;
            slug = slugGenerator.makeUnique(baseSlug, counter);
        }

        return slug;
    }

}
