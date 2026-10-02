package ru.rfsnab.productservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import ru.rfsnab.productservice.BaseIntegrationTest;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.model.News;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.ProductImage;
import ru.rfsnab.productservice.repository.CategoryRepository;
import ru.rfsnab.productservice.repository.NewsRepository;
import ru.rfsnab.productservice.repository.ProductImageRepository;
import ru.rfsnab.productservice.repository.ProductRepository;
import ru.rfsnab.productservice.service.CategoryService;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("YML-фид и sitemap Integration")
class ProductFeedControllerIntegrationTest extends BaseIntegrationTest {

    private static final String FEED_URL = "/api/v1/products/feed/yandex.yml";
    private static final String SITEMAP_URL = "/api/v1/products/sitemap.xml";
    private static final String SPECIAL_NAME = "Труба & \"Фитинг\" <1> «Люкс»";

    @Autowired MockMvc mockMvc;
    @Autowired ProductRepository productRepository;
    @Autowired ProductImageRepository imageRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired CategoryService categoryService;
    @Autowired NewsRepository newsRepository;

    private Category category;

    @BeforeEach
    void setUp() {
        imageRepository.deleteAll();
        productRepository.deleteAll();
        newsRepository.deleteAll();
        categoryRepository.deleteAll();
        category = categoryRepository.save(Category.builder()
                .name("Сантехника").slug("santehnika-" + System.nanoTime()).build());
        categoryService.refreshCategoryTree();
    }

    private Product save(String name, boolean active, boolean variantChild, String wholesale, boolean withImage) {
        Product p = productRepository.save(Product.builder()
                .name(name).slug("feed-" + System.nanoTime())
                .price(new BigDecimal("2000.00"))
                .wholesalePrice(wholesale != null ? new BigDecimal(wholesale) : null)
                .isActive(active).isVariantChild(variantChild)
                .category(category)
                .build());
        if (withImage) {
            imageRepository.save(ProductImage.builder()
                    .product(p).fileKey("k" + p.getId())
                    .fileUrl("https://storage.yandexcloud.net/test-bucket/k" + p.getId() + ".jpg")
                    .fileSize(1L).contentType("image/jpeg").isPrimary(true).build());
        }
        return p;
    }

    private Document fetchXml(String url) throws Exception {
        String body = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    private Element offer(Document doc, Product p) {
        NodeList offers = doc.getElementsByTagName("offer");
        for (int i = 0; i < offers.getLength(); i++) {
            Element e = (Element) offers.item(i);
            if (e.getAttribute("id").equals(String.valueOf(p.getId()))) {
                return e;
            }
        }
        return null;
    }

    private String child(Element offer, String tag) {
        return offer.getElementsByTagName(tag).item(0).getTextContent();
    }

    @Test
    @DisplayName("фид: активный товар с картинкой и ценой есть, лишние отфильтрованы")
    void feed_includesOnlyEligibleProducts() throws Exception {
        Product ok = save("Кран", true, false, "1500.00", true);
        Product inactive = save("Неактивный", false, false, "1500.00", true);
        Product variant = save("Вариант", true, true, "1500.00", true);
        Product noImage = save("Без картинки", true, false, "1500.00", false);
        Product zeroPrice = save("Нулевая цена", true, false, "0.00", true);

        Document doc = fetchXml(FEED_URL);

        Element offer = offer(doc, ok);
        assertThat(offer).isNotNull();
        assertThat(offer.getAttribute("available")).isEqualTo("true");
        assertThat(child(offer, "price")).isEqualTo("1500.00");
        assertThat(child(offer, "url")).isEqualTo("https://rfsnab.ru/products/" + ok.getId());
        assertThat(child(offer, "categoryId")).isEqualTo(String.valueOf(category.getId()));
        assertThat(child(offer, "picture")).endsWith("k" + ok.getId() + ".jpg");
        assertThat(offer(doc, inactive)).isNull();
        assertThat(offer(doc, variant)).isNull();
        assertThat(offer(doc, noImage)).isNull();
        assertThat(offer(doc, zeroPrice)).isNull();
        assertThat(doc.getElementsByTagName("category").getLength()).isEqualTo(1);
    }

    @Test
    @DisplayName("фид: акционная цена применяется как на витрине")
    void feed_appliesSalePrice() throws Exception {
        Product p = save("Акционный", true, false, "1000.00", true);
        p.setIsSale(true);
        p.setSaleMarkupPercent(new BigDecimal("-10.00"));
        productRepository.save(p);

        Element offer = offer(fetchXml(FEED_URL), p);

        assertThat(child(offer, "price")).isEqualTo("900.00");
    }

    @Test
    @DisplayName("фид: спецсимволы в названии экранированы, XML парсится")
    void feed_escapesSpecialCharacters() throws Exception {
        Product p = save(SPECIAL_NAME, true, false, "100.00", true);

        Element offer = offer(fetchXml(FEED_URL), p);

        assertThat(child(offer, "name")).isEqualTo(SPECIAL_NAME);
    }

    @Test
    @DisplayName("фид: неактивная категория не попадает в categories, у оффера нет categoryId")
    void feed_skipsInactiveCategory() throws Exception {
        Category inactive = categoryRepository.save(Category.builder()
                .name("Скрытая").slug("hidden-" + System.nanoTime()).isActive(false).build());
        categoryService.refreshCategoryTree();
        Product p = save("Товар скрытой", true, false, "100.00", true);
        p.setCategory(inactive);
        productRepository.save(p);

        Document doc = fetchXml(FEED_URL);

        NodeList categories = doc.getElementsByTagName("category");
        assertThat(categories.getLength()).isEqualTo(1);
        assertThat(((Element) categories.item(0)).getAttribute("id")).isEqualTo(String.valueOf(category.getId()));
        Element offer = offer(doc, p);
        assertThat(offer).isNotNull();
        assertThat(offer.getElementsByTagName("categoryId").getLength()).isZero();
    }

    @Test
    @DisplayName("фид: HTML-теги в описании вырезаются")
    void feed_stripsHtmlFromDescription() throws Exception {
        Product p = save("С описанием", true, false, "100.00", true);
        p.setDescription("<p><b>текст</b> и <i>ещё</i></p>");
        productRepository.save(p);

        Element offer = offer(fetchXml(FEED_URL), p);

        assertThat(child(offer, "description")).isEqualTo("текст и ещё");
    }

    @Test
    @DisplayName("sitemap: содержит товар, категорию и опубликованную новость, но не черновик")
    void sitemap_containsProductCategoryAndPublishedNews() throws Exception {
        Product p = save("Кран", true, false, "1500.00", true);
        newsRepository.save(News.builder().title("Опубликована").slug("published-news")
                .contentHtml("<p>x</p>").isPublished(true).build());
        newsRepository.save(News.builder().title("Черновик").slug("draft-news")
                .contentHtml("<p>x</p>").isPublished(false).build());

        String body = mockMvc.perform(get(SITEMAP_URL))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body)
                .contains("<loc>https://rfsnab.ru/products/" + p.getId() + "</loc>")
                .contains("<loc>https://rfsnab.ru/catalog?category=" + category.getId() + "</loc>")
                .contains("<loc>https://rfsnab.ru/news/published-news</loc>")
                .contains("<loc>https://rfsnab.ru/about</loc>")
                .doesNotContain("draft-news");
    }
}
