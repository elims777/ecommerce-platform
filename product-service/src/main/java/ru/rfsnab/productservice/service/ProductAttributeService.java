package ru.rfsnab.productservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.productservice.exception.BusinessException;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.ProductAttribute;
import ru.rfsnab.productservice.repository.ProductAttributeRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductAttributeService {

    private final ProductAttributeRepository attributeRepository;
    private final ProductService productService;

    /**
     * Добавить атрибут к товару
     * @param productId товара
     * @param attribute атрибут
     * @return ProductAttribute
     */
    @Transactional
    public ProductAttribute addAttribute(Long productId, ProductAttribute attribute){
        Product product = productService.getProductById(productId);

        attribute.setProduct(product);
        return attributeRepository.save(attribute);
    }

    /**
     * Обновить атрибут
     * @param id атрибута
     * @param updatedAttribute обновленные данные
     * @return ProductAttribute
     */
    @Transactional
    public ProductAttribute updateAttribute(Long id, ProductAttribute updatedAttribute){
        ProductAttribute existing = attributeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Аттрибут не найден id=" + id));

        if(     updatedAttribute!=null &&
                updatedAttribute.getAttributeName()!=null &&
                updatedAttribute.getAttributeValue()!=null){
            existing.setAttributeName(updatedAttribute.getAttributeName());
            existing.setAttributeValue(updatedAttribute.getAttributeValue());
        } else {
            throw new BusinessException("Обновляемые данные не должны быть пустыми");
        }
        return attributeRepository.save(existing);
    }

    /**
     * Удаление аттрибута
     * @param id аттрибута
     */
    @Transactional
    public void deleteAttribute(Long id){
        if(!attributeRepository.existsById(id)){
            throw new BusinessException("Аттрибут не найден id="+id);
        }
        attributeRepository.deleteById(id);
    }

    /**
     * Удалить все аттрибуты товара
     * @param productId товара
     */
    @Transactional
    public void deleteAllAttributes(Long productId){
        productService.getProductById(productId);
        attributeRepository.deleteAllByProduct(productId);
    }

    /**
     * Получить все атрибуты товара
     * @param productId товара
     * @return List<ProductAttribute>
     */
    public List<ProductAttribute> getProductAttributes(Long productId) {
        productService.getProductById(productId); // проверка существования товара
        return attributeRepository.findAllByProduct(productId);
    }

    /**
     * Получить атрибуты по имени
     * @param name имя атрибута
     * @return List<ProductAttribute>
     */
    public List<ProductAttribute> getAttributesByName(String name) {
        return attributeRepository.findByName(name);
    }

    /**
     * Получить атрибут по ID
     * @param attributeId атрибута
     * @return ProductAttribute
     */
    public ProductAttribute getAttributeById(Long attributeId) {
        return attributeRepository.findById(attributeId)
                .orElseThrow(() -> new BusinessException("Атрибут не найден"));
    }
}
