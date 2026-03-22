package ru.rfsnab.integrationservice.service.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.rfsnab.integrationservice.model.PendingOrder;
import ru.rfsnab.integrationservice.repository.PendingOrderRepository;

/**
 * Kafka consumer для топика "order-1c-export".
 * Получает полные данные заказа от order-service,
 * сохраняет в pending_orders для последующей выгрузки в 1С.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Order1CEventConsumer {

    private final PendingOrderRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Слушает топик order-1c-export.
     * Каждое сообщение — полный заказ (Order1CExportEvent из order-service).
     * Сохраняем JSON as-is в order_data JSONB.
     */
    @KafkaListener(
            topics = "order-1c-export",
            groupId = "${spring.kafka.consumer.group-id:integration-service}"
    )
    public void consume(String message){
        try{
            //Парсим для извлечения orderId и orderNumber
            var jsonNode = objectMapper.readTree(message);
            String orderId = jsonNode.get("orderId").asText();
            String orderNumber = jsonNode.has("orderNumber") ? jsonNode.get("orderNumber").asText() : null;

            // Проверяем дубликат
            if(repository.existsByOrderId(orderId)){
                log.debug("Заказ {} уже существует в pending_orders - пропускаем", orderId);
                return;
            }

            PendingOrder pendingOrder = PendingOrder.builder()
                    .orderId(orderId)
                    .externalId(orderNumber)
                    .orderData(message)
                    .build();

            repository.save(pendingOrder);
            log.info("Заказ {} сохранен в pending_orders для вышрузки в 1С", orderId);
        } catch (JsonProcessingException e){
            log.error("Ошибка парсинга сообщения из order-1c-export: {}", e.getMessage(), e);
        }
    }
}
