package ru.rfsnab.notificationservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.rfsnab.notificationservice.models.OrderEvent;
import ru.rfsnab.notificationservice.service.EmailService;

/**
 * Обработчик всех событий из топика order-events.
 * Выбирает шаблон письма в зависимости от eventType.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderHandler implements NotificationHandler{

    private final ObjectMapper objectMapper;
    private final EmailService emailService;

    @Value("${app.kafka.topic.order-events}")
    private String topic;

    @Override
    public boolean supports(String topic, String eventType) {
        return this.topic.equals(topic);
    }

    @Override
    public void handle(String eventJson) {

        try{
            OrderEvent event = objectMapper.readValue(eventJson, OrderEvent.class);

            switch (event.eventType()){
                case "ORDER_CREATED" -> handleOrderCreated(event);
                case "ORDER_PAID" -> handleOrderPaid(event);
                case "ORDER_CANCELLED" -> handleOrderCancelled(event);
                case "ORDER_STATUS_CHANGED" -> handleStatusChanged(event);
                default -> log.warn("Неизвестный order eventType: {}", event.eventType());
            }
        } catch (Exception e){
            log.error("Ошибка обработки order-event: {}", eventJson, e);
        }
    }

    private void handleOrderCreated(OrderEvent event) {
        emailService.sendOrderCreatedEmail(event);
        emailService.sendManagerOrderNotification(event);
        log.info("Order created email: order={}, customer={}", event.orderNumber(), event.customerEmail());
    }

    private void handleOrderPaid(OrderEvent event){
        emailService.sendOrderPaidEmail(
                event.customerEmail(),
                event.orderNumber(),
                event.totalAmount()
        );
        log.info("Order paid email: order={}, email={}",
                event.orderNumber(), event.customerEmail());
    }

    private void handleOrderCancelled(OrderEvent event){
        emailService.sendOrderCancelledEmail(
                event.customerEmail(),
                event.orderNumber()
        );
        log.info("Order cancelled email: order={}, email={}",
                event.orderNumber(), event.customerEmail());
    }

    private void handleStatusChanged(OrderEvent event){
        switch (event.status()) {
            case "INVOICE_SENT" -> {
                emailService.sendInvoiceSentEmail(event.customerEmail(), event.orderNumber());
                log.info("Invoice sent email: order={}, email={}", event.orderNumber(), event.customerEmail());
            }
            case "AWAITING_CONFIRMATION" -> {
                emailService.sendAwaitingConfirmationEmail(event.customerEmail(), event.orderNumber());
                log.info("Awaiting confirmation email: order={}, email={}", event.orderNumber(), event.customerEmail());
            }
            default -> {
                emailService.sendOrderStatusChangedEmail(
                        event.customerEmail(),
                        event.orderNumber(),
                        event.status()
                );
                log.info("Order status changed email: order={}, status={}, email={}",
                        event.orderNumber(), event.status(), event.customerEmail());
            }
        }
    }
}
