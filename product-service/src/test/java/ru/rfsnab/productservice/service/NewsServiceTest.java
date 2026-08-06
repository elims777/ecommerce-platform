package ru.rfsnab.productservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import ru.rfsnab.productservice.dto.NewsAdminResponse;
import ru.rfsnab.productservice.dto.NewsRequest;
import ru.rfsnab.productservice.dto.NewsResponse;
import ru.rfsnab.productservice.exception.NewsNotFoundException;
import ru.rfsnab.productservice.model.News;
import ru.rfsnab.productservice.repository.NewsRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NewsService")
class NewsServiceTest {

    @Mock
    private NewsRepository newsRepository;

    @Mock
    private SlugGeneratorService slugGeneratorService;

    @Mock
    private NewsHtmlSanitizer htmlSanitizer;

    @Mock
    private VideoEmbedResolver videoEmbedResolver;

    @InjectMocks
    private NewsService newsService;

    private News news;

    @BeforeEach
    void setUp() {
        news = News.builder()
                .id(1L)
                .title("Поступление спецодежды")
                .slug("postuplenie-specodezhdy")
                .contentHtml("<p>Текст</p>")
                .isPublished(true)
                .publishedAt(LocalDateTime.of(2026, 8, 6, 12, 0))
                .build();
    }

    private NewsRequest request(boolean published) {
        return NewsRequest.builder()
                .title("Поступление спецодежды")
                .contentHtml("<p>Текст</p>")
                .isPublished(published)
                .build();
    }

    @Nested
    @DisplayName("Создание")
    class Create {

        @Test
        @DisplayName("HTML проходит через санитайзер перед сохранением")
        void sanitizesHtmlBeforeSave() {
            when(slugGeneratorService.generateSlug(anyString())).thenReturn("postuplenie-specodezhdy");
            when(newsRepository.existsBySlug(anyString())).thenReturn(false);
            when(htmlSanitizer.sanitize("<p>Текст</p>")).thenReturn("<p>Чисто</p>");
            when(newsRepository.save(any(News.class))).thenAnswer(i -> i.getArgument(0));

            newsService.create(request(false));

            ArgumentCaptor<News> captor = ArgumentCaptor.forClass(News.class);
            verify(newsRepository).save(captor.capture());
            assertThat(captor.getValue().getContentHtml()).isEqualTo("<p>Чисто</p>");
        }

        @Test
        @DisplayName("занятый slug получает числовой суффикс")
        void appendsSuffixWhenSlugTaken() {
            when(slugGeneratorService.generateSlug(anyString())).thenReturn("novost");
            when(newsRepository.existsBySlug("novost")).thenReturn(true);
            when(newsRepository.existsBySlug("novost-2")).thenReturn(false);
            when(htmlSanitizer.sanitize(anyString())).thenReturn("<p>Текст</p>");
            when(newsRepository.save(any(News.class))).thenAnswer(i -> i.getArgument(0));

            NewsAdminResponse result = newsService.create(request(false));

            assertThat(result.getSlug()).isEqualTo("novost-2");
        }

        @Test
        @DisplayName("публикация проставляет publishedAt")
        void setsPublishedAtWhenPublished() {
            when(slugGeneratorService.generateSlug(anyString())).thenReturn("novost");
            when(newsRepository.existsBySlug(anyString())).thenReturn(false);
            when(htmlSanitizer.sanitize(anyString())).thenReturn("<p>Текст</p>");
            when(newsRepository.save(any(News.class))).thenAnswer(i -> i.getArgument(0));

            NewsAdminResponse result = newsService.create(request(true));

            assertThat(result.getIsPublished()).isTrue();
            assertThat(result.getPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("черновик создаётся без publishedAt")
        void draftHasNoPublishedAt() {
            when(slugGeneratorService.generateSlug(anyString())).thenReturn("novost");
            when(newsRepository.existsBySlug(anyString())).thenReturn(false);
            when(htmlSanitizer.sanitize(anyString())).thenReturn("<p>Текст</p>");
            when(newsRepository.save(any(News.class))).thenAnswer(i -> i.getArgument(0));

            NewsAdminResponse result = newsService.create(request(false));

            assertThat(result.getIsPublished()).isFalse();
            assertThat(result.getPublishedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("Обновление")
    class Update {

        @Test
        @DisplayName("slug не меняется при смене заголовка — старые ссылки должны работать")
        void keepsSlugOnTitleChange() {
            when(newsRepository.findById(1L)).thenReturn(Optional.of(news));
            when(htmlSanitizer.sanitize(anyString())).thenReturn("<p>Текст</p>");
            when(newsRepository.save(any(News.class))).thenAnswer(i -> i.getArgument(0));

            NewsRequest changed = NewsRequest.builder()
                    .title("Совсем другой заголовок")
                    .contentHtml("<p>Текст</p>")
                    .isPublished(true)
                    .build();

            NewsAdminResponse result = newsService.update(1L, changed);

            assertThat(result.getSlug()).isEqualTo("postuplenie-specodezhdy");
            assertThat(result.getTitle()).isEqualTo("Совсем другой заголовок");
            verify(slugGeneratorService, never()).generateSlug(anyString());
        }

        @Test
        @DisplayName("снятие с публикации не сбрасывает дату первой публикации")
        void unpublishKeepsPublishedAt() {
            LocalDateTime originalDate = news.getPublishedAt();
            when(newsRepository.findById(1L)).thenReturn(Optional.of(news));
            when(htmlSanitizer.sanitize(anyString())).thenReturn("<p>Текст</p>");
            when(newsRepository.save(any(News.class))).thenAnswer(i -> i.getArgument(0));

            NewsAdminResponse result = newsService.update(1L, request(false));

            assertThat(result.getIsPublished()).isFalse();
            assertThat(result.getPublishedAt()).isEqualTo(originalDate);
        }

        @Test
        @DisplayName("несуществующий id → NewsNotFoundException")
        void throwsWhenNotFound() {
            when(newsRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> newsService.update(99L, request(true)))
                    .isInstanceOf(NewsNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    @Nested
    @DisplayName("Чтение публичной части")
    class PublicRead {

        @Test
        @DisplayName("по slug отдаётся только опубликованная новость")
        void getBySlugReturnsPublished() {
            when(newsRepository.findBySlugAndIsPublishedTrue("postuplenie-specodezhdy"))
                    .thenReturn(Optional.of(news));

            NewsResponse result = newsService.getPublishedBySlug("postuplenie-specodezhdy");

            assertThat(result.getTitle()).isEqualTo("Поступление спецодежды");
        }

        @Test
        @DisplayName("черновик по slug недоступен")
        void draftIsNotFoundBySlug() {
            when(newsRepository.findBySlugAndIsPublishedTrue("chernovik")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> newsService.getPublishedBySlug("chernovik"))
                    .isInstanceOf(NewsNotFoundException.class);
        }

        @Test
        @DisplayName("ссылка на видео превращается в embed-URL")
        void resolvesVideoEmbedUrl() {
            news.setVideoUrl("https://youtu.be/dQw4w9WgXcQ");
            when(newsRepository.findBySlugAndIsPublishedTrue(anyString())).thenReturn(Optional.of(news));
            when(videoEmbedResolver.resolveEmbedUrl("https://youtu.be/dQw4w9WgXcQ"))
                    .thenReturn("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ");

            NewsResponse result = newsService.getPublishedBySlug("postuplenie-specodezhdy");

            assertThat(result.getVideoEmbedUrl()).isEqualTo("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ");
        }

        @Test
        @DisplayName("latest запрашивает ровно нужное количество")
        void getLatestLimitsSize() {
            when(newsRepository.findByIsPublishedTrueOrderByPublishedAtDesc(any(Pageable.class)))
                    .thenReturn(List.of(news));

            List<NewsResponse> result = newsService.getLatest(5);

            assertThat(result).hasSize(1);
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(newsRepository).findByIsPublishedTrueOrderByPublishedAtDesc(captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        }

        @Test
        @DisplayName("список опубликованных отдаётся постранично")
        void getPublishedReturnsPage() {
            Page<News> page = new PageImpl<>(List.of(news));
            when(newsRepository.findByIsPublishedTrue(any(Pageable.class))).thenReturn(page);

            Page<NewsResponse> result = newsService.getPublished(PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getSlug()).isEqualTo("postuplenie-specodezhdy");
        }
    }

    @Nested
    @DisplayName("Удаление")
    class Delete {

        @Test
        @DisplayName("существующая новость удаляется")
        void deletesExisting() {
            when(newsRepository.findById(1L)).thenReturn(Optional.of(news));

            newsService.delete(1L);

            verify(newsRepository).delete(news);
        }

        @Test
        @DisplayName("несуществующая → NewsNotFoundException")
        void throwsWhenMissing() {
            when(newsRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> newsService.delete(99L))
                    .isInstanceOf(NewsNotFoundException.class);
            verify(newsRepository, never()).delete(any());
        }
    }
}
