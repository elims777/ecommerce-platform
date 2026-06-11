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
 *   1. parseClassifier(rootImportStream) → Map groupUuid→path
 *   2. parseProducts(goodsImportStream, groupPaths) → List<FtkProduct> (без цен/остатков)
 *   3. parseOffers(offersStream) → Map offerUuid→OfferData
 *   4. parsePrices(pricesStream) → Map offerUuid→BigDecimal  (StAX — 125 МБ)
 *   5. parseRests(restsStream) → Map offerUuid→Integer
 *   6. assemble(products, offers, prices, rests) → List<FtkProduct>
 */
@Component
@Slf4j
public class FtkXmlParser {

    private static final String RETAIL_PRICE_UUID = "fdf5831f-8b8c-11e9-80f4-005056912b25";

    // ──────────────────────────────────────────────────────────────────────
    // 1. Классификатор — дерево групп
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Парсит корневой import___.xml, возвращает Map: groupUuid → "Родитель > Дочерний > Лист".
     */
    public Map<String, String> parseClassifier(InputStream stream) throws Exception {
        Document doc = buildDom(stream);
        Element classifier = firstChild(doc.getDocumentElement(), "Классификатор");
        if (classifier == null) {
            log.warn("Классификатор не найден в корневом import");
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        Element groups = firstChild(classifier, "Группы");
        if (groups != null) {
            walkGroups(groups.getElementsByTagName("Группа"), "", result, 0);
        }
        log.info("Классификатор: загружено {} групп", result.size());
        return result;
    }

    /** Рекурсивный обход дерева групп. depth ограничивает рекурсию для безопасности. */
    private void walkGroups(NodeList groupNodes, String parentPath, Map<String, String> result, int depth) {
        if (depth > 10) return;
        for (int i = 0; i < groupNodes.getLength(); i++) {
            if (!(groupNodes.item(i) instanceof Element group)) continue;
            // Берём только прямых детей, чтобы не захватить вложенные группы
            if (!group.getParentNode().equals(group.getParentNode())) continue;

            String uuid = text(group, "Ид");
            String name = text(group, "Наименование");
            if (uuid == null || uuid.isBlank()) continue;

            String path = parentPath.isBlank() ? name : parentPath + " > " + name;
            result.put(uuid, path);

            Element subGroups = firstChild(group, "Группы");
            if (subGroups != null) {
                NodeList children = subGroups.getChildNodes();
                List<Element> childElements = new ArrayList<>();
                for (int j = 0; j < children.getLength(); j++) {
                    if (children.item(j) instanceof Element e && e.getTagName().equals("Группа")) {
                        childElements.add(e);
                    }
                }
                for (Element child : childElements) {
                    String childUuid = text(child, "Ид");
                    String childName = text(child, "Наименование");
                    if (childUuid == null || childUuid.isBlank()) continue;
                    String childPath = path + " > " + childName;
                    result.put(childUuid, childPath);
                    Element childSubGroups = firstChild(child, "Группы");
                    if (childSubGroups != null) {
                        walkGroupsElement(childSubGroups, childPath, result, depth + 1);
                    }
                }
            }
        }
    }

    private void walkGroupsElement(Element groupsEl, String parentPath, Map<String, String> result, int depth) {
        if (depth > 10) return;
        NodeList children = groupsEl.getChildNodes();
        for (int j = 0; j < children.getLength(); j++) {
            if (!(children.item(j) instanceof Element child) || !child.getTagName().equals("Группа")) continue;
            String uuid = text(child, "Ид");
            String name = text(child, "Наименование");
            if (uuid == null || uuid.isBlank()) continue;
            String path = parentPath + " > " + name;
            result.put(uuid, path);
            Element sub = firstChild(child, "Группы");
            if (sub != null) {
                walkGroupsElement(sub, path, result, depth + 1);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 2. Товары из goods/1/import
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Промежуточная модель — данные товара до обогащения ценами/остатками.
     */
    public record ProductData(
            String productUuid,
            String article,
            String name,
            String description,
            String imagePath,
            String categoryPath   // первый groupUuid → путь через classifierMap
    ) {}

    /**
     * Парсит goods/1/import___.xml, возвращает Map: productUuid → ProductData.
     */
    public Map<String, ProductData> parseProducts(InputStream stream, Map<String, String> groupPaths) throws Exception {
        Document doc = buildDom(stream);
        Element catalog = firstChild(doc.getDocumentElement(), "Каталог");
        if (catalog == null) {
            log.warn("Каталог не найден в goods/1/import");
            return Map.of();
        }
        Element товары = firstChild(catalog, "Товары");
        if (товары == null) return Map.of();

        Map<String, ProductData> result = new LinkedHashMap<>();
        NodeList товарList = товары.getElementsByTagName("Товар");
        for (int i = 0; i < товарList.getLength(); i++) {
            if (!(товарList.item(i) instanceof Element товар)) continue;
            // Берём только прямых детей тега Товары
            if (!товар.getParentNode().getNodeName().equals("Товары")) continue;

            String uuid       = text(товар, "Ид");
            String article    = text(товар, "Артикул");
            String name       = text(товар, "Наименование");
            String description = text(товар, "Описание");
            String imagePath  = text(товар, "Картинка");

            if (uuid == null || uuid.isBlank()) continue;

            String categoryPath = resolveCategoryPath(товар, groupPaths);

            result.put(uuid, new ProductData(uuid, article, name, description, imagePath, categoryPath));
        }
        log.info("Товары: загружено {} позиций", result.size());
        return result;
    }

    /** Берёт первый UUID из <Группы><Ид>...</Ид>...</Группы> и ищет путь в словаре. */
    private String resolveCategoryPath(Element товар, Map<String, String> groupPaths) {
        Element groups = firstChild(товар, "Группы");
        if (groups == null) return null;
        NodeList ids = groups.getChildNodes();
        for (int i = 0; i < ids.getLength(); i++) {
            if (!(ids.item(i) instanceof Element el) || !el.getTagName().equals("Ид")) continue;
            String groupUuid = el.getTextContent().trim();
            if (!groupUuid.isBlank()) {
                return groupPaths.get(groupUuid);
            }
        }
        return null;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 3. Предложения (offers)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Промежуточная модель предложения (SKU/вариант).
     */
    public record OfferData(
            String offerUuid,     // полный Ид: UUID_товара или UUID_товара#UUID_варианта
            String productUuid,   // UUID_товара (до #)
            String article,       // FK_CML2_ARTICLE
            Integer vatRate,      // % НДС
            Map<String, String> attributes  // из ХарактеристикиТовара, только непустые
    ) {}

    /**
     * Парсит offers___.xml, возвращает Map: offerUuid → OfferData.
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
            String article = extractArticle(offer);
            Integer vatRate = extractVatRate(offer);
            Map<String, String> attributes = extractAttributes(offer);

            result.put(fullId, new OfferData(fullId, productUuid, article, vatRate, attributes));
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
            if ("FK_CML2_ARTICLE".equals(id)) {
                return text(prop, "Значение");
            }
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

    /** Извлекает непустые атрибуты из ХарактеристикиТовара (текстовые ключи). */
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
     * Возвращает Map: offerUuid → розничная цена.
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
                        case "Ид" -> {
                            if (currentOfferId == null) currentOfferId = txt;
                        }
                        case "ИдТипаЦены"    -> { if (insidePrice) currentTypeId = txt; }
                        case "ЦенаЗаЕдиницу" -> { if (insidePrice) currentValue  = txt; }
                        case "Цена" -> {
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
     * Парсит rests___.xml, возвращает Map: offerUuid → stockQuantity.
     */
    public Map<String, Integer> parseRests(InputStream stream) throws Exception {
        Document doc = buildDom(stream);
        Element packet = firstChild(doc.getDocumentElement(), "ПакетПредложений");
        if (packet == null) return Map.of();
        Element предложения = firstChild(packet, "Предложения");
        if (предложения == null) return Map.of();

        Map<String, Integer> result = new HashMap<>();
        NodeList list = предложения.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            if (!(list.item(i) instanceof Element offer) || !offer.getTagName().equals("Предложение")) continue;
            String uuid = text(offer, "Ид");
            if (uuid == null || uuid.isBlank()) continue;
            String qty = extractQuantity(offer);
            if (qty != null) {
                try { result.put(uuid, Integer.parseInt(qty)); } catch (NumberFormatException ignored) {}
            }
        }
        log.info("Остатки: загружено {} записей", result.size());
        return result;
    }

    private String extractQuantity(Element offer) {
        Element остатки = firstChild(offer, "Остатки");
        if (остатки == null) return null;
        NodeList items = остатки.getChildNodes();
        for (int i = 0; i < items.getLength(); i++) {
            if (!(items.item(i) instanceof Element остаток)) continue;
            String qty = text(остаток, "Количество");
            if (qty != null && !qty.isBlank()) return qty;
        }
        return null;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 6. Сборка итогового списка
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Собирает List<FtkProduct> из всех пяти проходов.
     * Для каждого товара из import находит его офферы, обогащает ценой и остатком.
     */
    public List<FtkProduct> assemble(
            Map<String, ProductData> products,
            Map<String, OfferData> offers,
            Map<String, BigDecimal> prices,
            Map<String, Integer> rests
    ) {
        // offerUuid → productUuid обратный индекс уже в OfferData
        // Собираем offers по productUuid
        Map<String, List<OfferData>> offersByProduct = new HashMap<>();
        for (OfferData od : offers.values()) {
            offersByProduct.computeIfAbsent(od.productUuid(), k -> new ArrayList<>()).add(od);
        }

        List<FtkProduct> result = new ArrayList<>();
        for (ProductData pd : products.values()) {
            List<OfferData> productOffers = offersByProduct.getOrDefault(pd.productUuid(), List.of());
            if (productOffers.isEmpty()) {
                // Товар без офферов — пропускаем (нет цены, нет варианта)
                log.debug("Товар без офферов: uuid={}, article={}", pd.productUuid(), pd.article());
                continue;
            }

            List<FtkVariant> variants = new ArrayList<>();
            for (OfferData od : productOffers) {
                BigDecimal price = prices.get(od.offerUuid());
                int stock = rests.getOrDefault(od.offerUuid(), 0);
                String article = od.article() != null ? od.article() : pd.article();

                variants.add(FtkVariant.builder()
                        .offerUuid(od.offerUuid())
                        .article(article)
                        .price(price)
                        .stockQuantity(stock)
                        .attributes(od.attributes())
                        .vatRate(od.vatRate())
                        .build());
            }

            result.add(FtkProduct.builder()
                    .productUuid(pd.productUuid())
                    .article(pd.article())
                    .name(pd.name())
                    .description(pd.description())
                    .imagePath(pd.imagePath())
                    .categoryPath(pd.categoryPath())
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
        // Защита от XXE
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(stream);
    }

    /** Первый дочерний элемент с данным именем тега. */
    private Element firstChild(Element parent, String tagName) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element el && el.getTagName().equals(tagName)) {
                return el;
            }
        }
        return null;
    }

    /** Первый дочерний элемент с данным именем тега (от Document). */
    private Element firstChild(Document doc, String tagName) {
        return firstChild(doc.getDocumentElement(), tagName);
    }

    /** Текстовое содержимое первого дочернего тега. */
    private String text(Element parent, String tagName) {
        Element child = firstChild(parent, tagName);
        return child != null ? child.getTextContent().trim() : null;
    }
}
