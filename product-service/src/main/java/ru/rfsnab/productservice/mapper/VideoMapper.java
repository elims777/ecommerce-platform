package ru.rfsnab.productservice.mapper;

import ru.rfsnab.productservice.dto.ProductVideoResponse;
import ru.rfsnab.productservice.model.ProductVideo;

public class VideoMapper {

    public static ProductVideoResponse mapToResponse(ProductVideo video){
        return ProductVideoResponse.builder()
                .id(video.getId())
                .videoUrl(video.getVideoUrl())
                .provider(video.getProvider())
                .thumbnailUrl(video.getThumbnailUrl())
                .durationSeconds(video.getDurationSeconds())
                .isPrimary(video.getIsPrimary())
                .displayOrder(video.getDisplayOrder())
                .title(video.getTitle())
                .build();
    }
}
