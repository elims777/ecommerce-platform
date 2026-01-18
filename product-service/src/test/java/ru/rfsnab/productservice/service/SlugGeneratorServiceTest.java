package ru.rfsnab.productservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SlugGeneratorService Unit Tests")
class SlugGeneratorServiceTest {

    private SlugGeneratorService slugGenerator;

    @BeforeEach
    void setUp() {
        slugGenerator = new SlugGeneratorService();
    }

    @Nested
    @DisplayName("generateSlug()")
    class GenerateSlugTests {

        @Test
        @DisplayName("транслитерирует кириллицу в латиницу")
        void generateSlug_Cyrillic_TransliteratesToLatin() {
            // When
            String result = slugGenerator.generateSlug("Огнетушитель");

            // Then
            assertThat(result).isEqualTo("ognetushitel");
        }

        @Test
        @DisplayName("обрабатывает сложное название с цифрами и спецсимволами")
        void generateSlug_ComplexName_GeneratesCleanSlug() {
            // When
            String result = slugGenerator.generateSlug("Огнетушитель ОП-4(з)-АВСЕ-01");

            // Then
            assertThat(result).isEqualTo("ognetushitel-op-4-z-avse-01");
        }

        @Test
        @DisplayName("приводит к нижнему регистру")
        void generateSlug_MixedCase_LowerCase() {
            // When
            String result = slugGenerator.generateSlug("TestProduct");

            // Then
            assertThat(result).isEqualTo("testproduct");
        }

        @Test
        @DisplayName("убирает множественные дефисы")
        void generateSlug_MultipleSpaces_SingleDash() {
            // When
            String result = slugGenerator.generateSlug("Товар   с   пробелами");

            // Then
            assertThat(result).isEqualTo("tovar-s-probelami");
        }

        @Test
        @DisplayName("убирает дефисы в начале и конце")
        void generateSlug_SpecialCharsAtEdges_TrimsCorrectly() {
            // When
            String result = slugGenerator.generateSlug("  -Товар-  ");

            // Then
            assertThat(result).isEqualTo("tovar");
        }

        @Test
        @DisplayName("выбрасывает исключение для null")
        void generateSlug_Null_ThrowsException() {
            // When & Then
            assertThatThrownBy(() -> slugGenerator.generateSlug(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("не может быть пустым");
        }

        @Test
        @DisplayName("выбрасывает исключение для пустой строки")
        void generateSlug_Empty_ThrowsException() {
            // When & Then
            assertThatThrownBy(() -> slugGenerator.generateSlug("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("обрабатывает латинский текст")
        void generateSlug_Latin_PreservesLatin() {
            // When
            String result = slugGenerator.generateSlug("Fire Extinguisher Model-5");

            // Then
            assertThat(result).isEqualTo("fire-extinguisher-model-5");
        }
    }

    @Nested
    @DisplayName("makeUnique()")
    class MakeUniqueTests {

        @Test
        @DisplayName("counter=1 → возвращает базовый slug без изменений")
        void makeUnique_CounterOne_ReturnsBaseSlug() {
            // When
            String result = slugGenerator.makeUnique("product", 1);

            // Then
            assertThat(result).isEqualTo("product");
        }

        @Test
        @DisplayName("counter>1 → добавляет суффикс")
        void makeUnique_CounterGreaterThanOne_AddsSuffix() {
            // When
            String result = slugGenerator.makeUnique("product", 3);

            // Then
            assertThat(result).isEqualTo("product-3");
        }
    }
}
