package ru.rfsnab.productservice.mapper;

import org.springframework.data.domain.Page;
import ru.rfsnab.productservice.dto.ProductDocumentDto;
import ru.rfsnab.productservice.dto.ProductRequest;
import ru.rfsnab.productservice.dto.ProductResponse;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.ProductDocument;
import ru.rfsnab.productservice.service.ProductAttributeExclusions;
import ru.rfsnab.productservice.service.SalePriceCalculator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ProductMapper {

    public static Product mapToEntity(ProductRequest productRequest) {
        return Product.builder()
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .shortDescription(productRequest.getShortDescription())
                .material(productRequest.getMaterial())
                .price(productRequest.getPrice())
                .wholesalePrice(productRequest.getWholesalePrice())
                .stockQuantity(productRequest.getStockQuantity())
                .isActive(productRequest.getIsActive())
                .isFeatured(productRequest.getIsFeatured())
                .isSale(productRequest.getIsSale())
                .saleMarkupPercent(productRequest.getSaleMarkupPercent())
                .externalId(productRequest.getExternalId())
                .sku(productRequest.getSku())
                .externalCode(productRequest.getExternalCode())
                .unitOfMeasure(productRequest.getUnitOfMeasure())
                .vatRate(productRequest.getVatRate())
                .category(productRequest.getCategoryId() != null
                        ? Category.builder().id(productRequest.getCategoryId()).build()
                        : null)
                .build();
    }

    public static ProductResponse mapToResponse(Product product) {
        return mapToResponse(product, List.of(), null);
    }

    public static ProductResponse mapToResponse(Product product, List<Product> children) {
        return mapToResponse(product, children, null);
    }

    /**
     * @param categoryMarkup акционный процент категории товара (с учётом наследования),
     *                       null — категория не акционная. Метка на самом товаре его перебивает.
     */
    public static ProductResponse mapWithSale(Product product, BigDecimal categoryMarkup) {
        return mapToResponse(product, List.of(), categoryMarkup);
    }

    /**
     * Для админки: акция видна во флагах isSale/saleMarkupPercent, но цена остаётся БАЗОВОЙ.
     * Подменять цену здесь нельзя — админ редактирует именно её, и акционная записалась бы как базовая.
     */
    public static ProductResponse mapForAdmin(Product product, List<Product> children, BigDecimal categoryMarkup) {
        ProductResponse response = mapToResponse(product, List.of(), null);
        // mapToResponse применяет собственную метку товара к цене даже без процента категории — возвращаем базовую
        response.setPrice(product.getPrice());
        response.setWholesalePrice(product.getWholesalePrice());
        response.setOldPrice(null);
        response.setOldWholesalePrice(null);

        BigDecimal markup = SalePriceCalculator.resolveMarkup(product, categoryMarkup);
        response.setIsSale(markup != null);
        response.setSaleMarkupPercent(markup);

        // Варианты лежат в той же категории — процент им достаётся тот же, цены тоже базовые
        response.setChildren(children.stream()
                .map(child -> mapForAdmin(child, List.of(), categoryMarkup))
                .collect(Collectors.toList()));
        return response;
    }

    public static ProductResponse mapToResponse(Product product, List<Product> children, BigDecimal categoryMarkup) {
        // Варианты лежат в той же категории, что и родитель, — процент им достаётся тот же
        List<ProductResponse> childResponses = children.stream()
                .map(child -> mapToResponse(child, List.of(), categoryMarkup))
                .collect(Collectors.toList());

        BigDecimal markup = SalePriceCalculator.resolveMarkup(product, categoryMarkup);
        BigDecimal salePrice = SalePriceCalculator.apply(product.getPrice(), markup);
        BigDecimal saleWholesalePrice = SalePriceCalculator.apply(product.getWholesalePrice(), markup);

        ProductResponse.ProductResponseBuilder builder = ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .importDescription(product.getImportDescription())
                .shortDescription(product.getShortDescription())
                .material(product.getMaterial())
                .barcode(product.getBarcode())
                .countryOfOrigin(product.getCountryOfOrigin())
                .price(salePrice)
                .wholesalePrice(saleWholesalePrice)
                .isSale(markup != null)
                .saleMarkupPercent(markup)
                .ownSale(Boolean.TRUE.equals(product.getIsSale()))
                .ownSaleMarkupPercent(product.getSaleMarkupPercent())
                .stockQuantity(product.getStockQuantity())
                .isActive(product.getIsActive())
                .isFeatured(product.getIsFeatured())
                .externalId(product.getExternalId())
                .sku(product.getSku())
                .externalCode(product.getExternalCode())
                .unitOfMeasure(product.getUnitOfMeasure())
                .vatRate(product.getVatRate())
                .isVariantChild(product.getIsVariantChild())
                .parentProductId(product.getParentProductId())
                .displayOrder(product.getDisplayOrder())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .source(product.getSource())
                .children(childResponses)
                .images(product.getImages().stream().map(ImageMapper::mapToResponse).toList())
                .videos(product.getVideos().stream().map(VideoMapper::mapToResponse).toList())
                .attributes(product.getAttributes().stream()
                        .filter(a -> !ProductAttributeExclusions.NAMES.contains(a.getAttributeName()))
                        .map(AttributeMapper::mapToResponse).toList())
                .documents(product.getDocuments().stream().map(ProductMapper::mapDocumentToDto).toList());

        // Старую цену отдаём только у скидок: при наценке зачёркнутая цена оказалась бы НИЖЕ текущей
        if (markup != null && markup.signum() < 0) {
            if (salePrice != null && salePrice.compareTo(product.getPrice()) != 0) {
                builder.oldPrice(product.getPrice());
            }
            if (saleWholesalePrice != null && saleWholesalePrice.compareTo(product.getWholesalePrice()) != 0) {
                builder.oldWholesalePrice(product.getWholesalePrice());
            }
        }

        if (product.getCategory() != null) {
            builder.categoryId(product.getCategory().getId());
            builder.categoryName(product.getCategory().getName());
            builder.categoryExternalId(product.getCategory().getExternalId());
        }

        return builder.build();
    }

    public static Page<ProductResponse> mapPageWithHasVariants(Page<Product> page, Set<Long> parentIdsWithChildren) {
        return mapPageWithHasVariants(page, parentIdsWithChildren, Map.of());
    }

    /**
     * @param categoryMarkups карта "категория -> акционный процент" для товаров страницы
     */
    public static Page<ProductResponse> mapPageWithHasVariants(Page<Product> page,
                                                               Set<Long> parentIdsWithChildren,
                                                               Map<Long, BigDecimal> categoryMarkups) {
        return page.map(p -> {
            BigDecimal categoryMarkup = p.getCategory() != null
                    ? categoryMarkups.get(p.getCategory().getId())
                    : null;
            ProductResponse response = mapWithSale(p, categoryMarkup);
            response.setHasVariants(parentIdsWithChildren.contains(p.getId()));
            return response;
        });
    }

    public static ProductDocumentDto mapDocumentToDto(ProductDocument document) {
        return ProductDocumentDto.builder()
                .id(document.getId())
                .name(document.getName())
                .url(document.getUrl())
                .contentType(document.getContentType())
                .displayOrder(document.getDisplayOrder())
                .build();
    }
}
