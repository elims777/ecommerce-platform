package ru.rfsnab.productservice.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(length = 100)
    private String contentType;

    @Column(nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;
}
