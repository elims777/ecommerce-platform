package ru.rfsnab.integrationservice.service.ftk;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import ru.rfsnab.integrationservice.model.ftk.FtkProduct;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты FtkXmlParser на реальных файлах ФТК из E:\Fakel\.
 * Тесты пропускаются если файлы недоступны — CI не требует FTP-доступа.
 */
@DisplayName("FtkXmlParser (реальные файлы)")
class FtkXmlParserTest {

    private static final Path ROOT_DIR  = Path.of("E:\\Fakel\\webdata\\000000003");
    private static final Path GOODS_DIR = ROOT_DIR.resolve("goods\\1");

    private static Path rootImportFile;
    private static Path goodsImportFile;
    private static Path offersFile;
    private static Path pricesFile;
    private static Path restsFile;

    private static boolean filesAvailable = false;

    @BeforeAll
    static void findFiles() throws Exception {
        if (!Files.isDirectory(GOODS_DIR)) return;

        rootImportFile  = findByPrefix(ROOT_DIR,  "import___");
        goodsImportFile = findByPrefix(GOODS_DIR, "import___");
        offersFile      = findByPrefix(GOODS_DIR, "offers___");
        pricesFile      = findByPrefix(GOODS_DIR, "prices___");
        restsFile       = findByPrefix(GOODS_DIR, "rests___");

        filesAvailable = rootImportFile != null && goodsImportFile != null
                && offersFile != null && pricesFile != null && restsFile != null;
    }

    private static Path findByPrefix(Path dir, String prefix) throws Exception {
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith(prefix)
                                 && p.getFileName().toString().endsWith(".xml"))
                    .findFirst()
                    .orElse(null);
        }
    }

    static boolean filesAvailable() { return filesAvailable; }

    private final FtkXmlParser parser = new FtkXmlParser();

    // ──────────────────────────────────────────────────────────────
    // Классификатор
    // ──────────────────────────────────────────────────────────────

    @Test
    @EnabledIf("filesAvailable")
    @DisplayName("parseClassifier: загружает группы с непустыми путями")
    void shouldParseClassifierGroups() throws Exception {
        try (InputStream is = new FileInputStream(rootImportFile.toFile())) {
            Map<String, String> groups = parser.parseClassifier(is);

            assertThat(groups).isNotEmpty();
            groups.forEach((uuid, path) -> {
                assertThat(uuid).isNotBlank();
                assertThat(path).isNotBlank().doesNotStartWith(" > ").doesNotEndWith(" > ");
            });
        }
    }

    @Test
    @EnabledIf("filesAvailable")
    @DisplayName("parseClassifier: содержит группу 'Спецодежда'")
    void shouldContainSpetsodezhda() throws Exception {
        try (InputStream is = new FileInputStream(rootImportFile.toFile())) {
            Map<String, String> groups = parser.parseClassifier(is);
            assertThat(groups.values()).anyMatch(path -> path.contains("Спецодежда"));
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Товары
    // ──────────────────────────────────────────────────────────────

    @Test
    @EnabledIf("filesAvailable")
    @DisplayName("parseProducts: загружает товары с артикулом и названием")
    void shouldParseProducts() throws Exception {
        Map<String, String> groups;
        try (InputStream is = new FileInputStream(rootImportFile.toFile())) {
            groups = parser.parseClassifier(is);
        }
        try (InputStream is = new FileInputStream(goodsImportFile.toFile())) {
            Map<String, FtkXmlParser.ProductData> products = parser.parseProducts(is, groups);

            assertThat(products).isNotEmpty();
            products.values().forEach(p -> {
                assertThat(p.productUuid()).isNotBlank();
                assertThat(p.name()).isNotBlank();
            });
        }
    }

    @Test
    @EnabledIf("filesAvailable")
    @DisplayName("parseProducts: у товара есть путь картинки")
    void someProductsShouldHaveImagePath() throws Exception {
        Map<String, String> groups;
        try (InputStream is = new FileInputStream(rootImportFile.toFile())) {
            groups = parser.parseClassifier(is);
        }
        try (InputStream is = new FileInputStream(goodsImportFile.toFile())) {
            Map<String, FtkXmlParser.ProductData> products = parser.parseProducts(is, groups);
            assertThat(products.values()).anyMatch(p -> p.imagePath() != null && p.imagePath().startsWith("import_files/"));
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Офферы
    // ──────────────────────────────────────────────────────────────

    @Test
    @EnabledIf("filesAvailable")
    @DisplayName("parseOffers: загружает офферы с артикулами FK_CML2_ARTICLE")
    void shouldParseOffers() throws Exception {
        try (InputStream is = new FileInputStream(offersFile.toFile())) {
            Map<String, FtkXmlParser.OfferData> offers = parser.parseOffers(is);

            assertThat(offers).isNotEmpty();
            long withArticle = offers.values().stream().filter(o -> o.article() != null).count();
            assertThat(withArticle).isGreaterThan(0);
        }
    }

    @Test
    @EnabledIf("filesAvailable")
    @DisplayName("parseOffers: составной Ид корректно разбирается")
    void compositeIdShouldBeParsedCorrectly() throws Exception {
        try (InputStream is = new FileInputStream(offersFile.toFile())) {
            Map<String, FtkXmlParser.OfferData> offers = parser.parseOffers(is);

            // Найти оффер с #
            offers.entrySet().stream()
                    .filter(e -> e.getKey().contains("#"))
                    .limit(1)
                    .forEach(e -> {
                        String[] parts = e.getKey().split("#");
                        assertThat(parts).hasSize(2);
                        assertThat(e.getValue().productUuid()).isEqualTo(parts[0]);
                    });
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Цены (StAX — 125 МБ)
    // ──────────────────────────────────────────────────────────────

    @Test
    @EnabledIf("filesAvailable")
    @DisplayName("parsePrices: загружает розничные цены, все > 0")
    void shouldParseRetailPrices() throws Exception {
        try (InputStream is = new FileInputStream(pricesFile.toFile())) {
            Map<String, BigDecimal> prices = parser.parsePrices(is);

            assertThat(prices).isNotEmpty();
            prices.values().forEach(p -> assertThat(p).isGreaterThan(BigDecimal.ZERO));
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Остатки
    // ──────────────────────────────────────────────────────────────

    @Test
    @EnabledIf("filesAvailable")
    @DisplayName("parseRests: загружает остатки, значения >= 0")
    void shouldParseRests() throws Exception {
        try (InputStream is = new FileInputStream(restsFile.toFile())) {
            Map<String, Integer> rests = parser.parseRests(is);

            assertThat(rests).isNotEmpty();
            rests.values().forEach(qty -> assertThat(qty).isGreaterThanOrEqualTo(0));
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Полная сборка — первые 100 товаров
    // ──────────────────────────────────────────────────────────────

    @Test
    @EnabledIf("filesAvailable")
    @DisplayName("assemble: первые 100 товаров имеют варианты с артикулами")
    void shouldAssembleFirst100Products() throws Exception {
        Map<String, String> groups;
        try (InputStream is = new FileInputStream(rootImportFile.toFile())) {
            groups = parser.parseClassifier(is);
        }
        Map<String, FtkXmlParser.ProductData> products;
        try (InputStream is = new FileInputStream(goodsImportFile.toFile())) {
            products = parser.parseProducts(is, groups);
        }
        Map<String, FtkXmlParser.OfferData> offers;
        try (InputStream is = new FileInputStream(offersFile.toFile())) {
            offers = parser.parseOffers(is);
        }
        Map<String, BigDecimal> prices;
        try (InputStream is = new FileInputStream(pricesFile.toFile())) {
            prices = parser.parsePrices(is);
        }
        Map<String, Integer> rests;
        try (InputStream is = new FileInputStream(restsFile.toFile())) {
            rests = parser.parseRests(is);
        }

        List<FtkProduct> assembled = parser.assemble(products, offers, prices, rests);

        assertThat(assembled).isNotEmpty();
        List<FtkProduct> first100 = assembled.subList(0, Math.min(100, assembled.size()));

        first100.forEach(p -> {
            assertThat(p.getArticle()).isNotBlank();
            assertThat(p.getName()).isNotBlank();
            assertThat(p.getVariants()).isNotEmpty();
            p.getVariants().forEach(v -> assertThat(v.getArticle()).isNotBlank());
        });

        // Хотя бы часть товаров имеет цену
        long withPrice = first100.stream()
                .flatMap(p -> p.getVariants().stream())
                .filter(v -> v.getPrice() != null)
                .count();
        assertThat(withPrice).isGreaterThan(0);

        // Хотя бы часть товаров имеет остатки > 0
        long withStock = first100.stream()
                .flatMap(p -> p.getVariants().stream())
                .filter(v -> v.getStockQuantity() > 0)
                .count();
        assertThat(withStock).isGreaterThan(0);
    }

    @Test
    @EnabledIf("filesAvailable")
    @DisplayName("assemble: товары с вариантами имеют составной offerUuid")
    void productWithVariantsShouldHaveCompositeOfferUuid() throws Exception {
        Map<String, String> groups;
        try (InputStream is = new FileInputStream(rootImportFile.toFile())) {
            groups = parser.parseClassifier(is);
        }
        Map<String, FtkXmlParser.ProductData> products;
        try (InputStream is = new FileInputStream(goodsImportFile.toFile())) {
            products = parser.parseProducts(is, groups);
        }
        Map<String, FtkXmlParser.OfferData> offers;
        try (InputStream is = new FileInputStream(offersFile.toFile())) {
            offers = parser.parseOffers(is);
        }
        Map<String, BigDecimal> prices;
        try (InputStream is = new FileInputStream(pricesFile.toFile())) {
            prices = parser.parsePrices(is);
        }
        Map<String, Integer> rests;
        try (InputStream is = new FileInputStream(restsFile.toFile())) {
            rests = parser.parseRests(is);
        }

        List<FtkProduct> assembled = parser.assemble(products, offers, prices, rests);

        // Найти товар с несколькими вариантами — у него offerUuid должен содержать #
        assembled.stream()
                .filter(p -> p.getVariants().size() > 1)
                .limit(1)
                .forEach(p -> {
                    boolean hasComposite = p.getVariants().stream()
                            .anyMatch(v -> v.getOfferUuid() != null && v.getOfferUuid().contains("#"));
                    assertThat(hasComposite).isTrue();
                });
    }
}
