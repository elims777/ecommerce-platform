package ru.rfsnab.productservice.service;

import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.rfsnab.productservice.dto.CategoryTreeDTO;
import ru.rfsnab.productservice.exception.BusinessException;
import ru.rfsnab.productservice.exception.CategoryNotFoundException;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.repository.CategoryRepository;
import ru.rfsnab.productservice.repository.ProductRepository;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final SlugGeneratorService slugGenerator;

    // Кэш дерева категорий (в памяти)
    private List<CategoryTreeDTO> cachedCategoryTree = new ArrayList<>();

    /**
     * Инициализация дерева категорий при старте приложения
     */
    @PostConstruct
    public void initCategoryTree() {
        refreshCategoryTree();
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
        List<Category> allCategories = categoryRepository.findAll();
        cachedCategoryTree = buildTree(allCategories);
    }

    /**
     * Построение дерева из плоского списка категорий
     */
    private List<CategoryTreeDTO> buildTree(List<Category> categories) {
        Map<Long, CategoryTreeDTO> map = new HashMap<>();

        for (Category category : categories) {
            CategoryTreeDTO dto = CategoryTreeDTO.builder()
                    .id(category.getId())
                    .name(category.getName())
                    .slug(category.getSlug())
                    .description(category.getDescription())
                    .parentId(category.getParent() != null ? category.getParent().getId() : null)
                    .isActive(category.getIsActive())
                    .displayOrder(category.getDisplayOrder())
                    .build();
            map.put(category.getId(), dto);
        }

        List<CategoryTreeDTO> rootCategories = new ArrayList<>();

        for (Category category : categories) {
            CategoryTreeDTO dto = map.get(category.getId());

            if (category.getParent() == null) {
                rootCategories.add(dto);
            } else {
                CategoryTreeDTO parent = map.get(category.getParent().getId());
                if (parent != null) {
                    parent.addChild(dto);
                }
            }
        }

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
     * Создать категорию
     */
    @Transactional
    public Category createCategory(Category category) {
        // Устанавливаем дефолты
        if (category.getIsActive() == null) {
            category.setIsActive(false);
        }
        if (category.getDisplayOrder() == null) {
            category.setDisplayOrder(0);
        }

        // Генерируем уникальный slug
        String baseSlug = slugGenerator.generateSlug(category.getName());
        String uniqueSlug = generateUniqueSlug(baseSlug);
        category.setSlug(uniqueSlug);

        // Устанавливаем родителя если указан
        if (category.getParent() != null && category.getParent().getId() != null) {
            Category parent = getCategoryById(category.getParent().getId());
            category.setParent(parent);
        }

        Category saved = categoryRepository.save(category);
        refreshCategoryTree();

        return saved;
    }

    /**
     * Обновить категорию
     */
    @Transactional
    public Category updateCategory(Long id, Category updatedCategory) {
        Category existing = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Категория не найдена: " + id));

        if (updatedCategory.getName() != null) {
            existing.setName(updatedCategory.getName());
        }
        if (updatedCategory.getDescription() != null) {
            existing.setDescription(updatedCategory.getDescription());
        }
        if (updatedCategory.getIsActive() != null) {
            existing.setIsActive(updatedCategory.getIsActive());
        }
        if (updatedCategory.getDisplayOrder() != null) {
            existing.setDisplayOrder(updatedCategory.getDisplayOrder());
        }

        // Обновляем родителя: null = сделать корневой, id = подвесить под родителя
        if (updatedCategory.getParent() != null && updatedCategory.getParent().getId() != null) {
            Category parent = categoryRepository.findById(updatedCategory.getParent().getId())
                    .orElseThrow(() -> new CategoryNotFoundException("Родительская категория не найдена: " + updatedCategory.getParent().getId()));
            existing.setParent(parent);
        } else {
            existing.setParent(null);
        }

        Category saved = categoryRepository.save(existing);
        refreshCategoryTree();

        return saved;
    }

    /**
     * Удалить категорию
     */
    @Transactional
    public void deleteCategory(Long id) {
        // Проверяем есть ли дочерние категории
        if (categoryRepository.existsByParentId(id)) {
            throw new BusinessException("Невозможно удалить категорию. У неё есть подкатегории.");
        }

        // Проверяем есть ли товары
        long productCount = productRepository.countByCategoryId(id);
        if (productCount > 0) {
            throw new BusinessException("Невозможно удалить категорию. В ней " + productCount + " товар(ов).");
        }

        categoryRepository.deleteById(id);
        refreshCategoryTree();
    }

    /**
     * Получить категорию по ID
     */
    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Категория c id " + id + " не найдена"));
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
        Category category = getCategoryById(categoryId);
        Category parent = getCategoryById(parentId);

        // Проверяем что у родителя нет товаров
        long productCount = productRepository.countByCategoryId(parent.getId());
        if (productCount > 0) {
            throw new BusinessException(
                    "Категория '" + parent.getName() + "' содержит товары (" +
                            productCount + "). Сначала перенесите товары из неё."
            );
        }

        category.setParent(parent);
        Category saved = categoryRepository.save(category);
        refreshCategoryTree();

        return saved;
    }

    /**
     * Проверка, что категория конечная (нет детей)
     */
    public boolean isLeafCategory(Long categoryId) {
        return !categoryRepository.existsByParentId(categoryId);
    }

    /**
     * Генерация уникального slug
     */
    private String generateUniqueSlug(String baseSlug) {
        String slug = baseSlug;
        int counter = 1;

        while (categoryRepository.existsBySlug(slug)) {
            counter++;
            slug = slugGenerator.makeUnique(baseSlug, counter);
        }

        return slug;
    }

    public boolean existsById(Long categoryId) {
        return categoryRepository.existsById(categoryId);
    }

    public boolean existsByParentId(Long categoryId) {
        return categoryRepository.existsByParentId(categoryId);
    }

    /**
     * Создать или обновить категорию по externalId (UUID группы из ФТК/1С).
     * Используется FtkCategoryMapper при импорте для построения дерева категорий.
     */
    @Transactional
    public Category upsertByExternalId(String externalId, String name, Long parentCategoryId) {
        Category category = categoryRepository.findByExternalId(externalId).orElse(null);

        if (category == null) {
            String baseSlug = slugGenerator.generateSlug(name);
            String uniqueSlug = generateUniqueSlug(baseSlug);

            Category parent = parentCategoryId != null
                    ? categoryRepository.findById(parentCategoryId).orElse(null)
                    : null;

            category = Category.builder()
                    .externalId(externalId)
                    .name(name)
                    .slug(uniqueSlug)
                    .isActive(true)
                    .displayOrder(0)
                    .parent(parent)
                    .build();
        } else {
            // категория уже существует — не перезаписываем name и parent
        }

        Category saved = categoryRepository.save(category);
        refreshCategoryTree();
        return saved;
    }
}