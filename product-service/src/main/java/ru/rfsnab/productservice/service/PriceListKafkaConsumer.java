package ru.rfsnab.productservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.rfsnab.productservice.dto.PriceListRequested;

/**
 * Consumer события генерации прайс-листа.
 * Ошибки генерации гасятся статусом FAILED внутри PriceListService.generate —
 * offset коммитится штатно, повторной обработки не требуется.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceListKafkaConsumer {

    private final PriceListService priceListService;

    @KafkaListener(
            topics = PriceListService.TOPIC_PRICE_LIST_REQUESTS,
            groupId = "${app.kafka.consumer.group-id}",
            containerFactory = "priceListRequestedListenerContainerFactory"
    )
    public void onPriceListRequested(PriceListRequested event) {
        log.info("Получено событие генерации прайс-листа: requestId={}", event.requestId());
        priceListService.generate(event.requestId());
    }
}
