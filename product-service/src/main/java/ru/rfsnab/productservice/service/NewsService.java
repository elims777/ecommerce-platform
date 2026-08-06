package ru.rfsnab.productservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.productservice.dto.NewsAdminResponse;
import ru.rfsnab.productservice.dto.NewsRequest;
import ru.rfsnab.productservice.dto.NewsResponse;
import ru.rfsnab.productservice.exception.NewsNotFoundException;
import ru.rfsnab.productservice.mapper.NewsMapper;
import ru.rfsnab.productservice.model.News;
import ru.rfsnab.productservice.repository.NewsRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {

    private final NewsRepository newsRepository;
    private final SlugGeneratorService slugGeneratorService;
    private final NewsHtmlSanitizer htmlSanitizer;
    private final VideoEmbedResolver videoEmbedResolver;

    @Transactional(readOnly = true)
    public Page<NewsResponse> getPublished(Pageable pageable) {
        return newsRepository.findByIsPublishedTrue(pageable).map(this::toPublicResponse);
    }

    @Transactional(readOnly = true)
    public NewsResponse getPublishedBySlug(String slug) {
        News news = newsRepository.findBySlugAndIsPublishedTrue(slug)
                .orElseThrow(() -> new NewsNotFoundException(slug));
        return toPublicResponse(news);
    }

    /** Последние опубликованные — для блока на главной. */
    @Transactional(readOnly = true)
    public List<NewsResponse> getLatest(int size) {
        return newsRepository.findByIsPublishedTrueOrderByPublishedAtDesc(PageRequest.of(0, size))
                .stream()
                .map(this::toPublicResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<NewsAdminResponse> getAllForAdmin(Pageable pageable) {
        return newsRepository.findAll(pageable).map(NewsMapper::mapToAdminResponse);
    }

    @Transactional(readOnly = true)
    public NewsAdminResponse getByIdForAdmin(Long id) {
        return NewsMapper.mapToAdminResponse(findById(id));
    }

    @Transactional
    public NewsAdminResponse create(NewsRequest request) {
        News news = News.builder()
                .slug(generateUniqueSlug(request.getTitle()))
                .build();

        NewsMapper.applyRequest(news, request, htmlSanitizer.sanitize(request.getContentHtml()));
        applyPublicationState(news, Boolean.TRUE.equals(request.getIsPublished()));

        News saved = newsRepository.save(news);
        log.info("Создана новость id={}, slug={}", saved.getId(), saved.getSlug());
        return NewsMapper.mapToAdminResponse(saved);
    }

    @Transactional
    public NewsAdminResponse update(Long id, NewsRequest request) {
        News news = findById(id);

        // Slug держится за первоначальным заголовком: смена ломала бы уже разосланные ссылки
        NewsMapper.applyRequest(news, request, htmlSanitizer.sanitize(request.getContentHtml()));
        applyPublicationState(news, Boolean.TRUE.equals(request.getIsPublished()));

        News saved = newsRepository.save(news);
        log.info("Обновлена новость id={}", saved.getId());
        return NewsMapper.mapToAdminResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        News news = findById(id);
        newsRepository.delete(news);
        log.info("Удалена новость id={}", id);
    }

    private News findById(Long id) {
        return newsRepository.findById(id).orElseThrow(() -> new NewsNotFoundException(id));
    }

    /** Дата публикации ставится один раз — при первой публикации, и снятием с публикации не сбрасывается. */
    private void applyPublicationState(News news, boolean shouldBePublished) {
        news.setIsPublished(shouldBePublished);
        if (shouldBePublished && news.getPublishedAt() == null) {
            news.setPublishedAt(LocalDateTime.now());
        }
    }

    private String generateUniqueSlug(String title) {
        String baseSlug = slugGeneratorService.generateSlug(title);
        String candidate = baseSlug;
        int counter = 1;
        while (newsRepository.existsBySlug(candidate)) {
            counter++;
            candidate = baseSlug + "-" + counter;
        }
        return candidate;
    }

    private NewsResponse toPublicResponse(News news) {
        return NewsMapper.mapToResponse(news, videoEmbedResolver.resolveEmbedUrl(news.getVideoUrl()));
    }
}
