package ru.rfsnab.productservice.spec;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.ProductAttribute;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ProductSpecifications {

    private ProductSpecifications() {}

    /**
     * Активные родители категории (поддерева), у которых для КАЖДОГО выбранного свойства
     * есть атрибут с одним из выбранных значений (AND между свойствами, OR внутри свойства).
     */
    public static Specification<Product> categoryWithAttributes(
            List<Long> categoryIds, Map<String, List<String>> attrFilters) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("category").get("id").in(categoryIds));
            predicates.add(cb.isTrue(root.get("isActive")));
            predicates.add(cb.isFalse(root.get("isVariantChild")));

            for (Map.Entry<String, List<String>> e : attrFilters.entrySet()) {
                Subquery<Long> sub = query.subquery(Long.class);
                var attr = sub.from(ProductAttribute.class);
                sub.select(attr.get("product").get("id"));
                sub.where(
                        cb.equal(attr.get("product").get("id"), root.get("id")),
                        cb.equal(attr.get("attributeName"), e.getKey()),
                        attr.get("attributeValue").in(e.getValue())
                );
                predicates.add(cb.exists(sub));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
