package ru.rfsnab.integrationservice.model.commerceml;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты JAXB-парсинга CommerceML XML.
 * Чистые тесты без Spring контекста — проверяют маппинг XML → Java объекты.
 */
@DisplayName("CommerceML JAXB Parsing")
class CommerceMLParsingTest {

    private static JAXBContext jaxbContext;

    @BeforeAll
    static void initJaxb() throws JAXBException {
        jaxbContext = JAXBContext.newInstance(CommerceInfo.class);
    }

    private CommerceInfo unmarshal(String resourcePath) throws JAXBException {
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        assertThat(is).as("Тестовый файл %s должен существовать", resourcePath).isNotNull();
        return (CommerceInfo) unmarshaller.unmarshal(is);
    }

    @Nested
    @DisplayName("import.xml — каталог товаров")
    class ImportXmlTests {

        @Test
        @DisplayName("парсит корневой элемент КоммерческаяИнформация")
        void shouldParseRootElement() throws JAXBException {
            CommerceInfo info = unmarshal("commerceml/import.xml");

            assertThat(info).isNotNull();
            assertThat(info.getSchemaVersion()).isEqualTo("2.10");
            assertThat(info.getFormationDate()).isNotBlank();
        }

        @Test
        @DisplayName("парсит классификатор с группами")
        void shouldParseClassifier() throws JAXBException {
            CommerceInfo info = unmarshal("commerceml/import.xml");

            Classifier classifier = info.getClassifier();
            assertThat(classifier).isNotNull();
            assertThat(classifier.getName()).isEqualTo("Классификатор товаров");
            assertThat(classifier.getGroups()).hasSize(1);

            // Рекурсивная вложенность: Средства защиты → Перчатки
            Group rootGroup = classifier.getGroups().getFirst();
            assertThat(rootGroup.getName()).isEqualTo("Средства защиты");
            assertThat(rootGroup.getSubGroups()).hasSize(1);
            assertThat(rootGroup.getSubGroups().getFirst().getName()).isEqualTo("Перчатки");
        }

        @Test
        @DisplayName("парсит каталог с товарами")
        void shouldParseCatalogProducts() throws JAXBException {
            CommerceInfo info = unmarshal("commerceml/import.xml");

            Catalog catalog = info.getCatalog();
            assertThat(catalog).isNotNull();
            assertThat(catalog.getProducts()).hasSize(2);
        }

        @Test
        @DisplayName("парсит все поля товара")
        void shouldParseProductFields() throws JAXBException {
            CommerceInfo info = unmarshal("commerceml/import.xml");

            CmlProduct product = info.getCatalog().getProducts().getFirst();
            assertThat(product.getId()).isEqualTo("ext-001");
            assertThat(product.getName()).isEqualTo("Перчатки нитриловые L");
            assertThat(product.getSku()).isEqualTo("ART-001");
            assertThat(product.getDescription()).isEqualTo("Перчатки нитриловые размер L, синие");
        }

        @Test
        @DisplayName("парсит базовую единицу измерения")
        void shouldParseBaseUnit() throws JAXBException {
            CommerceInfo info = unmarshal("commerceml/import.xml");

            BaseUnit unit = info.getCatalog().getProducts().getFirst().getBaseUnit();
            assertThat(unit).isNotNull();
            assertThat(unit.getValue()).isEqualTo("шт");
            assertThat(unit.getCode()).isEqualTo("796");
            assertThat(unit.getFullName()).isEqualTo("Штука");
            assertThat(unit.getInternationalAbbreviation()).isEqualTo("PCE");
        }

        @Test
        @DisplayName("парсит ссылки на группы товара")
        void shouldParseProductGroupIds() throws JAXBException {
            CommerceInfo info = unmarshal("commerceml/import.xml");

            CmlProduct product = info.getCatalog().getProducts().getFirst();
            assertThat(product.getGroupIds()).containsExactly("group-002");
        }

        @Test
        @DisplayName("парсит пути к картинкам")
        void shouldParseImagePaths() throws JAXBException {
            CommerceInfo info = unmarshal("commerceml/import.xml");

            CmlProduct product = info.getCatalog().getProducts().getFirst();
            assertThat(product.getImages()).containsExactly("import_files/perch_l.jpg");

            // Второй товар без картинки
            CmlProduct product2 = info.getCatalog().getProducts().get(1);
            assertThat(product2.getImages()).isEmpty();
        }

        @Test
        @DisplayName("парсит реквизиты товара")
        void shouldParseRequisiteValues() throws JAXBException {
            CommerceInfo info = unmarshal("commerceml/import.xml");

            CmlProduct product = info.getCatalog().getProducts().getFirst();
            assertThat(product.getRequisiteValues()).hasSize(1);
            assertThat(product.getRequisiteValues().getFirst().getName()).isEqualTo("ВидНоменклатуры");
            assertThat(product.getRequisiteValues().getFirst().getValue()).isEqualTo("Товар");
        }

        @Test
        @DisplayName("offers отсутствуют в import.xml")
        void shouldNotHaveOffersInImportXml() throws JAXBException {
            CommerceInfo info = unmarshal("commerceml/import.xml");

            assertThat(info.getOffersPackage()).isNull();
        }
    }

    @Nested
    @DisplayName("offers.xml — цены и остатки")
    class OffersXmlTests {

        @Test
        @DisplayName("парсит пакет предложений")
        void shouldParseOffersPackage() throws JAXBException {
            CommerceInfo info = unmarshal("commerceml/offers.xml");

            assertThat(info.getOffersPackage()).isNotNull();
            assertThat(info.getOffersPackage().getOffers()).hasSize(2);
        }

        @Test
        @DisplayName("парсит типы цен")
        void shouldParsePriceTypes() throws JAXBException {
            CommerceInfo info = unmarshal("commerceml/offers.xml");

            assertThat(info.getOffersPackage().getPriceTypes()).hasSize(2);
            PriceType priceType = info.getOffersPackage().getPriceTypes().getFirst();
            assertThat(priceType.getName()).isEqualTo("Оптовая");
            assertThat(priceType.getCurrency()).isEqualTo("RUB");
        }

        @Test
        @DisplayName("парсит цену и количество предложения")
        void shouldParseOfferPriceAndQuantity() throws JAXBException {
            CommerceInfo info = unmarshal("commerceml/offers.xml");

            Offer offer = info.getOffersPackage().getOffers().getFirst();
            assertThat(offer.getId()).isEqualTo("ext-001");
            assertThat(offer.getQuantity()).isEqualTo("1000");

            assertThat(offer.getPrices()).hasSize(2);
            OfferPrice price = offer.getPrices().getFirst();
            assertThat(price.getPricePerUnit()).isEqualTo("250.50");
            assertThat(price.getCurrency()).isEqualTo("RUB");
        }

        @Test
        @DisplayName("парсит ставку НДС числовую")
        void shouldParseTaxRateNumeric() throws JAXBException {
            CommerceInfo info = unmarshal("commerceml/offers.xml");

            Offer offer = info.getOffersPackage().getOffers().getFirst();
            assertThat(offer.getTaxRates()).hasSize(1);
            assertThat(offer.getTaxRates().getFirst().getName()).isEqualTo("НДС");
            assertThat(offer.getTaxRates().getFirst().getRate()).isEqualTo("20");
        }

        @Test
        @DisplayName("парсит 'Без НДС' как строку")
        void shouldParseTaxRateNoVat() throws JAXBException {
            CommerceInfo info = unmarshal("commerceml/offers.xml");

            Offer offer = info.getOffersPackage().getOffers().get(1);
            assertThat(offer.getTaxRates().getFirst().getRate()).isEqualTo("Без НДС");
        }

        @Test
        @DisplayName("парсит дробное количество")
        void shouldParseFractionalQuantity() throws JAXBException {
            CommerceInfo info = unmarshal("commerceml/offers.xml");

            Offer offer = info.getOffersPackage().getOffers().get(1);
            assertThat(offer.getQuantity()).isEqualTo("50.000");
        }

        @Test
        @DisplayName("каталог отсутствует в offers.xml")
        void shouldNotHaveCatalogInOffersXml() throws JAXBException {
            CommerceInfo info = unmarshal("commerceml/offers.xml");

            assertThat(info.getCatalog()).isNull();
            assertThat(info.getClassifier()).isNull();
        }
    }
}
