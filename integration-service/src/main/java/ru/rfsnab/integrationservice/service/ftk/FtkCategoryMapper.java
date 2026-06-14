package ru.rfsnab.integrationservice.service.ftk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.integrationservice.config.IntegrationProperties;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Маппинг пути категорий ФТК ("Спецодежда > Костюмы") → slug категории в product-service.
 *
 * Стратегия:
 * 1. Проверяем локальный кэш (в рамках одного запуска импорта)
 * 2. GET /api/v1/categories/by-slug/{slug} — проверяем существование
 * 3. Если не найдена → POST /api/v1/categories — создаём под корнем FTK
 * 4. Возвращаем slug (null если не удалось)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FtkCategoryMapper {

    private static final String CATEGORIES_URI = "/api/v1/categories";
    private static final String BY_SLUG_URI = "/api/v1/categories/by-slug/";

    private final RestTemplate productServiceRestTemplate;
    private final IntegrationProperties properties;

    /** Кэш: путь категории → slug, живёт в рамках одного сеанса запуска */
    private final Map<String, String> slugCache = new HashMap<>();

    /**
     * Сбрасывает кэш перед новым запуском импорта.
     */
    public void resetCache() {
        slugCache.clear();
    }

    /**
     * Возвращает slug категории для указанного пути.
     * Если категория не существует — создаёт. При ошибке — возвращает rootCategorySlug.
     */
    public String resolveSlug(String categoryPath) {
        if (categoryPath == null || categoryPath.isBlank()) {
            return properties.getFtk().getRootCategorySlug();
        }

        if (slugCache.containsKey(categoryPath)) {
            return slugCache.get(categoryPath);
        }

        // Берём последний сегмент пути как имя категории
        String[] parts = categoryPath.split(" > ");
        String leafName = parts[parts.length - 1].trim();
        String candidateSlug = toSlug(leafName);

        String resolvedSlug = findOrCreateCategory(candidateSlug, leafName);
        slugCache.put(categoryPath, resolvedSlug);
        return resolvedSlug;
    }

    private String findOrCreateCategory(String slug, String name) {
        String baseUrl = properties.getProductService().getUrl();

        // Проверяем существование
        try {
            CategoryCheckResponse resp = productServiceRestTemplate.getForObject(
                    baseUrl + BY_SLUG_URI + slug, CategoryCheckResponse.class
            );
            if (resp != null && resp.id() != null) {
                log.debug("Категория найдена: slug={}", slug);
                return slug;
            }
        } catch (RestClientException e) {
            // 404 → нужно создать
        }

        // Создаём под корневой категорией FTK
        return createCategory(name, slug, baseUrl);
    }

    private String createCategory(String name, String slug, String baseUrl) {
        try {
            Long parentId = resolveRootCategoryId(baseUrl);
            Map<String, Object> request = new HashMap<>();
            request.put("name", name);
            request.put("slug", slug);
            request.put("parentId", parentId);
            request.put("isActive", true);
            request.put("displayOrder", 0);

            CategoryCheckResponse created = productServiceRestTemplate.postForObject(
                    baseUrl + CATEGORIES_URI, request, CategoryCheckResponse.class
            );
            if (created != null && created.id() != null) {
                log.info("Создана категория: name='{}', slug={}", name, slug);
                return created.slug() != null ? created.slug() : slug;
            }
        } catch (Exception e) {
            log.warn("Не удалось создать категорию '{}': {}", name, e.getMessage());
        }
        return properties.getFtk().getRootCategorySlug();
    }

    private Long resolveRootCategoryId(String baseUrl) {
        String rootSlug = properties.getFtk().getRootCategorySlug();
        try {
            CategoryCheckResponse root = productServiceRestTemplate.getForObject(
                    baseUrl + BY_SLUG_URI + rootSlug, CategoryCheckResponse.class
            );
            return (root != null) ? root.id() : null;
        } catch (Exception e) {
            log.warn("Корневая категория FTK '{}' не найдена", rootSlug);
            return null;
        }
    }

    /**
     * Транслитерация + slug: "Спецодежда" → "spetsodezhda"
     */
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    public static String toSlug(String input) {
        if (input == null) return "category";
        String normalized = Normalizer.normalize(input.toLowerCase(), Normalizer.Form.NFD);
        // Кириллица → транслит упрощённый через замену
        normalized = transliterate(normalized);
        // Убираем диакритику и нечитаемые символы
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

    /** Минимальный ответ от product-service для категории */
    private record CategoryCheckResponse(Long id, String slug) {}
}
