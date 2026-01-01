package ru.rfsnab.productservice.mapper;

import ru.rfsnab.productservice.dto.ProductVideoRequest;
import ru.rfsnab.productservice.dto.ProductVideoResponse;
import ru.rfsnab.productservice.model.ProductVideo;

public class VideoMapper {

    public static ProductVideoResponse mapToResponse(ProductVideo video){
        return ProductVideoResponse.builder()
                .id(video.getId())
                .videoUrl(video.getVideoUrl())
                .videoType(video.getVideoType())
                .provider(video.getProvider())
                .thumbnailUrl(video.getThumbnailUrl())
                .durationSeconds(video.getDurationSeconds())
                .isPrimary(video.getIsPrimary())
                .displayOrder(video.getDisplayOrder())
                .title(video.getTitle())
                .build();
    }

    public static ProductVideo toEntity(ProductVideoRequest request) {
        return ProductVideo.builder()
                .videoUrl(request.getVideoUrl())
                .videoType(request.getVideoType())
                .provider(request.getProvider())
                .thumbnailUrl(request.getThumbnailUrl())
                .durationSeconds(request.getDurationSeconds())
                .title(request.getTitle())
                .build();
    }
}
