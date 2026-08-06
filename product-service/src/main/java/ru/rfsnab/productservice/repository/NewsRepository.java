package ru.rfsnab.productservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.rfsnab.productservice.model.News;

import java.util.List;
import java.util.Optional;

public interface NewsRepository extends JpaRepository<News, Long> {

    Optional<News> findBySlugAndIsPublishedTrue(String slug);

    Page<News> findByIsPublishedTrue(Pageable pageable);

    /** Последние опубликованные — для блока на главной. */
    List<News> findByIsPublishedTrueOrderByPublishedAtDesc(Pageable pageable);

    boolean existsBySlug(String slug);
}