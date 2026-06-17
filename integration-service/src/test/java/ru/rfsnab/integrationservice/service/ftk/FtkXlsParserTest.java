package ru.rfsnab.integrationservice.service.ftk;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.rfsnab.integrationservice.model.ftk.FtkProduct;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты FtkXlsParser.
 * XLS строится программно через POI — нет зависимости от файла на диске.
 *
 * Структура реального файла ФТК (колонки):
 *   A(0)=наименование/группа, D(3)=артикул, E(4)=цена, I(8)=описание, M(12)=изображение
 */
@DisplayName("FtkXlsParser")
class FtkXlsParserTest {

    private FtkXlsParser parser;

    @BeforeEach
    void setUp() {
        parser = new FtkXlsParser();
    }

    // ===== Вспомогательный билдер XLS =====

    private static final class XlsBuilder {
        private final HSSFWorkbook workbook = new HSSFWorkbook();
        private final Sheet sheet = workbook.createSheet("Лист1");
        private int rowIdx = 0;

        XlsBuilder() {
            for (int i = 0; i < 7; i++) {
                sheet.createRow(rowIdx++);
            }
        }

        XlsBuilder category(String name) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(name);
            return this;
        }

        XlsBuilder product(String article, String name, double price,
                           String printName, String description, String material, String imagePath) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(name);
            row.createCell(3).setCellValue(article);
            row.createCell(4).setCellValue(price);
            if (printName != null)   row.createCell(6).setCellValue(printName);
            if (description != null) row.createCell(8).setCellValue(description);
            if (material != null)    row.createCell(9).setCellValue(material);
            if (imagePath != null)   row.createCell(12).setCellValue(imagePath);
            return this;
        }

        XlsBuilder variant(String article, String characteristic, double price) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(characteristic);
            row.createCell(3).setCellValue(article);
            row.createCell(4).setCellValue(price);
            return this;
        }

        ByteArrayInputStream toInputStream() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.close();
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    // ===== Тесты =====

    @Nested
    @DisplayName("Категории")
    class CategoryTests {

        @Test
        @DisplayName("товары парсятся после строки-категории")
        void shouldAssignRootCategory() throws IOException {
            var xls = new XlsBuilder()
                    .category("01 Спецодежда")
                    .product("87490974", "Костюм летний", 9267, null, null, null, null)
                    .toInputStream();

            List<FtkProduct> result = parser.parse(xls);

            assertThat(result).hasSize(1);
            // XLS-парсер не знает UUID групп — groupUuid всегда null
            assertThat(result.get(0).getGroupUuid()).isNull();
        }

        @Test
        @DisplayName("строки-категории не превращаются в товары")
        void shouldNotCreateProductsFromCategoryRows() throws IOException {
            var xls = new XlsBuilder()
                    .category("01 Спецодежда")
                    .category("01.01 Спецодежда летняя")
                    .category("01.01.1 Костюмы летние")
                    .product("87490974", "Костюм летний", 9267, null, null, null, null)
                    .toInputStream();

            List<FtkProduct> result = parser.parse(xls);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getArticle()).isEqualTo("87490974");
        }

        @Test
        @DisplayName("переход на соседнюю подкатегорию: оба товара парсятся")
        void shouldResetDeepCategoryOnSiblingSwitch() throws IOException {
            var xls = new XlsBuilder()
                    .category("01 Спецодежда")
                    .category("01.01 Спецодежда летняя")
                    .category("01.01.1 Костюмы летние")
                    .product("111", "Товар 1", 1000, null, null, null, null)
                    .category("01.01.2 Куртки летние")
                    .product("222", "Товар 2", 2000, null, null, null, null)
                    .toInputStream();

            List<FtkProduct> result = parser.parse(xls);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getArticle()).isEqualTo("111");
            assertThat(result.get(1).getArticle()).isEqualTo("222");
        }

        @Test
        @DisplayName("переход на категорию 1-го уровня: оба товара парсятся")
        void shouldResetStackOnRootCategory() throws IOException {
            var xls = new XlsBuilder()
                    .category("01 Спецодежда")
                    .category("01.01 Спецодежда летняя")
                    .product("111", "Товар 1", 1000, null, null, null, null)
                    .category("02 СИЗ")
                    .product("222", "Товар 2", 2000, null, null, null, null)
                    .toInputStream();

            List<FtkProduct> result = parser.parse(xls);

            assertThat(result).hasSize(2);
            assertThat(result.get(1).getArticle()).isEqualTo("222");
        }
    }

    @Nested
    @DisplayName("Товары")
    class ProductTests {

        @Test
        @DisplayName("парсит основные поля товара")
        void shouldParseProductFields() throws IOException {
            var xls = new XlsBuilder()
                    .category("01 Спецодежда")
                    .product("87490974", "Костюм летний",
                            9267,
                            "Костюм ProfLineBase-1",
                            "Описание костюма",
                            "Ткань Твил 65% хлопок",
                            "ftp://fakelftp:poiPOI098@31.44.91.154/GoodsPictures/img.jpg")
                    .toInputStream();

            List<FtkProduct> result = parser.parse(xls);

            assertThat(result).hasSize(1);
            FtkProduct p = result.get(0);
            assertThat(p.getArticle()).isEqualTo("87490974");
            assertThat(p.getName()).isEqualTo("Костюм летний");
            assertThat(p.getDescription()).isEqualTo("Описание костюма");
            assertThat(p.getImagePaths()).containsExactly("ftp://fakelftp:poiPOI098@31.44.91.154/GoodsPictures/img.jpg");
            // товар без вариантов → default-вариант с ценой
            assertThat(p.getVariants()).hasSize(1);
            assertThat(p.getVariants().get(0).getPrice()).isEqualByComparingTo("9267");
        }

        @Test
        @DisplayName("пустой файл возвращает пустой список")
        void shouldReturnEmptyListForEmptyFile() throws IOException {
            var xls = new XlsBuilder().toInputStream();

            List<FtkProduct> result = parser.parse(xls);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("несколько товаров в одной категории")
        void shouldParseMultipleProductsInCategory() throws IOException {
            var xls = new XlsBuilder()
                    .category("01 Спецодежда")
                    .product("111", "Товар 1", 1000, null, null, null, null)
                    .product("222", "Товар 2", 2000, null, null, null, null)
                    .product("333", "Товар 3", 3000, null, null, null, null)
                    .toInputStream();

            List<FtkProduct> result = parser.parse(xls);

            assertThat(result).hasSize(3);
            assertThat(result).extracting(FtkProduct::getArticle)
                    .containsExactly("111", "222", "333");
        }
    }

    @Nested
    @DisplayName("Варианты")
    class VariantTests {

        @Test
        @DisplayName("парсит варианты с характеристикой 'Размер; Рост'")
        void shouldParseVariantsWithSizeAndHeight() throws IOException {
            var xls = new XlsBuilder()
                    .category("01 Спецодежда")
                    .product("87486380", "Жилет сигнальный", 4660, null, null, null, null)
                    .variant("87486380.001", "44-46; 170-176", 4660)
                    .variant("87486380.002", "44-46; 182-188", 4660)
                    .toInputStream();

            List<FtkProduct> result = parser.parse(xls);

            assertThat(result).hasSize(1);
            FtkProduct p = result.get(0);
            assertThat(p.getVariants()).hasSize(2);

            FtkProduct.FtkVariant v1 = p.getVariants().get(0);
            assertThat(v1.getArticle()).isEqualTo("87486380.001");
            assertThat(v1.getAttributes()).containsEntry("Размер", "44-46");
            assertThat(v1.getAttributes()).containsEntry("Рост", "170-176");
            assertThat(v1.getPrice()).isEqualByComparingTo("4660");
        }

        @Test
        @DisplayName("вариант с нестандартной характеристикой → ключ 'Характеристика'")
        void shouldParseNonStandardCharacteristic() throws IOException {
            var xls = new XlsBuilder()
                    .category("01 Спецодежда")
                    .product("12345", "Товар", 1000, null, null, null, null)
                    .variant("12345.001", "Синий", 1000)
                    .toInputStream();

            List<FtkProduct> result = parser.parse(xls);

            FtkProduct.FtkVariant v = result.get(0).getVariants().get(0);
            assertThat(v.getAttributes()).containsEntry("Характеристика", "Синий");
        }

        @Test
        @DisplayName("товар без вариантов получает default-вариант")
        void shouldCreateDefaultVariantWhenNoVariantRows() throws IOException {
            var xls = new XlsBuilder()
                    .category("01 Спецодежда")
                    .product("99999", "Товар без вариантов", 500, null, null, null, null)
                    .toInputStream();

            List<FtkProduct> result = parser.parse(xls);

            assertThat(result).hasSize(1);
            FtkProduct p = result.get(0);
            assertThat(p.getVariants()).hasSize(1);
            assertThat(p.getVariants().get(0).getArticle()).isEqualTo("99999");
            assertThat(p.getVariants().get(0).getPrice()).isEqualByComparingTo("500");
        }

        @Test
        @DisplayName("варианты не попадают в следующий товар")
        void shouldNotLeakVariantsToNextProduct() throws IOException {
            var xls = new XlsBuilder()
                    .category("01 Спецодежда")
                    .product("111", "Товар 1", 1000, null, null, null, null)
                    .variant("111.001", "44-46; 170-176", 1000)
                    .product("222", "Товар 2", 2000, null, null, null, null)
                    .toInputStream();

            List<FtkProduct> result = parser.parse(xls);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getVariants()).hasSize(1);
            // Товар 2 без вариантов → один default-вариант
            assertThat(result.get(1).getVariants()).hasSize(1);
            assertThat(result.get(1).getVariants().get(0).getArticle()).isEqualTo("222");
        }
    }
}
