package ru.rfsnab.integrationservice.service.ftk;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import ru.rfsnab.integrationservice.model.ftk.FtkProduct;
import ru.rfsnab.integrationservice.service.ftk.FtkXmlParser.ClassifierData;
import ru.rfsnab.integrationservice.service.ftk.FtkXmlParser.OfferData;
import ru.rfsnab.integrationservice.service.ftk.FtkXmlParser.ProductData;
import ru.rfsnab.integrationservice.service.ftk.FtkXmlParser.RestData;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты FtkXmlParser.
 * Тесты на реальных файлах пропускаются если E:\Fakel\ недоступен.
 * Unit-тесты на синтетическом XML всегда выполняются.
 */
@DisplayName("FtkXmlParser")
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
    // Unit-тесты на синтетическом XML (всегда работают)
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Unit: parseClassifier")
    class ParseClassifierUnitTests {

        @Test
        @DisplayName("парсит группы, свойства и единицы измерения")
        void shouldParseClassifierFromSyntheticXml() throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <КоммерческаяИнформация>
                      <Классификатор>
                        <Группы>
                          <Группа>
                            <Ид>UUID-ROOT</Ид>
                            <Наименование>Спецодежда</Наименование>
                            <Группы>
                              <Группа>
                                <Ид>UUID-CHILD</Ид>
                                <Наименование>Летняя</Наименование>
                              </Группа>
                            </Группы>
                          </Группа>
                        </Группы>
                        <Свойства>
                          <Свойство>
                            <Ид>PROP-001</Ид>
                            <Наименование>Размер</Наименование>
                            <ВариантыЗначений>
                              <Справочник>
                                <ИдЗначения>VAL-S</ИдЗначения>
                                <Значение>S</Значение>
                              </Справочник>
                            </ВариантыЗначений>
                          </Свойство>
                        </Свойства>
                        <ЕдиницыИзмерения>
                          <ЕдиницаИзмерения>
                            <Код>796</Код>
                            <НаименованиеПолное>штука</НаименованиеПолное>
                          </ЕдиницаИзмерения>
                          <ЕдиницаИзмерения>
                            <Код>715</Код>
                            <НаименованиеПолное>Пара (2 шт.)</НаименованиеПолное>
                          </ЕдиницаИзмерения>
                        </ЕдиницыИзмерения>
                      </Классификатор>
                    </КоммерческаяИнформация>
                    """;

            ClassifierData data = parser.parseClassifier(toStream(xml));

            assertThat(data.groupPaths()).containsKey("UUID-ROOT").containsKey("UUID-CHILD");
            assertThat(data.groupPaths().get("UUID-ROOT")).isEqualTo("Спецодежда");
            assertThat(data.groupPaths().get("UUID-CHILD")).isEqualTo("Спецодежда > Летняя");
            assertThat(data.groupParents().get("UUID-CHILD")).isEqualTo("UUID-ROOT");
            assertThat(data.groupParents().get("UUID-ROOT")).isNull();
            assertThat(data.propertyDefs()).containsKey("PROP-001");
            assertThat(data.propertyDefs().get("PROP-001").name()).isEqualTo("Размер");
            assertThat(data.propertyDefs().get("PROP-001").values()).containsEntry("VAL-S", "S");
            assertThat(data.unitsOfMeasure()).containsEntry("796", "штука");
            // уточняющий хвост "(2 шт.)" вырезается: "Пара (2 шт.)" → "Пара"
            assertThat(data.unitsOfMeasure()).containsEntry("715", "Пара");
        }
    }

    @Nested
    @DisplayName("Unit: parseProducts")
    class ParseProductsUnitTests {

        @Test
        @DisplayName("парсит все картинки товара (imagePaths), пометку удаления и свойства")
        void shouldParseMultipleImagePathsAndDeletionMark() throws Exception {
            Map<String, String> groupParents = new HashMap<>();
            groupParents.put("GRP-1", null);
            ClassifierData classifier = new ClassifierData(
                    Map.of("GRP-1", "Спецодежда"),
                    groupParents,
                    Map.of("PROP-001", new FtkXmlParser.PropertyDef("Размер",
                            Map.of("VAL-XL", "XL"))),
                    Map.of("796", "шт")
            );

            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <КоммерческаяИнформация>
                      <Каталог>
                        <Товары>
                          <Товар>
                            <Ид>PROD-UUID-1</Ид>
                            <Артикул>12345</Артикул>
                            <Наименование>Куртка рабочая</Наименование>
                            <Описание>Описание куртки</Описание>
                            <Картинка>import_files/ab/img1.jpg</Картинка>
                            <Картинка>import_files/cd/img2.jpg</Картинка>
                            <БазоваяЕдиница Код="796"/>
                            <Группы><Ид>GRP-1</Ид></Группы>
                            <ЗначенияСвойств>
                              <ЗначениеСвойства>
                                <Ид>PROP-001</Ид>
                                <Значение>VAL-XL</Значение>
                              </ЗначениеСвойства>
                            </ЗначенияСвойств>
                          </Товар>
                          <Товар>
                            <Ид>PROD-UUID-2</Ид>
                            <Артикул>99999</Артикул>
                            <Наименование>Удалённый товар</Наименование>
                            <ПометкаУдаления>true</ПометкаУдаления>
                          </Товар>
                        </Товары>
                      </Каталог>
                    </КоммерческаяИнформация>
                    """;

            Map<String, ProductData> products = parser.parseProducts(toStream(xml), classifier);

            assertThat(products).hasSize(2);

            ProductData prod1 = products.get("PROD-UUID-1");
            assertThat(prod1.imagePaths()).containsExactly(
                    "import_files/ab/img1.jpg", "import_files/cd/img2.jpg");
            assertThat(prod1.unitCode()).isEqualTo("796");
            assertThat(prod1.groupUuid()).isEqualTo("GRP-1");
            assertThat(prod1.properties()).containsEntry("Размер", "XL");
            assertThat(prod1.deletionMark()).isFalse();

            ProductData prod2 = products.get("PROD-UUID-2");
            assertThat(prod2.deletionMark()).isTrue();
        }
    }

    @Nested
    @DisplayName("Unit: parseOffers")
    class ParseOffersUnitTests {

        @Test
        @DisplayName("парсит barcode и countryOfOrigin из ЗначенияСвойств")
        void shouldParseBarcodeAndCountryFromOffers() throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <КоммерческаяИнформация>
                      <ПакетПредложений>
                        <Предложения>
                          <Предложение>
                            <Ид>UUID-PROD#UUID-VAR</Ид>
                            <ЗначенияСвойств>
                              <ЗначениеСвойства>
                                <Ид>FK_CML2_ARTICLE</Ид>
                                <Значение>87490974.001</Значение>
                              </ЗначениеСвойства>
                              <ЗначениеСвойства>
                                <Ид>фк_Штрихкод</Ид>
                                <Значение>4607032891234</Значение>
                              </ЗначениеСвойства>
                              <ЗначениеСвойства>
                                <Ид>фк_СтранаПроизводства</Ид>
                                <Значение>Россия</Значение>
                              </ЗначениеСвойства>
                            </ЗначенияСвойств>
                            <ХарактеристикиТовара>
                              <ХарактеристикаТовара>
                                <Наименование>Размер</Наименование>
                                <Значение>44-46</Значение>
                              </ХарактеристикаТовара>
                            </ХарактеристикиТовара>
                          </Предложение>
                        </Предложения>
                      </ПакетПредложений>
                    </КоммерческаяИнформация>
                    """;

            Map<String, OfferData> offers = parser.parseOffers(toStream(xml));

            assertThat(offers).containsKey("UUID-PROD#UUID-VAR");
            OfferData offer = offers.get("UUID-PROD#UUID-VAR");
            assertThat(offer.productUuid()).isEqualTo("UUID-PROD");
            assertThat(offer.article()).isEqualTo("87490974.001");
            assertThat(offer.barcode()).isEqualTo("4607032891234");
            assertThat(offer.countryOfOrigin()).isEqualTo("Россия");
            assertThat(offer.attributes()).containsEntry("Размер", "44-46");
        }
    }

    @Nested
    @DisplayName("Unit: parseRests")
    class ParseRestsUnitTests {

        @Test
        @DisplayName("парсит количество, barcode и countryOfOrigin из остатков")
        void shouldParseRestsWithBarcodeAndCountry() throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <КоммерческаяИнформация>
                      <ПакетПредложений>
                        <Предложения>
                          <Предложение>
                            <Ид>UUID-PROD#UUID-VAR</Ид>
                            <Остатки>
                              <Остаток>
                                <Количество>15</Количество>
                              </Остаток>
                            </Остатки>
                            <ЗначенияРеквизитов>
                              <ЗначениеРеквизита>
                                <Наименование>фк_Штрихкод</Наименование>
                                <Значение>4600000000001</Значение>
                              </ЗначениеРеквизита>
                              <ЗначениеРеквизита>
                                <Наименование>фк_СтранаПроизводства</Наименование>
                                <Значение>Китай</Значение>
                              </ЗначениеРеквизита>
                            </ЗначенияРеквизитов>
                          </Предложение>
                        </Предложения>
                      </ПакетПредложений>
                    </КоммерческаяИнформация>
                    """;

            Map<String, RestData> rests = parser.parseRests(toStream(xml));

            assertThat(rests).containsKey("UUID-PROD#UUID-VAR");
            RestData rest = rests.get("UUID-PROD#UUID-VAR");
            assertThat(rest.quantity()).isEqualTo(15);
            assertThat(rest.barcode()).isEqualTo("4600000000001");
            assertThat(rest.countryOfOrigin()).isEqualTo("Китай");
        }
    }

    @Nested
    @DisplayName("Unit: assemble")
    class AssembleUnitTests {

        @Test
        @DisplayName("собирает FtkProduct: barcode/country из rests, imagePaths, unitOfMeasure")
        void shouldAssembleWithAllNewFields() {
            Map<String, String> groupParents1 = new HashMap<>();
            groupParents1.put("GRP-1", null);
            ClassifierData classifier = new ClassifierData(
                    Map.of("GRP-1", "Спецодежда"),
                    groupParents1,
                    Map.of(),
                    Map.of("796", "шт")
            );

            Map<String, ProductData> products = Map.of(
                    "PROD-1", new ProductData("PROD-1", "12345", "Куртка", "Описание",
                            List.of("img1.jpg", "img2.jpg"), "GRP-1", "796",
                            Map.of("Цвет", "синий"), false)
            );

            Map<String, OfferData> offers = Map.of(
                    "PROD-1#VAR-1", new OfferData("PROD-1#VAR-1", "PROD-1", "12345.001",
                            20, Map.of("Размер", "S"), "4600000000001", "Россия")
            );

            Map<String, BigDecimal> prices = Map.of(
                    "PROD-1#VAR-1", new BigDecimal("5000")
            );

            Map<String, RestData> rests = Map.of(
                    "PROD-1#VAR-1", new RestData(10, "4607000000002", "Китай")
            );

            List<FtkProduct> result = parser.assemble(products, offers, prices, rests, classifier, 1);

            assertThat(result).hasSize(1);
            FtkProduct p = result.get(0);
            assertThat(p.getImagePaths()).containsExactly("img1.jpg", "img2.jpg");
            assertThat(p.getUnitOfMeasure()).isEqualTo("шт");
            assertThat(p.getProperties()).containsEntry("Цвет", "синий");
            assertThat(p.getVariants()).hasSize(1);

            FtkProduct.FtkVariant v = p.getVariants().get(0);
            assertThat(v.getBarcode()).isEqualTo("4607000000002");       // rests имеет приоритет
            assertThat(v.getCountryOfOrigin()).isEqualTo("Китай");         // rests имеет приоритет
            assertThat(v.getPrice()).isEqualByComparingTo("5000");
            assertThat(v.getStockQuantity()).isEqualTo(10);
            assertThat(v.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("barcode из offers если в rests не задан")
        void shouldFallbackBarcodeToOffers() {
            ClassifierData classifier = new ClassifierData(
                    Map.of("GRP-1", "Спецодежда"), Map.of(), Map.of(), Map.of());

            Map<String, ProductData> products = Map.of(
                    "PROD-1", new ProductData("PROD-1", "111", "Товар", null,
                            List.of(), "GRP-1", null, Map.of(), false)
            );
            Map<String, OfferData> offers = Map.of(
                    "PROD-1", new OfferData("PROD-1", "PROD-1", "111", null,
                            Map.of(), "BARCODE-FROM-OFFER", "Беларусь")
            );
            Map<String, BigDecimal> prices = Map.of("PROD-1", new BigDecimal("100"));
            Map<String, RestData> rests    = Map.of("PROD-1", new RestData(5, null, null));

            List<FtkProduct> result = parser.assemble(products, offers, prices, rests, classifier, 1);

            assertThat(result).hasSize(1);
            FtkProduct.FtkVariant v = result.get(0).getVariants().get(0);
            assertThat(v.getBarcode()).isEqualTo("BARCODE-FROM-OFFER");
            assertThat(v.getCountryOfOrigin()).isEqualTo("Беларусь");
        }

        @Test
        @DisplayName("товар с ПометкаУдаления=true имеет deleted=true в вариантах")
        void shouldMarkDeletedVariants() {
            ClassifierData classifier = new ClassifierData(
                    Map.of("GRP-1", "Спецодежда"), Map.of(), Map.of(), Map.of());

            Map<String, ProductData> products = Map.of(
                    "PROD-DEL", new ProductData("PROD-DEL", "222", "Удалённый", null,
                            List.of(), "GRP-1", null, Map.of(), true)
            );
            Map<String, OfferData> offers = Map.of(
                    "PROD-DEL", new OfferData("PROD-DEL", "PROD-DEL", "222", null,
                            Map.of(), null, null)
            );
            Map<String, BigDecimal> prices = Map.of("PROD-DEL", new BigDecimal("50"));
            Map<String, RestData> rests    = Map.of("PROD-DEL", new RestData(0, null, null));

            List<FtkProduct> result = parser.assemble(products, offers, prices, rests, classifier, 1);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getVariants().get(0).isDeleted()).isTrue();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Тесты на реальных файлах ФТК
    // ──────────────────────────────────────────────────────────────

    @Test
    @EnabledIf("filesAvailable")
    @DisplayName("parseClassifier: загружает группы, свойства и единицы измерения")
    void shouldParseClassifierGroups() throws Exception {
        try (InputStream is = new FileInputStream(rootImportFile.toFile())) {
            ClassifierData data = parser.parseClassifier(is);

            assertThat(data.groupPaths()).isNotEmpty();
            data.groupPaths().forEach((uuid, path) -> {
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
            ClassifierData data = parser.parseClassifier(is);
            assertThat(data.groupPaths().values()).anyMatch(path -> path.contains("Спецодежда"));
        }
    }

    @Test
    @EnabledIf("filesAvailable")
    @DisplayName("parseProducts: загружает товары с imagePaths")
    void shouldParseProductsWithImagePaths() throws Exception {
        ClassifierData classifier;
        try (InputStream is = new FileInputStream(rootImportFile.toFile())) {
            classifier = parser.parseClassifier(is);
        }
        try (InputStream is = new FileInputStream(goodsImportFile.toFile())) {
            Map<String, ProductData> products = parser.parseProducts(is, classifier);

            assertThat(products).isNotEmpty();
            products.values().forEach(p -> {
                assertThat(p.productUuid()).isNotBlank();
                assertThat(p.name()).isNotBlank();
                assertThat(p.imagePaths()).isNotNull();
            });
            long withImages = products.values().stream()
                    .filter(p -> !p.imagePaths().isEmpty())
                    .count();
            assertThat(withImages).isGreaterThan(0);
        }
    }

    @Test
    @EnabledIf("filesAvailable")
    @DisplayName("parseOffers: загружает офферы с артикулами FK_CML2_ARTICLE")
    void shouldParseOffers() throws Exception {
        try (InputStream is = new FileInputStream(offersFile.toFile())) {
            Map<String, OfferData> offers = parser.parseOffers(is);
            assertThat(offers).isNotEmpty();
            long withArticle = offers.values().stream().filter(o -> o.article() != null).count();
            assertThat(withArticle).isGreaterThan(0);
        }
    }

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

    @Test
    @EnabledIf("filesAvailable")
    @DisplayName("parseRests: загружает остатки, значения >= 0")
    void shouldParseRests() throws Exception {
        try (InputStream is = new FileInputStream(restsFile.toFile())) {
            Map<String, RestData> rests = parser.parseRests(is);
            assertThat(rests).isNotEmpty();
            rests.values().forEach(r -> assertThat(r.quantity()).isGreaterThanOrEqualTo(0));
        }
    }

    @Test
    @EnabledIf("filesAvailable")
    @DisplayName("assemble: первые 100 товаров имеют imagePaths и варианты с артикулами")
    void shouldAssembleFirst100Products() throws Exception {
        ClassifierData classifier;
        try (InputStream is = new FileInputStream(rootImportFile.toFile())) {
            classifier = parser.parseClassifier(is);
        }
        Map<String, ProductData> products;
        try (InputStream is = new FileInputStream(goodsImportFile.toFile())) {
            products = parser.parseProducts(is, classifier);
        }
        Map<String, OfferData> offers;
        try (InputStream is = new FileInputStream(offersFile.toFile())) {
            offers = parser.parseOffers(is);
        }
        Map<String, BigDecimal> prices;
        try (InputStream is = new FileInputStream(pricesFile.toFile())) {
            prices = parser.parsePrices(is);
        }
        Map<String, RestData> rests;
        try (InputStream is = new FileInputStream(restsFile.toFile())) {
            rests = parser.parseRests(is);
        }

        List<FtkProduct> assembled = parser.assemble(products, offers, prices, rests, classifier, 1);

        assertThat(assembled).isNotEmpty();
        List<FtkProduct> first100 = assembled.subList(0, Math.min(100, assembled.size()));

        first100.forEach(p -> {
            assertThat(p.getArticle()).isNotBlank();
            assertThat(p.getName()).isNotBlank();
            assertThat(p.getVariants()).isNotEmpty();
            assertThat(p.getImagePaths()).isNotNull();
            p.getVariants().forEach(v -> assertThat(v.getArticle()).isNotBlank());
        });

        long withPrice = first100.stream()
                .flatMap(p -> p.getVariants().stream())
                .filter(v -> v.getPrice() != null)
                .count();
        assertThat(withPrice).isGreaterThan(0);
    }

    // ──────────────────────────────────────────────────────────────
    // Вспомогательный метод
    // ──────────────────────────────────────────────────────────────

    private static InputStream toStream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
