package ru.rfsnab.integrationservice.service.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Сервис приёма обновлённых статусов заказов из 1С.
 * 1С отправляет XML со статусами через type=sale, mode=file+import.
 * Парсим XML → обновляем статусы в order-service через REST API.
 * TODO: реализация — парсинг XML статусов, вызов order-service
 */
@Service
@RequiredArgsConstructor
public class OrderStatusImportService {

    /**
     * Обработка обновления статусов заказов от 1С.
     * Вызывается при type=sale, mode=import.
     *
     * @return текстовый ответ для 1С
     */
    public String processStatusUpdate() {
        // TODO: реализация — парсинг orders.xml, обновление статусов через order-service API
        return "success";
    }
}