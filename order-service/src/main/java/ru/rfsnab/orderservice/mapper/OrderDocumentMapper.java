package ru.rfsnab.orderservice.mapper;

import ru.rfsnab.orderservice.models.dto.order.OrderDocumentDto;
import ru.rfsnab.orderservice.models.entity.OrderDocument;

public class OrderDocumentMapper {

    public static OrderDocumentDto toDto(OrderDocument document) {
        return new OrderDocumentDto(
                document.getId(),
                document.getType().name(),
                document.getType().getDisplayName(),
                document.getOriginalFileName(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getUploadedAt()
        );
    }
}
