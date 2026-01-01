package ru.rfsnab.productservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.productservice.exception.BusinessException;
import ru.rfsnab.productservice.exception.ProductNotFoundException;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.ProductVideo;
import ru.rfsnab.productservice.repository.ProductVideoRepository;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductVideoService {

    private final ProductVideoRepository videoRepository;
    private final ProductService productService;

    /**
     * Добавить видео к товару
     * @param productId товара
     * @param video объект
     * @return ProductVideo
     */
    public ProductVideo addVideo(Long productId, ProductVideo video){
        log.info("Adding video to product id={}", productId);

        Product product = productService.getProductById(productId);

        //Определяем displayOrder (последний+1)
        List<ProductVideo> existingVideos = videoRepository.findAllByProduct(productId);
        int nextOrder = existingVideos.isEmpty() ? 1 : existingVideos.get(existingVideos.size()-1).getDisplayOrder()+1;

        video.setProduct(product);
        video.setDisplayOrder(nextOrder);

        if(existingVideos.isEmpty()){
            video.setIsPrimary(true);
        }

        ProductVideo saved = videoRepository.save(video);
        log.info("Video added to product id={}, video id={}", productId, saved.getId());

        return saved;
    }

    /**
     * Удаление видео
     * @param id видео
     */
    @Transactional
    public void deleteVideo(Long id){
        log.info("Deleting video id={}", id);

        if(!videoRepository.existsById(id)){
            throw new BusinessException("Видео не найдено");
        }
        videoRepository.deleteById(id);
        log.info("Video deleted id={}",id);
    }

    /**
     * Устанавливаем главное видео товара
     * @param id видео
     * @return ProductVideo
     */
    @Transactional
    public ProductVideo setPrimaryVideo(Long id){
        log.info("Set isPrimary for video id={}", id);

        ProductVideo video = videoRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Видео не найдено"));

        Long productId = video.getProduct().getId();

        // Сбрасываем isPrimary для всех видео товара
        videoRepository.resetPrimaryForProduct(productId);

        // Устанавливаем главное
        video.setIsPrimary(true);
        ProductVideo saved = videoRepository.save(video);
        log.info("Primary video set for product id={}, video id={}", productId, id);

        return saved;
    }

    /**
     * Изменить порядок отображения видео
     * @param videoId видео
     * @param newOrder позиция
     * @return ProductVideo
     */
    @Transactional
    public ProductVideo updateVideoOrder(Long videoId, Integer newOrder){
        log.info("Updating video order id={}, newOrder={}", videoId, newOrder);

        if(newOrder<1){
            throw new BusinessException("Порядок должен быть >= 1");
        }

        ProductVideo video = videoRepository.findById(videoId)
                .orElseThrow(() -> new BusinessException("Видео не найдено"));

        video.setDisplayOrder(newOrder);
        ProductVideo saved = videoRepository.save(video);

        log.info("Video order updated id={}", videoId);

        return saved;
    }

    /**
     * Обновить информацию о видео
     * @param id видео
     * @param updateVideo обновленное видео
     * @return ProductVideo
     */
    @Transactional
    public ProductVideo updateVideoInfo(Long id, ProductVideo updateVideo){
        log.info("Updating video info id={}", id);

        ProductVideo existing = videoRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Видео не найдено"));

        existing.setProduct(updateVideo.getProduct());
        existing.setVideoUrl(updateVideo.getVideoUrl());
        existing.setTitle(updateVideo.getTitle());
        existing.setVideoType(updateVideo.getVideoType());
        existing.setDisplayOrder(updateVideo.getDisplayOrder());
        existing.setIsPrimary(updateVideo.getIsPrimary());
        existing.setDurationSeconds(updateVideo.getDurationSeconds());
        existing.setProvider(updateVideo.getProvider());
        existing.setThumbnailUrl(updateVideo.getThumbnailUrl());
        existing.setProvider(updateVideo.getProvider());

        ProductVideo saved = videoRepository.save(existing);
        log.info("Video info updated id={}", id);

        return saved;
    }

    /**
     * Получить все видео товара
     * @param productId товара
     * @return List<ProductVideo>
     */
    public List<ProductVideo> getProductVideos(Long productId){
        productService.getProductById(productId);
        return videoRepository.findAllByProduct(productId);
    }

    /**
     * Получить главное видео товара
     * @param productId товара
     * @return Optional<ProductVideo>
     */
    public ProductVideo getPrimaryVideo(Long productId){
        productService.getProductById(productId);
        return  videoRepository.findPrimaryByProduct(productId)
                .orElseThrow(() -> new BusinessException("Видео не найдено"));
    }

    /**
     * Получение видео по id
     * @param id видео
     * @return ProductVideo
     */
    public ProductVideo getVideoById(long id){
        return videoRepository.findById(id)
                .orElseThrow(()-> new BusinessException("Видео не найдено"));
    }
}
