package ru.rfsnab.productservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.rfsnab.productservice.service.ProductFeedService;

/** Публичные XML: YML-фид для Яндекс Директа и sitemap. Литеральные пути приоритетнее /{id} в ProductController. */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductFeedController {

    private static final MediaType XML_UTF8 = MediaType.parseMediaType("application/xml;charset=UTF-8");

    private final ProductFeedService feedService;

    @GetMapping("/feed/yandex.yml")
    public ResponseEntity<String> yandexFeed() {
        return ResponseEntity.ok().contentType(XML_UTF8).body(feedService.buildYandexFeed());
    }

    @GetMapping("/sitemap.xml")
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.ok().contentType(XML_UTF8).body(feedService.buildSitemap());
    }
}
