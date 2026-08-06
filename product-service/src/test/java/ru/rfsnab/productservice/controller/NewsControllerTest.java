package ru.rfsnab.productservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.productservice.dto.NewsRequest;
import ru.rfsnab.productservice.model.News;
import ru.rfsnab.productservice.repository.NewsRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Новости: публичный и админский API")
class NewsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NewsRepository newsRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        newsRepository.deleteAll();
    }

    private News published(String title, String slug) {
        return newsRepository.save(News.builder()
                .title(title)
                .slug(slug)
                .contentHtml("<p>Текст</p>")
                .isPublished(true)
                .publishedAt(LocalDateTime.now())
                .build());
    }

    private News draft(String title, String slug) {
        return newsRepository.save(News.builder()
                .title(title)
                .slug(slug)
                .contentHtml("<p>Черновик</p>")
                .isPublished(false)
                .build());
    }

    @Nested
    @DisplayName("Публичный API")
    class PublicApi {

        @Test
        @DisplayName("список отдаётся без авторизации")
        void listIsPublic() throws Exception {
            published("Новость 1", "novost-1");

            mockMvc.perform(get("/api/v1/news"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("черновики в список НЕ попадают")
        void draftsAreHiddenFromList() throws Exception {
            published("Опубликованная", "opublikovannaya");
            draft("Черновик", "chernovik");

            mockMvc.perform(get("/api/v1/news"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].slug", is("opublikovannaya")));
        }

        @Test
        @DisplayName("черновик по прямой ссылке отдаёт 404")
        void draftBySlugReturns404() throws Exception {
            draft("Черновик", "chernovik");

            mockMvc.perform(get("/api/v1/news/chernovik"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("опубликованная новость доступна по slug")
        void publishedBySlugIsAvailable() throws Exception {
            published("Поступление", "postuplenie");

            mockMvc.perform(get("/api/v1/news/postuplenie"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title", is("Поступление")));
        }

        @Test
        @DisplayName("latest ограничивает количество")
        void latestRespectsSize() throws Exception {
            published("Первая", "pervaya");
            published("Вторая", "vtoraya");
            published("Третья", "tretya");

            mockMvc.perform(get("/api/v1/news/latest").param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        @DisplayName("ссылка на видео отдаётся вместе с embed-URL")
        void returnsVideoEmbedUrl() throws Exception {
            News news = published("С видео", "s-video");
            news.setVideoUrl("https://youtu.be/dQw4w9WgXcQ");
            newsRepository.save(news);

            mockMvc.perform(get("/api/v1/news/s-video"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.videoEmbedUrl", containsString("youtube-nocookie.com/embed/dQw4w9WgXcQ")));
        }

        @Test
        @DisplayName("несуществующий slug → 404")
        void unknownSlugReturns404() throws Exception {
            mockMvc.perform(get("/api/v1/news/net-takoy"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Админский API — доступ")
    class AdminAccess {

        @Test
        @DisplayName("без авторизации — 401")
        void anonymousIsRejected() throws Exception {
            mockMvc.perform(get("/api/v1/admin/news"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(authorities = "ROLE_USER")
        @DisplayName("обычный пользователь — 403")
        void plainUserIsForbidden() throws Exception {
            mockMvc.perform(get("/api/v1/admin/news"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(authorities = "ROLE_ADMIN")
        @DisplayName("ADMIN видит черновики")
        void adminSeesDrafts() throws Exception {
            draft("Черновик", "chernovik");

            mockMvc.perform(get("/api/v1/admin/news"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].isPublished", is(false)));
        }

        @Test
        @WithMockUser(authorities = "ROLE_MANAGER")
        @DisplayName("MANAGER тоже имеет доступ")
        void managerHasAccess() throws Exception {
            mockMvc.perform(get("/api/v1/admin/news"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(authorities = "ROLE_USER")
        @DisplayName("создание обычным пользователем запрещено")
        void plainUserCannotCreate() throws Exception {
            NewsRequest request = NewsRequest.builder()
                    .title("Попытка")
                    .contentHtml("<p>Текст</p>")
                    .build();

            mockMvc.perform(post("/api/v1/admin/news")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Админский API — CRUD")
    class AdminCrud {

        @Test
        @WithMockUser(authorities = "ROLE_ADMIN")
        @DisplayName("создание возвращает 201 и генерирует slug")
        void createsNews() throws Exception {
            NewsRequest request = NewsRequest.builder()
                    .title("Поступление спецодежды")
                    .contentHtml("<p>Текст новости</p>")
                    .isPublished(true)
                    .build();

            mockMvc.perform(post("/api/v1/admin/news")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.slug", not(emptyString())))
                    .andExpect(jsonPath("$.isPublished", is(true)));
        }

        @Test
        @WithMockUser(authorities = "ROLE_ADMIN")
        @DisplayName("опасный HTML вырезается при сохранении")
        void sanitizesHtmlOnCreate() throws Exception {
            NewsRequest request = NewsRequest.builder()
                    .title("Новость со скриптом")
                    .contentHtml("<p>Текст</p><script>alert('xss')</script>")
                    .build();

            mockMvc.perform(post("/api/v1/admin/news")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.contentHtml", not(containsString("script"))))
                    .andExpect(jsonPath("$.contentHtml", not(containsString("alert"))));

            assertThat(newsRepository.findAll().get(0).getContentHtml()).doesNotContain("script");
        }

        @Test
        @WithMockUser(authorities = "ROLE_ADMIN")
        @DisplayName("пустой заголовок → 400")
        void rejectsBlankTitle() throws Exception {
            NewsRequest request = NewsRequest.builder()
                    .title("")
                    .contentHtml("<p>Текст</p>")
                    .build();

            mockMvc.perform(post("/api/v1/admin/news")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(authorities = "ROLE_ADMIN")
        @DisplayName("обновление меняет заголовок")
        void updatesNews() throws Exception {
            News existing = draft("Старый", "staryy");
            NewsRequest request = NewsRequest.builder()
                    .title("Новый заголовок")
                    .contentHtml("<p>Обновлённый текст</p>")
                    .isPublished(true)
                    .build();

            mockMvc.perform(put("/api/v1/admin/news/" + existing.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title", is("Новый заголовок")))
                    .andExpect(jsonPath("$.slug", is("staryy")));
        }

        @Test
        @WithMockUser(authorities = "ROLE_ADMIN")
        @DisplayName("удаление возвращает 204")
        void deletesNews() throws Exception {
            News existing = published("На удаление", "na-udalenie");

            mockMvc.perform(delete("/api/v1/admin/news/" + existing.getId()))
                    .andExpect(status().isNoContent());

            assertThat(newsRepository.findById(existing.getId())).isEmpty();
        }

        @Test
        @WithMockUser(authorities = "ROLE_ADMIN")
        @DisplayName("удаление несуществующей → 404")
        void deleteMissingReturns404() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/news/9999"))
                    .andExpect(status().isNotFound());
        }
    }
}
