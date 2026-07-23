package ru.rfsnab.productservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "price_list_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceListRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "client_type", nullable = false, length = 10)
    private String clientType;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "category_ids", nullable = false, columnDefinition = "bigint[]")
    private List<Long> categoryIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PriceListStatus status;

    @Column(name = "file_key", length = 500)
    private String fileKey;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
