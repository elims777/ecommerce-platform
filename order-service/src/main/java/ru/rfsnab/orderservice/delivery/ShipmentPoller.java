package ru.rfsnab.orderservice.delivery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Фоновый опрос статусов отправлений (спек 6.5): раз в 2 часа по всем ТК, пачками.
 * Не опрашивает отправления в финальном статусе, с завершённым или отменённым заказом и с остановленным опросом.
 * Каждое отправление применяется в своей транзакции: ошибка одного не мешает остальным.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentPoller {

    /** Каждые 2 часа в начале часа (сервер в UTC). */
    static final String POLL_CRON = "0 0 */2 * * *";

    /** Размер пачки накладных на один запрос к ТК (минимум среди ТК: ПЭК принимает до 15). */
    static final int BATCH_SIZE = 15;

    private static final List<ShipmentStatusCode> FINAL_CODES =
            List.of(ShipmentStatusCode.DELIVERED, ShipmentStatusCode.CANCELLED);

    private static final List<OrderStatus> FINISHED_ORDER_STATUSES =
            List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.REFUNDED);

    private final List<CarrierClient> clients;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentService shipmentService;

    /** Опрашивает все ТК, умеющие отдавать статусы. */
    @Scheduled(cron = POLL_CRON, zone = "UTC")
    public void pollAll() {
        for (CarrierClient client : clients) {
            if (!client.supportsStatusTracking()) {
                continue;
            }
            try {
                pollCarrier(client);
            } catch (RuntimeException e) {
                log.error("Опрос статусов {} прерван", client.code(), e);
            }
        }
    }

    private void pollCarrier(CarrierClient client) {
        List<Shipment> due = shipmentRepository.findPollable(client.code(), FINAL_CODES, FINISHED_ORDER_STATUSES);
        for (int from = 0; from < due.size(); from += BATCH_SIZE) {
            List<Shipment> batch = due.subList(from, Math.min(from + BATCH_SIZE, due.size()));
            Map<String, CarrierShipmentStatus> statuses;
            try {
                statuses = client.fetchStatuses(batch.stream().map(Shipment::getWaybillNumber).toList());
            } catch (CarrierApiException | RestClientException e) {
                log.warn("Опрос статусов {}: пачка из {} накладных пропущена: {}", client.code(), batch.size(), e.getMessage());
                continue;
            }
            for (Shipment shipment : batch) {
                try {
                    shipmentService.applyPolled(shipment.getId(), Optional.ofNullable(statuses.get(shipment.getWaybillNumber())), false);
                } catch (RuntimeException e) {
                    log.error("Не удалось применить статус отправления {}", shipment.getId(), e);
                }
            }
        }
    }
}
