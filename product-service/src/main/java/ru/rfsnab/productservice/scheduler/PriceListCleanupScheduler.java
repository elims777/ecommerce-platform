package ru.rfsnab.productservice.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.productservice.model.PriceListRequest;
import ru.rfsnab.productservice.model.PriceListStatus;
import ru.rfsnab.productservice.repository.PriceListRequestRepository;
import ru.rfsnab.productservice.service.StorageService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ежедневная чистка прайс-листов: истёкшие файлы READY удаляются из S3,
 * зависшие PENDING (превышено время формирования) помечаются FAILED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceListCleanupScheduler {

    private static final int READY_RETENTION_DAYS = 3;
    private static final int PENDING_TIMEOUT_MINUTES = 30;

    private final PriceListRequestRepository priceListRequestRepository;
    private final StorageService storageService;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanup() {
        expireOldReadyPriceLists();
        failStalePendingPriceLists();
    }

    private void expireOldReadyPriceLists() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(READY_RETENTION_DAYS);
        List<PriceListRequest> expired = priceListRequestRepository.findByStatusAndCreatedAtBefore(
                PriceListStatus.READY, threshold);

        for (PriceListRequest request : expired) {
            if (request.getFileKey() != null) {
                storageService.deleteFile(request.getFileKey());
            }
            request.setStatus(PriceListStatus.EXPIRED);
            priceListRequestRepository.save(request);
        }

        if (!expired.isEmpty()) {
            log.info("Прайс-листы помечены EXPIRED: {}", expired.size());
        }
    }

    private void failStalePendingPriceLists() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(PENDING_TIMEOUT_MINUTES);
        List<PriceListRequest> stale = priceListRequestRepository.findByStatusAndCreatedAtBefore(
                PriceListStatus.PENDING, threshold);

        for (PriceListRequest request : stale) {
            request.setStatus(PriceListStatus.FAILED);
            request.setErrorMessage("Превышено время формирования");
            request.setCompletedAt(LocalDateTime.now());
            priceListRequestRepository.save(request);
        }

        if (!stale.isEmpty()) {
            log.warn("Зависшие прайс-листы помечены FAILED: {}", stale.size());
        }
    }
}
