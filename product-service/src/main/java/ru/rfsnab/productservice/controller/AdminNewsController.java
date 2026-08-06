package ru.rfsnab.productservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.rfsnab.productservice.dto.NewsAdminResponse;
import ru.rfsnab.productservice.dto.NewsRequest;
import ru.rfsnab.productservice.service.NewsImageService;
import ru.rfsnab.productservice.service.NewsService;

import java.util.Map;

/** Управление новостями. Доступ — ROLE_ADMIN и ROLE_MANAGER (правила в SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/news")
@RequiredArgsConstructor
public class AdminNewsController {

    private final NewsService newsService;
    private final NewsImageService newsImageService;

    @GetMapping
    public ResponseEntity<Page<NewsAdminResponse>> getAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(newsService.getAllForAdmin(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NewsAdminResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(newsService.getByIdForAdmin(id));
    }

    @PostMapping
    public ResponseEntity<NewsAdminResponse> create(@Valid @RequestBody NewsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(newsService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NewsAdminResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody NewsRequest request) {
        return ResponseEntity.ok(newsService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        newsService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Загрузка картинки (обложка либо вставка в текст). Возвращает URL в S3. */
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        String url = newsImageService.upload(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", url));
    }
}
