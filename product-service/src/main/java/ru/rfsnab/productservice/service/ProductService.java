package ru.rfsnab.productservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.productservice.exception.BusinessException;
import ru.rfsnab.productservice.exception.CategoryNotFoundException;
import ru.rfsnab.productservice.exception.ProductNotFoundException;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.repository.CategoryRepository;
import ru.rfsnab.productservice.repository.ProductRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final SlugGeneratorService slugGenerator;

    /***
     * Подсчет товаров в категории
     * @param parentId категории
     * @return long
     */
    public long countByCategoryId(Long parentId){
        return productRepository.countByCategoryId(parentId);
    }

    /**
     * Количество активных товаров в наличии (эффективный остаток > 0:
     * собственный stock + stock активных дочерних вариантов)
     */
    @Transactional(readOnly = true)
    public long countAvailableProducts() {
        return productRepository.countAvailableProducts();
    }

    /**
     * Создать товар
     * category_id может быть NULL
     */
    @Transactional
    public Product createProduct(Product product){
        log.info("Создание продукта: {}", product.getName());

        String baseSlug = slugGenerator.generateSlug(product.getName());
        String uniqSlug = generateUniqueSlug(baseSlug);
        product.setSlug(uniqSlug);

        if(product.getCategory() != null){
            product.setCategory(categoryRepository.findById(product.getCategory().getId())
                    .orElseThrow(()-> new CategoryNotFoundException("Категория с id " + product.getCategory().getId() +
                            " не найдена.")));
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
        existing.setMaterial(updatedProduct.getMaterial());
        existing.setPrice(updatedProduct.getPrice());
        existing.setWholesalePrice(updatedProduct.getWholesalePrice());
        existing.setStockQuantity(updatedProduct.getStockQuantity());
        existing.setSku(updatedProduct.getSku());
        existing.setUnitOfMeasure(updatedProduct.getUnitOfMeasure());
        existing.setVatRate(updatedProduct.getVatRate());
        existing.setIsActive(updatedProduct.getIsActive());
        existing.setIsFeatured(updatedProduct.getIsFeatured());

        if (updatedProduct.getCategory() != null) {
            Category category = categoryRepository.findById(updatedProduct.getCategory().getId())
                    .orElseThrow(()->
                            new CategoryNotFoundException("Категория с id " + updatedProduct.getCategory().getId() +
                    " не найдена."));
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
    public Page<Product> getProductsByCategory(Long categoryId, Pageable pageable) {
        log.debug("Getting products for category id={}", categoryId);

        if (!categoryRepository.existsById(categoryId)) {
            throw new CategoryNotFoundException(categoryId);
        }

        return productRepository.findByCategoryId(categoryId, pageable);
    }

    /**
     * Поиск товаров по названию
     */
    public Page<Product> searchProducts(String query, Pageable pageable) {
        log.debug("Searching products with query: {}", query);
        return productRepository.searchByName(query, pageable);
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
     * Пагинация товаров (дочерние варианты скрыты)
     */
    public Page<Product> getProductsPage(Pageable pageable) {
        return productRepository.findByIsActiveTrueAndIsVariantChildFalse(pageable);
    }

    public Page<Product> getAllProductsPage(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    public Page<Product> getAllProductsAdminPage(Long categoryId, Boolean isActive, Pageable pageable) {
        pageable = withIdTiebreaker(pageable);
        if (categoryId != null) {
            if (!categoryRepository.existsById(categoryId)) {
                throw new CategoryNotFoundException(categoryId);
            }
            return isActive == null
                    ? productRepository.findByCategoryId(categoryId, pageable)
                    : productRepository.findByCategoryIdAndIsActive(categoryId, isActive, pageable);
        }
        return isActive == null
                ? productRepository.findAll(pageable)
                : productRepository.findByIsActive(isActive, pageable);
    }

    /**
     * Пагинация товаров по категории (включая товары всех подкатегорий поддерева,
     * т.к. товары привязываются только к листовым категориям)
     */
    public Page<Product> getProductsByCategoryPage(Long categoryId, Pageable pageable) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new CategoryNotFoundException(categoryId);
        }
        List<Long> categoryIds = categoryService.getSubtreeCategoryIds(categoryId);
        return productRepository.findByCategoryIdInAndIsActiveTrueAndIsVariantChildFalse(categoryIds, withIdTiebreaker(pageable));
    }

    /**
     * Сортировка по displayOrder/name неуникальна (дубли значений) — без тайбрейкера
     * порядок строк между страницами недетерминирован: строка может пропасть из выдачи
     * или задублироваться на соседних страницах.
     */
    private static Pageable withIdTiebreaker(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                pageable.getSort().and(Sort.by(Sort.Direction.ASC, "id")));
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
                    .orElseThrow(()-> new CategoryNotFoundException("Категория с id " + categoryId + " не найдена."));

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

    /**
     * Удаление товара по id
     * @param id товара
     */
    public void deleteProduct(Long id){
        if(productRepository.findById(id).isEmpty()){
            throw new ProductNotFoundException("Товар не найден с id="+id);
        }
        productRepository.deleteById(id);
    }

    /**
     * Массовое удаление товаров по id
     */
    @Transactional
    public void batchDelete(List<Long> ids) {
        log.info("Массовое удаление {} товаров", ids.size());
        productRepository.deleteAllById(ids);
    }

    @Transactional
    public void batchUpdateCategory(List<Long> productIds, Long categoryId) {
        Category category = categoryId != null
                ? categoryRepository.findById(categoryId).orElseThrow(
                        ()-> new CategoryNotFoundException("Категория с id " + categoryId + " не найдена."))
                : null;

        productRepository.findAllById(productIds).forEach(product -> {
            product.setCategory(category);
            product.setUpdatedAt(LocalDateTime.now());
        });
    }

    /**
     * Установить/снять родительский товар (объединение в варианты)
     */
    @Transactional
    public Product setParent(Long productId, Long parentProductId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (parentProductId == null) {
            product.setIsVariantChild(false);
            product.setParentProductId(null);
        } else {
            Product parent = productRepository.findById(parentProductId)
                    .orElseThrow(() -> new ProductNotFoundException(parentProductId));
            if (parent.getIsVariantChild()) {
                throw new BusinessException("Нельзя назначить дочерний товар родителем");
            }
            if (parentProductId.equals(productId)) {
                throw new BusinessException("Товар не может быть родителем самого себя");
            }
            product.setIsVariantChild(true);
            product.setParentProductId(parentProductId);
        }

        return productRepository.save(product);
    }

    /**
     * Получить дочерние товары-варианты родителя
     */
    @Transactional(readOnly = true)
    public List<Product> getChildren(Long parentId) {
        return productRepository.findChildrenWithAttributes(parentId);
    }

    /**
     * Найти родителей, у которых есть активные дочерние варианты,
     * среди заданного списка id (для пометки hasVariants в листинге).
     */
    @Transactional(readOnly = true)
    public Set<Long> findParentIdsWithActiveChildren(List<Long> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(productRepository.findParentIdsWithActiveChildren(ids));
    }

    public Product findByExternalId(String externalId) {
        return productRepository.findByExternalId(externalId).orElseThrow(
                ()-> new ProductNotFoundException("Товар с id= " + externalId + " не найден"));
    }

    /**
     * Обновить порядок отображения товара в категории
     */
    @Transactional
    public Product updateDisplayOrder(Long productId, Integer displayOrder) {
        log.info("Updating displayOrder for product id={}, order={}", productId, displayOrder);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        product.setDisplayOrder(displayOrder);
        return productRepository.save(product);
    }

    /**
     * Обновить displayOrder для списка товаров одним вызовом
     */
    @Transactional
    public void reorderProducts(Map<Long, Integer> orders) {
        orders.forEach((id, order) -> {
            Product product = productRepository.findById(id)
                    .orElseThrow(() -> new ProductNotFoundException(id));
            product.setDisplayOrder(order);
            productRepository.save(product);
        });
    }

    /**
     * Массовая активация товаров
     */
    @Transactional
    public void batchUpdateActive(List<Long> productIds, Boolean isActive) {
        log.info("Массовое обновление isActive={} для {} товаров", isActive, productIds.size());
        List<Product> products = productRepository.findAllById(productIds);
        products.forEach(p -> p.setIsActive(isActive));
        productRepository.saveAll(products);
    }
}
