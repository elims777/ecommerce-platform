package ru.rfsnab.integrationservice.service.ftk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import ru.rfsnab.integrationservice.model.ftk.FtkProduct;
import ru.rfsnab.integrationservice.model.ftk.FtkProduct.FtkVariant;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Парсер CommerceML 3.1 от ФТК.
 *
 * Порядок вызовов:
 *   1. parseClassifier(rootImportStream) → ClassifierData (группы + свойства + единицы измерения)
 *   2. parseProducts(goodsImportStream, classifierData) → Map<productUuid, ProductData>
 *   3. parseOffers(offersStream) → Map<offerUuid, OfferData>
 *   4. parsePrices(pricesStream) → Map<offerUuid, BigDecimal>  (StAX — 125 МБ)
 *   5. parseRests(restsStream) → Map<offerUuid, RestData>
 *   6. assemble(products, offers, prices, rests, classifierData, partNumber) → List<FtkProduct>
 */
@Component
@Slf4j
public class FtkXmlParser {

    private static final String RETAIL_PRICE_UUID = "fdf5831f-8b8c-11e9-80f4-005056912b25";

    // ──────────────────────────────────────────────────────────────────────
    // Модели данных
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Все данные из корневого классификатора, нужные для импорта.
     *
     * @param groupPaths       groupUuid → "Родитель > Дочерний > Лист"
     * @param groupParents     groupUuid → parentUuid (null для корневых)
     * @param propertyDefs     propertyUuid → PropertyDef (имя + словарь значений)
     * @param unitsOfMeasure   unitCode → текстовое обозначение ("796" → "шт", "715" → "пар")
     */
    public record ClassifierData(
            Map<String, String> groupPaths,
            Map<String, String> groupParents,
            Map<String, PropertyDef> propertyDefs,
            Map<String, String> unitsOfMeasure
    ) {}

    /** Описание свойства из классификатора: имя и словарь допустимых значений. */
    public record PropertyDef(
            String name,
            Map<String, String> values   // valueUuid → valueText
    ) {}

    /** Данные товара после parseProducts — без цен/остатков. */
    public record ProductData(
            String productUuid,
            String article,
            String name,
            String description,
            List<String> imagePaths,    // все картинки товара (может быть несколько)
            String groupUuid,           // UUID первой группы из <Группы>
            String unitCode,            // код единицы измерения из <БазоваяЕдиница>
            Map<String, String> properties,  // расшифрованные свойства: имя → значение
            boolean deletionMark        // true если <ПометкаУдаления>true</ПометкаУдаления>
    ) {}

    /** Данные предложения из parseOffers. */
    public record OfferData(
            String offerUuid,
            String productUuid,
            String article,
            Integer vatRate,
            Map<String, String> attributes,
            String barcode,
            String countryOfOrigin
    ) {}

    /** Данные остатка из parseRests. */
    public record RestData(
            int quantity,
            String barcode,
            String countryOfOrigin
    ) {}

    // ──────────────────────────────────────────────────────────────────────
    // 1. Классификатор
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Парсит корневой import___.xml.
     * Извлекает: дерево групп, свойства с словарями значений, единицы измерения.
     */
    public ClassifierData parseClassifier(InputStream stream) throws Exception {
        Document doc = buildDom(stream);
        Element classifier = firstChild(doc.getDocumentElement(), "Классификатор");
        if (classifier == null) {
            log.warn("Классификатор не найден в корневом import");
            return new ClassifierData(Map.of(), Map.of(), Map.of(), Map.of());
        }

        Map<String, String> groupPaths   = new LinkedHashMap<>();
        Map<String, String> groupParents = new HashMap<>();
        Element groups = firstChild(classifier, "Группы");
        if (groups != null) {
            walkGroups(groups, null, "", groupPaths, groupParents, 0);
        }

        Map<String, PropertyDef> propertyDefs = parsePropertyDefs(classifier);
        Map<String, String> unitsOfMeasure    = parseUnitsOfMeasure(classifier);

        log.info("Классификатор: {} групп, {} свойств, {} единиц измерения",
                groupPaths.size(), propertyDefs.size(), unitsOfMeasure.size());
        return new ClassifierData(groupPaths, groupParents, propertyDefs, unitsOfMeasure);
    }

    private void walkGroups(Element groupsEl, String parentUuid, String parentPath,
                             Map<String, String> paths, Map<String, String> parents, int depth) {
        if (depth > 10) return;
        NodeList nodes = groupsEl.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element g) || !g.getTagName().equals("Группа")) continue;

            String uuid = text(g, "Ид");
            String name = text(g, "Наименование");
            if (uuid == null || uuid.isBlank()) continue;

            String path = parentPath.isBlank() ? name : parentPath + " > " + name;
            paths.put(uuid, path);
            parents.put(uuid, parentUuid);

            Element sub = firstChild(g, "Группы");
            if (sub != null) walkGroups(sub, uuid, path, paths, parents, depth + 1);
        }
    }

    private Map<String, PropertyDef> parsePropertyDefs(Element classifier) {
        Map<String, PropertyDef> result = new HashMap<>();
        Element свойства = firstChild(classifier, "Свойства");
        if (свойства == null) return result;

        NodeList list = свойства.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            if (!(list.item(i) instanceof Element prop)) continue;
            if (!prop.getTagName().equals("Свойство") && !prop.getTagName().equals("СвойствоНоменклатуры")) continue;

            String uuid = text(prop, "Ид");
            String name = text(prop, "Наименование");
            if (uuid == null) continue;

            Map<String, String> values = new HashMap<>();
            Element variants = firstChild(prop, "ВариантыЗначений");
            if (variants != null) {
                NodeList vList = variants.getChildNodes();
                for (int j = 0; j < vList.getLength(); j++) {
                    if (!(vList.item(j) instanceof Element v)) continue;
                    String vid = text(v, "ИдЗначения");
                    String vval = text(v, "Значение");
                    if (vid != null && vval != null) values.put(vid, vval);
                }
            }
            result.put(uuid, new PropertyDef(name, values));
        }
        return result;
    }

    private Map<String, String> parseUnitsOfMeasure(Element classifier) {
        Map<String, String> result = new HashMap<>();
        Element units = firstChild(classifier, "ЕдиницыИзмерения");
        if (units == null) return result;

        NodeList list = units.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            if (!(list.item(i) instanceof Element u)) continue;
            String code = text(u, "Код");
            String name = text(u, "НаименованиеПолное");
            if (name == null) name = text(u, "Наименование");
            if (code != null && name != null) result.put(code, cleanUnitName(name));
        }
        return result;
    }

    /**
     * Убирает уточняющий хвост из полного наименования единицы ОКЕИ:
     * "Пара (2 шт.)" → "Пара". Пробелы по краям тримятся.
     */
    private String cleanUnitName(String name) {
        return name.replaceFirst("\\s*\\(\\d+\\s*шт\\.?\\)\\s*$", "").trim();
    }

    // ──────────────────────────────────────────────────────────────────────
    // 2. Товары из goods/1/import
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Парсит goods/1/import___.xml.
     * Для каждого товара извлекает все картинки, единицу измерения и расшифрованные свойства.
     */
    public Map<String, ProductData> parseProducts(InputStream stream, ClassifierData classifier) throws Exception {
        Document doc = buildDom(stream);
        Element catalog = firstChild(doc.getDocumentElement(), "Каталог");
        if (catalog == null) {
            log.warn("Каталог не найден в goods/1/import");
            return Map.of();
        }
        Element товары = firstChild(catalog, "Товары");
        if (товары == null) return Map.of();

        Map<String, ProductData> result = new LinkedHashMap<>();
        NodeList товарList = товары.getChildNodes();
        for (int i = 0; i < товарList.getLength(); i++) {
            if (!(товарList.item(i) instanceof Element товар) || !товар.getTagName().equals("Товар")) continue;

            String uuid    = text(товар, "Ид");
            String article = text(товар, "Артикул");
            String name    = text(товар, "Наименование");
            if (uuid == null || uuid.isBlank()) continue;

            boolean deletionMark = "true".equalsIgnoreCase(text(товар, "ПометкаУдаления"));

            List<String> imagePaths = extractAllImagePaths(товар);
            String groupUuid        = extractFirstGroupUuid(товар);
            String unitCode         = extractUnitCode(товар);
            String description      = text(товар, "Описание");

            Map<String, String> properties = extractProperties(товар, classifier.propertyDefs());

            result.put(uuid, new ProductData(uuid, article, name, description,
                    imagePaths, groupUuid, unitCode, properties, deletionMark));
        }
        log.info("Товары: загружено {} позиций", result.size());
        return result;
    }

    private List<String> extractAllImagePaths(Element товар) {
        List<String> paths = new ArrayList<>();
        NodeList nodes = товар.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element el && el.getTagName().equals("Картинка")) {
                String path = el.getTextContent().trim();
                if (!path.isBlank()) paths.add(path);
            }
        }
        return paths;
    }

    private String extractFirstGroupUuid(Element товар) {
        Element groups = firstChild(товар, "Группы");
        if (groups == null) return null;
        NodeList ids = groups.getChildNodes();
        for (int i = 0; i < ids.getLength(); i++) {
            if (ids.item(i) instanceof Element el && el.getTagName().equals("Ид")) {
                String uuid = el.getTextContent().trim();
                if (!uuid.isBlank()) return uuid;
            }
        }
        return null;
    }

    private String extractUnitCode(Element товар) {
        Element base = firstChild(товар, "БазоваяЕдиница");
        if (base == null) return null;
        // Код может быть атрибутом или дочерним тегом
        String code = base.getAttribute("Код");
        if (code == null || code.isBlank()) code = base.getTextContent().trim();
        return (code != null && !code.isBlank()) ? code : null;
    }

    private Map<String, String> extractProperties(Element товар, Map<String, PropertyDef> defs) {
        Map<String, String> result = new LinkedHashMap<>();
        Element значения = firstChild(товар, "ЗначенияСвойств");
        if (значения == null) return result;

        NodeList list = значения.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            if (!(list.item(i) instanceof Element зн)) continue;
            String propId  = text(зн, "Ид");
            String valueId = text(зн, "Значение");
            if (propId == null || valueId == null) continue;

            PropertyDef def = defs.get(propId);
            if (def == null) continue;

            // Значение может быть UUID из словаря или сразу текстом
            String resolvedValue = def.values().getOrDefault(valueId, valueId);
            if (resolvedValue != null && !resolvedValue.isBlank()) {
                result.put(def.name(), resolvedValue);
            }
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 3. Предложения (offers)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Парсит offers___.xml. Извлекает barcode (фк_Штрихкод) и countryOfOrigin (фк_СтранаПроизводства).
     */
    public Map<String, OfferData> parseOffers(InputStream stream) throws Exception {
        Document doc = buildDom(stream);
        Element packet = firstChild(doc.getDocumentElement(), "ПакетПредложений");
        if (packet == null) return Map.of();
        Element предложения = firstChild(packet, "Предложения");
        if (предложения == null) return Map.of();

        Map<String, OfferData> result = new LinkedHashMap<>();
        NodeList list = предложения.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            if (!(list.item(i) instanceof Element offer) || !offer.getTagName().equals("Предложение")) continue;

            String fullId = text(offer, "Ид");
            if (fullId == null || fullId.isBlank()) continue;

            String productUuid = fullId.contains("#") ? fullId.split("#")[0] : fullId;
            String article     = extractArticle(offer);
            Integer vatRate    = extractVatRate(offer);
            Map<String, String> attributes = extractAttributes(offer);
            String barcode          = extractOfferProperty(offer, "фк_Штрихкод");
            String countryOfOrigin  = extractOfferProperty(offer, "фк_СтранаПроизводства");

            result.put(fullId, new OfferData(fullId, productUuid, article, vatRate,
                    attributes, barcode, countryOfOrigin));
        }
        log.info("Предложения: загружено {} офферов", result.size());
        return result;
    }

    private String extractArticle(Element offer) {
        Element props = firstChild(offer, "ЗначенияСвойств");
        if (props == null) return null;
        NodeList items = props.getChildNodes();
        for (int i = 0; i < items.getLength(); i++) {
            if (!(items.item(i) instanceof Element prop)) continue;
            String id = text(prop, "Ид");
            if ("FK_CML2_ARTICLE".equals(id)) return text(prop, "Значение");
        }
        return null;
    }

    private String extractOfferProperty(Element offer, String propId) {
        Element props = firstChild(offer, "ЗначенияСвойств");
        if (props == null) return null;
        NodeList items = props.getChildNodes();
        for (int i = 0; i < items.getLength(); i++) {
            if (!(items.item(i) instanceof Element prop)) continue;
            String id = text(prop, "Ид");
            if (propId.equalsIgnoreCase(id)) return text(prop, "Значение");
        }
        return null;
    }

    private Integer extractVatRate(Element offer) {
        Element taxes = firstChild(offer, "СтавкиНалогов");
        if (taxes == null) return null;
        NodeList items = taxes.getChildNodes();
        for (int i = 0; i < items.getLength(); i++) {
            if (!(items.item(i) instanceof Element tax)) continue;
            String name = text(tax, "Наименование");
            if ("НДС".equals(name)) {
                String rate = text(tax, "Ставка");
                if (rate != null && !rate.isBlank()) {
                    try { return Integer.parseInt(rate.trim()); } catch (NumberFormatException ignored) {}
                }
            }
        }
        return null;
    }

    private Map<String, String> extractAttributes(Element offer) {
        Map<String, String> attrs = new LinkedHashMap<>();
        Element chars = firstChild(offer, "ХарактеристикиТовара");
        if (chars == null) return attrs;
        NodeList items = chars.getChildNodes();
        for (int i = 0; i < items.getLength(); i++) {
            if (!(items.item(i) instanceof Element char_)) continue;
            String name  = text(char_, "Наименование");
            String value = text(char_, "Значение");
            if (name != null && !name.isBlank() && value != null && !value.isBlank()) {
                attrs.put(name, value);
            }
        }
        return attrs;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 4. Цены (StAX — 125 МБ)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Парсит prices___.xml потоковым методом (StAX).
     */
    public Map<String, BigDecimal> parsePrices(InputStream stream) throws Exception {
        Map<String, BigDecimal> result = new HashMap<>();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
        XMLStreamReader reader = factory.createXMLStreamReader(stream);

        String currentOfferId = null;
        boolean insidePrice   = false;
        String currentTypeId  = null;
        String currentValue   = null;
        StringBuilder textBuf = new StringBuilder();

        while (reader.hasNext()) {
            int event = reader.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    String tag = localName(reader);
                    textBuf.setLength(0);
                    switch (tag) {
                        case "Предложение" -> { currentOfferId = null; insidePrice = false; }
                        case "Цена"        -> { insidePrice = true; currentTypeId = null; currentValue = null; }
                        default -> {}
                    }
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> textBuf.append(reader.getText());
                case XMLStreamConstants.END_ELEMENT -> {
                    String tag = localName(reader);
                    String txt = textBuf.toString().trim();
                    textBuf.setLength(0);
                    switch (tag) {
                        case "Ид"            -> { if (currentOfferId == null) currentOfferId = txt; }
                        case "ИдТипаЦены"    -> { if (insidePrice) currentTypeId = txt; }
                        case "ЦенаЗаЕдиницу" -> { if (insidePrice) currentValue  = txt; }
                        case "Цена"          -> {
                            if (insidePrice && RETAIL_PRICE_UUID.equals(currentTypeId) && currentValue != null) {
                                try {
                                    result.put(currentOfferId, new BigDecimal(currentValue.replace(",", ".")));
                                } catch (NumberFormatException e) {
                                    log.warn("Невалидная цена '{}' для offer {}", currentValue, currentOfferId);
                                }
                            }
                            insidePrice = false;
                        }
                        default -> {}
                    }
                }
            }
        }
        reader.close();
        log.info("Цены: загружено {} розничных цен", result.size());
        return result;
    }

    private String localName(XMLStreamReader reader) {
        String local = reader.getLocalName();
        return local != null ? local : reader.getName().getLocalPart();
    }

    // ──────────────────────────────────────────────────────────────────────
    // 5. Остатки (rests)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Парсит rests___.xml. Дополнительно извлекает barcode и countryOfOrigin из реквизитов.
     */
    public Map<String, RestData> parseRests(InputStream stream) throws Exception {
        Document doc = buildDom(stream);
        Element packet = firstChild(doc.getDocumentElement(), "ПакетПредложений");
        if (packet == null) return Map.of();
        Element предложения = firstChild(packet, "Предложения");
        if (предложения == null) return Map.of();

        Map<String, RestData> result = new HashMap<>();
        NodeList list = предложения.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            if (!(list.item(i) instanceof Element offer) || !offer.getTagName().equals("Предложение")) continue;
            String uuid = text(offer, "Ид");
            if (uuid == null || uuid.isBlank()) continue;

            int qty = extractQuantityInt(offer);
            String barcode         = extractRequisite(offer, "фк_Штрихкод");
            String countryOfOrigin = extractRequisite(offer, "фк_СтранаПроизводства");

            result.put(uuid, new RestData(qty, barcode, countryOfOrigin));
        }
        log.info("Остатки: загружено {} записей", result.size());
        return result;
    }

    private int extractQuantityInt(Element offer) {
        Element остатки = firstChild(offer, "Остатки");
        if (остатки == null) return 0;
        NodeList items = остатки.getChildNodes();
        for (int i = 0; i < items.getLength(); i++) {
            if (!(items.item(i) instanceof Element остаток)) continue;
            String qty = text(остаток, "Количество");
            if (qty != null && !qty.isBlank()) {
                try { return Integer.parseInt(qty.trim()); } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    private String extractRequisite(Element offer, String propId) {
        Element реквизиты = firstChild(offer, "ЗначенияРеквизитов");
        if (реквизиты == null) return null;
        NodeList items = реквизиты.getChildNodes();
        for (int i = 0; i < items.getLength(); i++) {
            if (!(items.item(i) instanceof Element req)) continue;
            String name = text(req, "Наименование");
            if (propId.equalsIgnoreCase(name)) return text(req, "Значение");
        }
        return null;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 6. Сборка итогового списка
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Собирает List<FtkProduct> из всех пяти проходов.
     * Товары с ПометкаУдаления=true помечаются как неактивные (isActive=false в FtkVariant).
     */
    public List<FtkProduct> assemble(
            Map<String, ProductData> products,
            Map<String, OfferData> offers,
            Map<String, BigDecimal> prices,
            Map<String, RestData> rests,
            ClassifierData classifier,
            int partNumber
    ) {
        Map<String, List<OfferData>> offersByProduct = new HashMap<>();
        for (OfferData od : offers.values()) {
            offersByProduct.computeIfAbsent(od.productUuid(), k -> new ArrayList<>()).add(od);
        }

        List<FtkProduct> result = new ArrayList<>();
        for (ProductData pd : products.values()) {
            List<OfferData> productOffers = offersByProduct.getOrDefault(pd.productUuid(), List.of());
            if (productOffers.isEmpty()) {
                log.debug("Товар без офферов: uuid={}, article={}", pd.productUuid(), pd.article());
                continue;
            }

            String unitOfMeasure = pd.unitCode() != null ? classifier.unitsOfMeasure().get(pd.unitCode()) : null;

            List<FtkVariant> variants = new ArrayList<>();
            for (OfferData od : productOffers) {
                BigDecimal price = prices.get(od.offerUuid());
                RestData rest    = rests.getOrDefault(od.offerUuid(), new RestData(0, null, null));
                String article   = od.article() != null ? od.article() : pd.article();

                // barcode и countryOfOrigin: приоритет у rests, fallback — offers
                String barcode        = rest.barcode()        != null ? rest.barcode()        : od.barcode();
                String countryOrigin  = rest.countryOfOrigin() != null ? rest.countryOfOrigin() : od.countryOfOrigin();

                variants.add(FtkVariant.builder()
                        .offerUuid(od.offerUuid())
                        .article(article)
                        .price(price)
                        .stockQuantity(rest.quantity())
                        .attributes(od.attributes())
                        .vatRate(od.vatRate())
                        .barcode(barcode)
                        .countryOfOrigin(countryOrigin)
                        .deleted(pd.deletionMark())
                        .build());
            }

            result.add(FtkProduct.builder()
                    .productUuid(pd.productUuid())
                    .article(pd.article())
                    .name(pd.name())
                    .description(pd.description())
                    .imagePaths(pd.imagePaths())
                    .groupUuid(pd.groupUuid())
                    .unitOfMeasure(unitOfMeasure)
                    .partNumber(partNumber)
                    .properties(pd.properties())
                    .variants(variants)
                    .build());
        }
        log.info("Сборка: {} товаров с вариантами", result.size());
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Утилиты
    // ──────────────────────────────────────────────────────────────────────

    private Document buildDom(InputStream stream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(stream);
    }

    private Element firstChild(Element parent, String tagName) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element el && el.getTagName().equals(tagName)) return el;
        }
        return null;
    }

    private String text(Element parent, String tagName) {
        Element child = firstChild(parent, tagName);
        return child != null ? child.getTextContent().trim() : null;
    }
}
