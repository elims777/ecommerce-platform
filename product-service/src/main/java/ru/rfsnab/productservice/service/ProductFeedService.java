package ru.rfsnab.productservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.productservice.dto.CategoryTreeDTO;
import ru.rfsnab.productservice.model.News;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.repository.NewsRepository;
import ru.rfsnab.productservice.repository.ProductImageRepository;
import ru.rfsnab.productservice.repository.ProductRepository;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * YML-фид для Яндекс Директа и sitemap.xml. Id оффера = id родительского товара —
 * тот же id фронт передаёт в ecommerce Метрики.
 */
@Slf4j
@Service
public class ProductFeedService {

    private static final String XML_VERSION = "1.0";
    private static final String XML_ENCODING = "UTF-8";
    private static final String SHOP_NAME = "РФснаб";
    private static final String COMPANY_NAME = "ООО «МСВ»";
    private static final String CURRENCY = "RUB";
    private static final String SITEMAP_NS = "http://www.sitemaps.org/schemas/sitemap/0.9";
    /** Лимит Яндекса на описание оффера — 3000 символов. */
    private static final int MAX_DESCRIPTION_LENGTH = 3000;
    private static final DateTimeFormatter FEED_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId FEED_ZONE = ZoneId.of("Europe/Moscow");
    /** Символы, недопустимые в XML 1.0: управляющие (кроме \t \n \r), одиночные суррогаты, ￾, ￿. */
    private static final Pattern INVALID_XML_CHARS =
            Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\uD800-\\uDFFF\\uFFFE\\uFFFF]");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final List<String> STATIC_PAGES =
            List.of("/", "/catalog", "/about", "/contacts", "/news");

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final CategoryService categoryService;
    private final NewsRepository newsRepository;
    private final String siteUrl;

    public ProductFeedService(ProductRepository productRepository,
                              ProductImageRepository imageRepository,
                              CategoryService categoryService,
                              NewsRepository newsRepository,
                              @Value("${site.base-url:https://rfsnab.ru}") String siteUrl) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.categoryService = categoryService;
        this.newsRepository = newsRepository;
        this.siteUrl = siteUrl;
    }

    /** Товар, попавший в фид, с итоговой B2C-ценой (акция учтена) и главной картинкой. */
    private record Offer(Product product, BigDecimal price, String pictureUrl) {}

    @Transactional(readOnly = true)
    public String buildYandexFeed() {
        List<Offer> offers = loadOffers();
        int withoutCategory = 0;
        StringWriter out = new StringWriter();
        try {
            XMLStreamWriter w = XMLOutputFactory.newFactory().createXMLStreamWriter(out);
            w.writeStartDocument(XML_ENCODING, XML_VERSION);
            w.writeStartElement("yml_catalog");
            w.writeAttribute("date", ZonedDateTime.now(FEED_ZONE).format(FEED_DATE));
            w.writeStartElement("shop");
            text(w, "name", SHOP_NAME);
            text(w, "company", COMPANY_NAME);
            text(w, "url", siteUrl);
            w.writeStartElement("currencies");
            w.writeEmptyElement("currency");
            w.writeAttribute("id", CURRENCY);
            w.writeAttribute("rate", "1");
            w.writeEndElement();

            w.writeStartElement("categories");
            Set<Long> categoryIds = new HashSet<>();
            for (CategoryTreeDTO category : flattenCategories()) {
                if (!Boolean.TRUE.equals(category.getIsActive())) {
                    continue;
                }
                categoryIds.add(category.getId());
                w.writeStartElement("category");
                w.writeAttribute("id", String.valueOf(category.getId()));
                if (category.getParentId() != null) {
                    w.writeAttribute("parentId", String.valueOf(category.getParentId()));
                }
                w.writeCharacters(clean(category.getName()));
                w.writeEndElement();
            }
            w.writeEndElement();

            // Витрина не скрывает товары неактивных категорий, поэтому офферы остаются,
            // а categoryId выводится только для категорий из списка выше
            w.writeStartElement("offers");
            for (Offer offer : offers) {
                if (!writeOffer(w, offer, categoryIds)) {
                    withoutCategory++;
                }
            }
            w.writeEndElement();

            w.writeEndElement();
            w.writeEndElement();
            w.writeEndDocument();
            w.close();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Не удалось сформировать YML-фид: " + e.getMessage(), e);
        }
        log.info("YML-фид сформирован: {} товаров, из них без категории {}", offers.size(), withoutCategory);
        return out.toString();
    }

    @Transactional(readOnly = true)
    public String buildSitemap() {
        List<String> paths = new ArrayList<>(STATIC_PAGES);
        for (CategoryTreeDTO category : flattenCategories()) {
            if (Boolean.TRUE.equals(category.getIsActive())) {
                paths.add("/catalog?category=" + category.getId());
            }
        }
        for (Offer offer : loadOffers()) {
            paths.add("/products/" + offer.product().getId());
        }
        for (News news : newsRepository.findByIsPublishedTrue(Pageable.unpaged())) {
            paths.add("/news/" + news.getSlug());
        }

        StringWriter out = new StringWriter();
        try {
            XMLStreamWriter w = XMLOutputFactory.newFactory().createXMLStreamWriter(out);
            w.writeStartDocument(XML_ENCODING, XML_VERSION);
            w.writeStartElement("urlset");
            w.writeDefaultNamespace(SITEMAP_NS);
            for (String path : paths) {
                w.writeStartElement("url");
                text(w, "loc", siteUrl + path);
                w.writeEndElement();
            }
            w.writeEndElement();
            w.writeEndDocument();
            w.close();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Не удалось сформировать sitemap: " + e.getMessage(), e);
        }
        log.info("sitemap сформирован: {} URL", paths.size());
        return out.toString();
    }

    /** @return true, если у оффера выведен categoryId */
    private boolean writeOffer(XMLStreamWriter w, Offer offer, Set<Long> categoryIds) throws XMLStreamException {
        Product p = offer.product();
        w.writeStartElement("offer");
        w.writeAttribute("id", String.valueOf(p.getId()));
        // Сайт не скрывает товары без остатка (показывает «под заказ»), поэтому в фиде всегда в наличии
        w.writeAttribute("available", "true");
        text(w, "url", siteUrl + "/products/" + p.getId());
        text(w, "price", offer.price().toPlainString());
        text(w, "currencyId", CURRENCY);
        boolean hasCategory = p.getCategory() != null && categoryIds.contains(p.getCategory().getId());
        if (hasCategory) {
            text(w, "categoryId", String.valueOf(p.getCategory().getId()));
        }
        text(w, "picture", offer.pictureUrl());
        text(w, "name", p.getName());
        String description = pickDescription(p);
        if (description != null) {
            text(w, "description", description);
        }
        w.writeEndElement();
        return hasCategory;
    }

    /**
     * Активные родительские товары с картинкой и ценой > 0. Цена и акция считаются как на витрине
     * для B2C: wholesalePrice (исторически «розничная») либо price, затем процент акции.
     */
    private List<Offer> loadOffers() {
        Map<Long, String> pictures = new LinkedHashMap<>();
        for (Object[] row : imageRepository.findFeedImageUrls()) {
            pictures.putIfAbsent((Long) row[0], (String) row[1]);
        }

        List<Offer> offers = new ArrayList<>();
        int skippedNoPicture = 0;
        int skippedNoPrice = 0;
        for (Product p : productRepository.findAllForFeed()) {
            String picture = pictures.get(p.getId());
            if (picture == null) {
                skippedNoPicture++;
                continue;
            }
            BigDecimal base = p.getWholesalePrice() != null ? p.getWholesalePrice() : p.getPrice();
            BigDecimal price = SalePriceCalculator.apply(base,
                    SalePriceCalculator.resolveMarkup(p, categoryService.getCategorySaleMarkup(
                            p.getCategory() != null ? p.getCategory().getId() : null)));
            if (price == null || price.signum() <= 0) {
                skippedNoPrice++;
                continue;
            }
            offers.add(new Offer(p, price, picture));
        }
        log.info("Фид: пропущено без картинки {}, без цены {}", skippedNoPicture, skippedNoPrice);
        return offers;
    }

    private List<CategoryTreeDTO> flattenCategories() {
        List<CategoryTreeDTO> flat = new ArrayList<>();
        collect(categoryService.getCategoryTree(), flat);
        return flat;
    }

    private void collect(List<CategoryTreeDTO> nodes, List<CategoryTreeDTO> into) {
        for (CategoryTreeDTO node : nodes) {
            into.add(node);
            collect(node.getChildren(), into);
        }
    }

    private String pickDescription(Product p) {
        for (String candidate : new String[]{p.getDescription(), p.getImportDescription(), p.getShortDescription()}) {
            if (candidate != null && !candidate.isBlank()) {
                String plain = WHITESPACE.matcher(HTML_TAG.matcher(candidate).replaceAll(" ")).replaceAll(" ").trim();
                String cleaned = clean(plain);
                return cleaned.length() > MAX_DESCRIPTION_LENGTH
                        ? cleaned.substring(0, MAX_DESCRIPTION_LENGTH)
                        : cleaned;
            }
        }
        return null;
    }

    private void text(XMLStreamWriter w, String name, String value) throws XMLStreamException {
        w.writeStartElement(name);
        w.writeCharacters(clean(value));
        w.writeEndElement();
    }

    private String clean(String value) {
        return value == null ? "" : INVALID_XML_CHARS.matcher(value).replaceAll("");
    }
}
