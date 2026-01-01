package ru.rfsnab.productservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.rfsnab.productservice.dto.ProductVideoRequest;
import ru.rfsnab.productservice.dto.ProductVideoResponse;
import ru.rfsnab.productservice.mapper.VideoMapper;
import ru.rfsnab.productservice.model.ProductVideo;
import ru.rfsnab.productservice.service.ProductVideoService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products/{productId}/videos")
@RequiredArgsConstructor
public class ProductVideoController {
    private final ProductVideoService videoService;

    /**
     * Получить все видео товара
     */
    @GetMapping
    public ResponseEntity<List<ProductVideoResponse>> getAllVideos(@PathVariable Long productId){
        List<ProductVideo> productVideos = videoService.getProductVideos(productId);
        return ResponseEntity.ok(productVideos.stream().map(VideoMapper::mapToResponse).toList());
    }

    /**
     * Получить главное видео товара
     */
    @GetMapping("/primary")
    public ResponseEntity<ProductVideoResponse> getPrimaryVideo(@PathVariable Long productId){
        ProductVideo primaryVideo = videoService.getPrimaryVideo(productId);
        return ResponseEntity.ok(VideoMapper.mapToResponse(primaryVideo));
    }

    /**
     * Добавить видео к товару
     */
    @PostMapping
    public ResponseEntity<ProductVideoResponse> addVideo(
            @PathVariable Long productId,
            @Valid @RequestBody ProductVideoRequest videoRequest
            ){
        ProductVideo video = videoService.addVideo(productId, VideoMapper.toEntity(videoRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(VideoMapper.mapToResponse(video));
    }

    /**
     * Обновить информацию о видео
     */
    @PutMapping("/{videoId}")
    public ResponseEntity<ProductVideoResponse> updateVideo(
            @PathVariable Long productId,
            @PathVariable Long videoId,
            @Valid @RequestBody ProductVideoRequest videoRequest
            ){
        ProductVideo video = videoService.updateVideoInfo(videoId, VideoMapper.toEntity(videoRequest));
        return ResponseEntity.ok(VideoMapper.mapToResponse(video));
    }

    /**
     * Удалить видео
     */
    @DeleteMapping("/{videoId}")
    public ResponseEntity<ProductVideoResponse> deleteVideo(
            @PathVariable Long productId,
            @PathVariable Long videoId
            ){
        videoService.deleteVideo(videoId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Установить главное видео
     */
    @PutMapping("/{videoId}/primary")
    public ResponseEntity<ProductVideoResponse> setPrimaryVideo(
            @PathVariable Long productId,
            @PathVariable Long videoId
            ){
        ProductVideo video = videoService.setPrimaryVideo(videoId);
        return ResponseEntity.ok(VideoMapper.mapToResponse(video));
    }

    /**
     * Изменить порядок отображения
     */
    @PutMapping("/{videoId}/order")
    public ResponseEntity<ProductVideoResponse> updateVideoOrder(
            @PathVariable Long productId,
            @PathVariable Long videoId,
            @RequestParam Integer order
            ){
        ProductVideo video = videoService.updateVideoOrder(videoId, order);
        return ResponseEntity.ok(VideoMapper.mapToResponse(video));
    }
}
