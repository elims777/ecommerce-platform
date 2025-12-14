package ru.rfsnab.productservice.service;

import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rfsnab.productservice.dto.CategoryTreeDTO;
import ru.rfsnab.productservice.exception.BusinessException;
import ru.rfsnab.productservice.exception.CategoryNotFoundException;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.repository.CategoryRepository;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final ProductService productService;

    // Кэш дерева категорий (в памяти)
    private List<CategoryTreeDTO> cachedCategoryTree = new ArrayList<>();

    /**
     * Инициализация дерева категорий при старте приложения
     */
    @PostConstruct
    public void initCategoryTree() {
        log.info("Initializing category tree...");
        refreshCategoryTree();
        log.info("Category tree initialized with {} root categories", cachedCategoryTree.size());
    }

    /**
     * Получить дерево категорий (из кэша)
     */
    public List<CategoryTreeDTO> getCategoryTree() {
        return cachedCategoryTree;
    }

    /**
     * Построить дерево категорий из БД и обновить кэш
     */
    public synchronized void refreshCategoryTree() {
        log.debug("Refreshing category tree from database...");

        // 1. Загружаем все категории из БД одним запросом
        List<Category> allCategories = categoryRepository.findAll();

        // 2. Строим дерево в памяти
        cachedCategoryTree = buildTree(allCategories);

        log.debug("Category tree refreshed: {} categories loaded", allCategories.size());
    }

    /**
     * Построение дерева из плоского списка категорий
     */
    private List<CategoryTreeDTO> buildTree(List<Category> categories) {
        // Map для быстрого поиска по ID
        Map<Long, CategoryTreeDTO> map = new HashMap<>();

        // Создаем DTO для каждой категории
        for (Category category : categories) {
            CategoryTreeDTO dto = mapToDTO(category);
            map.put(category.getId(), dto);
        }

        // Связываем родителей и детей
        List<CategoryTreeDTO> rootCategories = new ArrayList<>();

        for (Category category : categories) {
            CategoryTreeDTO dto = map.get(category.getId());

            if (category.getParent() == null) {
                // Это корневая категория
                rootCategories.add(dto);
            } else {
                // Добавляем к родителю
                CategoryTreeDTO parent = map.get(category.getParent().getId());
                if (parent != null) {
                    parent.addChild(dto);
                }
            }
        }

        // Сортируем по displayOrder
        sortByDisplayOrder(rootCategories);

        return rootCategories;
    }

    /**
     * Рекурсивная сортировка дерева по displayOrder
     */
    private void sortByDisplayOrder(List<CategoryTreeDTO> categories) {
        categories.sort(Comparator.comparing(CategoryTreeDTO::getDisplayOrder));

        for (CategoryTreeDTO category : categories) {
            if (!category.getChildren().isEmpty()) {
                sortByDisplayOrder(category.getChildren());
            }
        }
    }

    /**
     * Маппинг Category в CategoryTreeDTO
     */
    private CategoryTreeDTO mapToDTO(Category category) {
        return CategoryTreeDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .isActive(category.getIsActive())
                .displayOrder(category.getDisplayOrder())
                .build();
    }

    /**
     * Создать категорию
     */
    @Transactional
    public Category createCategory(Category category) {
        log.info("Creating category: {}", category.getName());

        Category saved = categoryRepository.save(category);

        // Обновляем дерево в кэше
        refreshCategoryTree();

        return saved;
    }

    /**
     * Обновить категорию
     */
    @Transactional
    public Category updateCategory(Long id, Category updatedCategory) {
        log.info("Updating category id={}", id);

        Category existing = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Категория не найдена: " + id));

        // Обновляем поля
        existing.setName(updatedCategory.getName());
        existing.setSlug(updatedCategory.getSlug());
        existing.setDescription(updatedCategory.getDescription());
        existing.setIsActive(updatedCategory.getIsActive());
        existing.setDisplayOrder(updatedCategory.getDisplayOrder());

        if (updatedCategory.getParent() != null) {
            existing.setParent(updatedCategory.getParent());
        }

        Category saved = categoryRepository.save(existing);

        // Обновляем дерево в кэше
        refreshCategoryTree();

        return saved;
    }

    /**
     * Удалить категорию
     */
    @Transactional
    public void deleteCategory(Long id) {
        log.info("Deleting category id={}", id);

        // Проверяем есть ли дочерние категории
        if (categoryRepository.existsByParentId(id)) {
            throw new BusinessException(
                    "Невозможно удалить категорию. У неё есть подкатегории."
            );
        }

        // Проверяем есть ли товары
        long productCount = productService.countByCategoryId(id);
        if (productCount > 0) {
            throw new BusinessException(
                    "Невозможно удалить категорию. В ней " + productCount + " товар(ов)."
            );
        }

        categoryRepository.deleteById(id);

        // Обновляем дерево в кэше
        refreshCategoryTree();
    }

    /**
     * Получить категорию по ID
     */
    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Категория не найдена: " + id));
    }

    /**
     * Получить категорию по slug
     */
    public Category getCategoryBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new CategoryNotFoundException("Категория не найдена: " + slug));
    }

    /**
     * Установить родительскую категорию
     */
    @Transactional
    public Category setParent(Long categoryId, Long parentId) {
        log.info("Setting parent for category id={}, parent id={}", categoryId, parentId);

        Category category = getCategoryById(categoryId);
        Category parent = getCategoryById(parentId);

        long productCount = productService.countByCategoryId(parent.getId());
        if (productCount > 0) {
            throw new BusinessException(
                    "Категория '" + parent.getName() + "' содержит товары (" +
                            productCount + "). Сначала перенесите товары из неё."
            );
        }

        category.setParent(parent);
        Category saved = categoryRepository.save(category);

        // Обновляем дерево в кэше
        refreshCategoryTree();

        return saved;
    }

    /**
     * Проверка, что категория конечная (нет детей)
     */
    public boolean isLeafCategory(Long categoryId) {
        return !categoryRepository.existsByParentId(categoryId);
    }
}
