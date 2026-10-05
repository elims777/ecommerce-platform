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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    @DisplayName("фид: активная категория под неактивным родителем не попадает в categories")
    void feed_skipsCategoryWithInactiveParent() throws Exception {
        Category inactiveParent = categoryRepository.save(Category.builder()
                .name("Скрытый родитель").slug("hidden-parent-" + System.nanoTime()).isActive(false).build());
        Category orphan = categoryRepository.save(Category.builder()
                .name("Дочерняя").slug("orphan-" + System.nanoTime()).parent(inactiveParent).build());
        categoryService.refreshCategoryTree();
        Product p = save("Товар дочерней", true, false, "100.00", true);
        p.setCategory(orphan);
        productRepository.save(p);

        Document doc = fetchXml(FEED_URL);

        NodeList categories = doc.getElementsByTagName("category");
        for (int i = 0; i < categories.getLength(); i++) {
            Element c = (Element) categories.item(i);
            assertThat(c.getAttribute("id")).isNotEqualTo(String.valueOf(orphan.getId()));
            assertThat(c.getAttribute("parentId")).isNotEqualTo(String.valueOf(inactiveParent.getId()));
        }
        Element offer = offer(doc, p);
        assertThat(offer).isNotNull();
        assertThat(offer.getElementsByTagName("categoryId").getLength()).isZero();
        assertThat(collectionIds(offer)).isEmpty();
    }

    private Element collection(Document doc, Category c) {
        NodeList collections = doc.getElementsByTagName("collection");
        for (int i = 0; i < collections.getLength(); i++) {
            Element e = (Element) collections.item(i);
            if (e.getAttribute("id").equals(String.valueOf(c.getId()))) {
                return e;
            }
        }
        return null;
    }

    @Test
    @DisplayName("фид: коллекция для категории с товаром содержит url, picture и name")
    void feed_collectionForCategoryWithProduct() throws Exception {
        Product p = save("Кран", true, false, "100.00", true);

        Element collection = collection(fetchXml(FEED_URL), category);

        assertThat(collection).isNotNull();
        assertThat(child(collection, "url")).isEqualTo("https://rfsnab.ru/catalog?category=" + category.getId());
        assertThat(child(collection, "picture")).endsWith("k" + p.getId() + ".jpg");
        assertThat(child(collection, "name")).isEqualTo("Сантехника");
        assertThat(child(collection, "description")).isEqualTo("Сантехника в интернет-магазине РФснаб");
    }

    private List<String> childTags(Element parent) {
        List<String> tags = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element e) {
                tags.add(e.getTagName());
            }
        }
        return tags;
    }

    private List<String> collectionIds(Element offer) {
        NodeList ids = offer.getElementsByTagName("collectionId");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < ids.getLength(); i++) {
            result.add(ids.item(i).getTextContent());
        }
        return result;
    }

    @Test
    @DisplayName("фид: порядок блоков в shop — categories, offers, collections")
    void feed_collectionsGoAfterOffers() throws Exception {
        save("Кран", true, false, "100.00", true);

        Element shop = (Element) fetchXml(FEED_URL).getElementsByTagName("shop").item(0);

        List<String> tags = childTags(shop);
        assertThat(tags.indexOf("categories")).isLessThan(tags.indexOf("offers"));
        assertThat(tags.indexOf("offers")).isLessThan(tags.indexOf("collections"));
    }

    @Test
    @DisplayName("фид: оффер подкатегории ссылается на коллекции своей категории и родителя, после description")
    void feed_offerHasCollectionIdsOfCategoryAndAncestors() throws Exception {
        Category parent = categoryRepository.save(Category.builder()
                .name("Родитель").slug("parent-" + System.nanoTime()).build());
        Category child = categoryRepository.save(Category.builder()
                .name("Потомок").slug("child-" + System.nanoTime()).parent(parent).build());
        categoryService.refreshCategoryTree();
        Product p = save("Товар потомка", true, false, "100.00", true);
        p.setCategory(child);
        p.setDescription("Описание");
        productRepository.save(p);

        Element offer = offer(fetchXml(FEED_URL), p);

        assertThat(collectionIds(offer)).containsExactlyInAnyOrder(
                String.valueOf(child.getId()), String.valueOf(parent.getId()));
        List<String> tags = childTags(offer);
        assertThat(tags.indexOf("collectionId")).isGreaterThan(tags.indexOf("description"));
    }

    @Test
    @DisplayName("фид: оффер в категории без коллекции не получает collectionId")
    void feed_offerWithoutCollectionHasNoCollectionId() throws Exception {
        Category inactive = categoryRepository.save(Category.builder()
                .name("Скрытая").slug("hidden-" + System.nanoTime()).isActive(false).build());
        categoryService.refreshCategoryTree();
        Product p = save("Товар скрытой", true, false, "100.00", true);
        p.setCategory(inactive);
        productRepository.save(p);

        Element offer = offer(fetchXml(FEED_URL), p);

        assertThat(offer.getElementsByTagName("collectionId").getLength()).isZero();
    }

    @Test
    @DisplayName("фид: каждый collectionId оффера существует среди id коллекций блока collections")
    void feed_everyOfferCollectionIdExistsInCollections() throws Exception {
        Category parent = categoryRepository.save(Category.builder()
                .name("Родитель").slug("parent-" + System.nanoTime()).build());
        Category child = categoryRepository.save(Category.builder()
                .name("Потомок").slug("child-" + System.nanoTime()).parent(parent).build());
        categoryService.refreshCategoryTree();
        save("Кран", true, false, "100.00", true);
        Product p = save("Товар потомка", true, false, "100.00", true);
        p.setCategory(child);
        productRepository.save(p);

        Document doc = fetchXml(FEED_URL);

        Set<String> offerCollectionIds = new HashSet<>();
        NodeList offers = doc.getElementsByTagName("offer");
        for (int i = 0; i < offers.getLength(); i++) {
            offerCollectionIds.addAll(collectionIds((Element) offers.item(i)));
        }
        Set<String> collectionIds = new HashSet<>();
        NodeList collections = doc.getElementsByTagName("collection");
        for (int i = 0; i < collections.getLength(); i++) {
            collectionIds.add(((Element) collections.item(i)).getAttribute("id"));
        }
        assertThat(offerCollectionIds).isNotEmpty().isSubsetOf(collectionIds);
    }

    @Test
    @DisplayName("фид: порядок полей collection— url, picture, name, description; description до 81 символа")
    void feed_collectionChildOrderAndDescriptionLimit() throws Exception {
        Category longName = categoryRepository.save(Category.builder()
                .name("А".repeat(80)).slug("long-" + System.nanoTime()).build());
        categoryService.refreshCategoryTree();
        Product p = save("Товар длинной", true, false, "100.00", true);
        p.setCategory(longName);
        productRepository.save(p);

        Element collection = collection(fetchXml(FEED_URL), longName);

        assertThat(childTags(collection)).containsExactly("url", "picture", "name", "description");
        assertThat(child(collection, "description")).hasSize(81);
    }

    @Test
    @DisplayName("фид: без коллекций блока collections нет")
    void feed_noCollectionsBlockWhenEmpty() throws Exception {
        Document doc = fetchXml(FEED_URL);

        assertThat(doc.getElementsByTagName("collections").getLength()).isZero();
    }

    @Test
    @DisplayName("фид: категория без офферов в себе и потомках не попадает в collections")
    void feed_collectionSkipsEmptyCategory() throws Exception {
        Category empty = categoryRepository.save(Category.builder()
                .name("Пустая").slug("empty-" + System.nanoTime()).build());
        categoryService.refreshCategoryTree();
        save("Кран", true, false, "100.00", true);

        Document doc = fetchXml(FEED_URL);

        assertThat(collection(doc, empty)).isNull();
        assertThat(collection(doc, category)).isNotNull();
    }

    @Test
    @DisplayName("фид: родитель без своих товаров попадает в collections, picture от товара подкатегории")
    void feed_collectionForParentUsesChildProductPicture() throws Exception {
        Category parent = categoryRepository.save(Category.builder()
                .name("Родитель").slug("parent-" + System.nanoTime()).build());
        Category child = categoryRepository.save(Category.builder()
                .name("Потомок").slug("child-" + System.nanoTime()).parent(parent).build());
        categoryService.refreshCategoryTree();
        Product p = save("Товар потомка", true, false, "100.00", true);
        p.setCategory(child);
        productRepository.save(p);

        Document doc = fetchXml(FEED_URL);

        Element parentCollection = collection(doc, parent);
        assertThat(parentCollection).isNotNull();
        assertThat(child(parentCollection, "picture")).endsWith("k" + p.getId() + ".jpg");
        assertThat(collection(doc, child)).isNotNull();
    }

    @Test
    @DisplayName("фид: имя коллекции длиннее 56 символов обрезается")
    void feed_collectionNameTruncatedTo56() throws Exception {
        Category longName = categoryRepository.save(Category.builder()
                .name("А".repeat(80)).slug("long-" + System.nanoTime()).build());
        categoryService.refreshCategoryTree();
        Product p = save("Товар длинной", true, false, "100.00", true);
        p.setCategory(longName);
        productRepository.save(p);

        Element collection = collection(fetchXml(FEED_URL), longName);

        assertThat(child(collection, "name")).isEqualTo("А".repeat(56));
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
