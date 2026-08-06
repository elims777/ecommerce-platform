package ru.rfsnab.productservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.productservice.dto.NewsResponse;
import ru.rfsnab.productservice.service.NewsService;

import java.util.List;

/** Новости для публичной части сайта. Черновики недоступны. */
@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    @GetMapping
    public ResponseEntity<Page<NewsResponse>> getNews(
            @PageableDefault(size = 10, sort = "publishedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(newsService.getPublished(pageable));
    }

    /** Последние новости для блока на главной. */
    @GetMapping("/latest")
    public ResponseEntity<List<NewsResponse>> getLatest(@RequestParam(defaultValue = "5") int size) {
        return ResponseEntity.ok(newsService.getLatest(size));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<NewsResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(newsService.getPublishedBySlug(slug));
    }
}
