package ru.rfsnab.integrationservice.service.ftk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.integrationservice.config.IntegrationProperties;
import ru.rfsnab.integrationservice.service.ftk.FtkXmlParser.ClassifierData;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Строит дерево категорий в product-service из UUID-групп классификатора ФТК.
 *
 * Стратегия:
 * 1. resetCache() + loadClassifier() перед новым запуском импорта
 * 2. resolveCategory(groupUuid) → Long categoryId (создаёт при отсутствии)
 * 3. Иерархия соблюдается: сначала создаётся родитель, потом дочерний
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FtkCategoryMapper {

    private static final String UPSERT_URI   = "/api/v1/categories/upsert-by-external-id";
    private static final String BY_SLUG_URI  = "/api/v1/categories/by-slug/";

    private final RestTemplate productServiceRestTemplate;
    private final IntegrationProperties properties;

    /** Кэш: groupUuid → categoryId в product-service. Живёт один сеанс импорта. */
    private final Map<String, Long> categoryIdCache = new HashMap<>();

    private ClassifierData classifierData;
    private String rootCategorySlug;

    public void resetCache() {
        categoryIdCache.clear();
        classifierData = null;
        rootCategorySlug = null;
    }

    /**
     * Загружает данные классификатора в маппер перед началом импорта.
     * Использует slug корневой категории из конфигурации (для ФТК).
     */
    public void loadClassifier(ClassifierData data) {
        this.classifierData = data;
        this.rootCategorySlug = properties.getFtk().getRootCategorySlug();
    }

    /**
     * Загружает данные классификатора с явным slug корневой категории.
     * Используется для 1С (rootSlug = "import-1c") и других источников.
     */
    public void loadClassifier(ClassifierData data, String rootSlug) {
        this.classifierData = data;
        this.rootCategorySlug = rootSlug;
    }

    /**
     * Возвращает categoryId для UUID группы из классификатора.
     * Создаёт категорию через product-service если её ещё нет.
     * При ошибке — возвращает null (товар попадёт в корень FTK).
     */
    public Long resolveCategory(String groupUuid) {
        if (groupUuid == null || classifierData == null) return null;
        return resolveCategoryRecursive(groupUuid);
    }

    private Long resolveCategoryRecursive(String uuid) {
        if (categoryIdCache.containsKey(uuid)) {
            return categoryIdCache.get(uuid);
        }

        String name       = classifierData.groupPaths().get(uuid);
        String parentUuid = classifierData.groupParents().get(uuid);

        // Сначала обеспечиваем существование родителя
        Long parentId = (parentUuid != null) ? resolveCategoryRecursive(parentUuid) : resolveRootCategoryId();

        String leafName = extractLeafName(name);
        Long categoryId = upsertCategory(uuid, leafName, parentId);

        if (categoryId != null) {
            categoryIdCache.put(uuid, categoryId);
        }
        return categoryId;
    }

    private Long upsertCategory(String externalId, String name, Long parentId) {
        String baseUrl = properties.getProductService().getUrl();
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("externalId", externalId);
            request.put("name", name);
            request.put("parentCategoryId", parentId);

            CategoryResponse resp = productServiceRestTemplate.postForObject(
                    baseUrl + UPSERT_URI, request, CategoryResponse.class);

            if (resp != null && resp.id() != null) {
                log.debug("Категория upsert: externalId={}, name={}, id={}", externalId, name, resp.id());
                return resp.id();
            }
        } catch (Exception e) {
            log.warn("Не удалось upsert категорию externalId={}, name={}: {}", externalId, name, e.getMessage());
        }
        return null;
    }

    private Long resolveRootCategoryId() {
        String slug    = rootCategorySlug != null ? rootCategorySlug : properties.getFtk().getRootCategorySlug();
        String baseUrl = properties.getProductService().getUrl();
        try {
            CategoryResponse root = productServiceRestTemplate.getForObject(
                    baseUrl + BY_SLUG_URI + slug, CategoryResponse.class);
            return (root != null) ? root.id() : null;
        } catch (RestClientException e) {
            log.warn("Корневая категория '{}' не найдена", slug);
            return null;
        }
    }

    /** Берёт последний сегмент пути "А > Б > В" → "В". */
    private String extractLeafName(String path) {
        if (path == null) return "Без категории";
        String[] parts = path.split(" > ");
        return parts[parts.length - 1].trim();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Транслитерация slug (оставлена для совместимости с тестами)
    // ──────────────────────────────────────────────────────────────────────

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    public static String toSlug(String input) {
        if (input == null) return "category";
        String normalized = Normalizer.normalize(input.toLowerCase(), Normalizer.Form.NFD);
        normalized = transliterate(normalized);
        normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return NON_ALNUM.matcher(normalized).replaceAll("-").replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }

    private static String transliterate(String s) {
        return s
                .replace("а", "a").replace("б", "b").replace("в", "v").replace("г", "g")
                .replace("д", "d").replace("е", "e").replace("ё", "yo").replace("ж", "zh")
                .replace("з", "z").replace("и", "i").replace("й", "y").replace("к", "k")
                .replace("л", "l").replace("м", "m").replace("н", "n").replace("о", "o")
                .replace("п", "p").replace("р", "r").replace("с", "s").replace("т", "t")
                .replace("у", "u").replace("ф", "f").replace("х", "kh").replace("ц", "ts")
                .replace("ч", "ch").replace("ш", "sh").replace("щ", "shch").replace("ъ", "")
                .replace("ы", "y").replace("ь", "").replace("э", "e").replace("ю", "yu")
                .replace("я", "ya");
    }

    private record CategoryResponse(Long id, String slug) {}
}
