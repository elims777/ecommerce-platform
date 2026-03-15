package ru.rfsnab.integrationservice.service.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Сервис выгрузки заказов в 1С.
 *
 * При mode=query формирует CommerceML XML из накопленных заказов (таблица pending_orders).
 * При mode=success помечает заказы как переданные.
 *
 * TODO: реализация — Kafka consumer, формирование XML, выгрузка
 */
@Service
@RequiredArgsConstructor
public class OrderExportService {

    /**
     * Формирование XML с заказами для 1С.
     * Вызывается при type=sale, mode=query.
     *
     * @return CommerceML XML с непереданными заказами
     */
    public String exportPendingOrders() {
        // TODO: реализация — выборка из pending_orders, формирование XML
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<КоммерческаяИнформация />";
    }

    /**
     * Пометка заказов как переданных.
     * Вызывается при type=sale, mode=success (1С подтвердила получение).
     */
    public void markOrdersAsExported() {
        // TODO: реализация — UPDATE pending_orders SET exported=true
    }
}