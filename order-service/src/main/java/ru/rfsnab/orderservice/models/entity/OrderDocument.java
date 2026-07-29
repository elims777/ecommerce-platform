package ru.rfsnab.orderservice.models.entity;

import jakarta.persistence.*;
import lombok.*;
import ru.rfsnab.orderservice.models.entity.enums.OrderDocumentType;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderDocumentType type;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "file_key", nullable = false, length = 512)
    private String fileKey;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "uploaded_by_user_id")
    private Long uploadedByUserId;
}
