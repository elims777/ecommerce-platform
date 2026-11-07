package ru.rfsnab.productservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Приоритетные категории YML-фида: ID из {@code feed.priority-category-ids} (через запятую).
 * Товары этих категорий получают в фиде {@code custom_score=10} и {@code custom_label_0=high},
 * чтобы в Единой перфоманс-кампании можно было отобрать их отдельным фильтром.
 */
@Slf4j
@Component
public class FeedPriorityCategories {

    private final Set<Long> categoryIds;

    public FeedPriorityCategories(@Value("${feed.priority-category-ids:}") String raw) {
        this.categoryIds = parse(raw);
    }

    public boolean contains(Long categoryId) {
        return categoryId != null && categoryIds.contains(categoryId);
    }

    private static Set<Long> parse(String raw) {
        Set<Long> ids = new HashSet<>();
        if (raw == null || raw.isBlank()) {
            return ids;
        }
        for (String part : raw.split(",")) {
            String value = part.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.valueOf(value));
            } catch (NumberFormatException e) {
                log.warn("feed.priority-category-ids: пропущено нечисловое значение '{}'", value);
            }
        }
        return Set.copyOf(ids);
    }
}
