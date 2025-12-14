package ru.rfsnab.productservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class SlugGeneratorService {

    private static final Map<Character, String> TRANSLITERATION_MAP = Stream.of(
            new String[][]{
                    {"а", "a"}, {"б", "b"}, {"в", "v"}, {"г", "g"}, {"д", "d"},
                    {"е", "e"}, {"ё", "yo"}, {"ж", "zh"}, {"з", "z"}, {"и", "i"},
                    {"й", "y"}, {"к", "k"}, {"л", "l"}, {"м", "m"}, {"н", "n"},
                    {"о", "o"}, {"п", "p"}, {"р", "r"}, {"с", "s"}, {"т", "t"},
                    {"у", "u"}, {"ф", "f"}, {"х", "h"}, {"ц", "ts"}, {"ч", "ch"},
                    {"ш", "sh"}, {"щ", "sch"}, {"ъ", ""}, {"ы", "y"}, {"ь", ""},
                    {"э", "e"}, {"ю", "yu"}, {"я", "ya"}
            }
    ).collect(Collectors.toMap(
            pair -> pair[0].charAt(0),
            pair -> pair[1]
    ));

    /**
     * Генерация slug из названия
     *
     * @param name название товара/категории
     * @return slug (URL-friendly строка)
     *
     * Пример:
     * "Огнетушитель ОП-4(з)-АВСЕ-01" → "ognetushitel-op-4-z-avse-01"
     */
    public String generatedSlug(String name){
        if(name == null || name.trim().isEmpty()){
            throw new IllegalArgumentException("Название не может быть пустым");
        }

        String result = name.toLowerCase().trim();

        // 1. Транслитерация кириллицы
        result = transliterate(result);

        // 2. Нормализация (удаление диакритических знаков для латиницы)
        result = Normalizer.normalize(result, Normalizer.Form.NFD);
        result = result.replaceAll("\\p{M}", "");

        // 3. Заменяем небуквенно-цифровые символы на дефисы
        result = result.replaceAll("[^a-z0-9]+", "-");

        // 4. Убираем дефисы в начале и конце
        result = result.replaceAll("^-+|-+$", "");

        // 5. Заменяем множественные дефисы на один
        result = result.replaceAll("-{2,}", "-");

        if (result.isEmpty()) {
            throw new IllegalArgumentException("Не удалось сгенерировать slug из: " + name);
        }

        return result;
    }


    /**
     * Транслитерация кириллицы в латиницу
     */
    private String transliterate(String text) {
        StringBuilder result = new StringBuilder();

        for(char c: text.toCharArray()){
            String replacement = TRANSLITERATION_MAP.get(c);
            if(replacement != null){
                result.append(replacement);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Генерация уникального slug (добавление суффикса при конфликте)
     *
     * @param baseSlug базовый slug
     * @param counter счетчик для уникальности
     * @return уникальный slug
     */
    public String makeUnique(String baseSlug, int counter) {
        if (counter <= 1) {
            return baseSlug;
        }
        return baseSlug + "-" + counter;
    }
}
