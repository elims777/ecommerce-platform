package ru.rfsnab.orderservice.delivery.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import ru.rfsnab.orderservice.BaseIntegrationTest;
import ru.rfsnab.orderservice.delivery.models.dto.ShipmentFilter;
import ru.rfsnab.orderservice.delivery.models.entity.Shipment;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentDirection;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentSource;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentStatusCode;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.enums.CustomerType;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;
import ru.rfsnab.orderservice.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Спецификации журнала отправлений и запрос опроса на реальной PostgreSQL:
 * экранирование LIKE, граница периода, выборка отправлений для фонового опроса.
 */
@DisplayName("ShipmentSpecifications / ShipmentRepository.findPollable")
class ShipmentSpecificationsTest extends BaseIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private ShipmentRepository shipmentRepository;
    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    @AfterEach
    void clean() {
        shipmentRepository.deleteAll();
        orderRepository.deleteAll();
    }

    private Order order(OrderStatus status) {
        return orderRepository.save(Order.builder().userId(1L).orderNumber("SPC-" + COUNTER.incrementAndGet())
                .status(status).paymentMethod(PaymentMethod.INVOICE).deliveryMethod(DeliveryMethod.CARRIER_ADDRESS)
                .totalAmount(BigDecimal.TEN).customerEmail("a@b.ru").customerType(CustomerType.B2B).build());
    }

    private Shipment shipment(Order order, CarrierCode carrier, String waybill, ShipmentStatusCode code) {
        Instant now = Instant.now();
        return shipmentRepository.save(Shipment.builder().order(order).carrierCode(carrier).waybillNumber(waybill)
                .direction(ShipmentDirection.OUTGOING).source(ShipmentSource.MANUAL).carrierStatusCode(code)
                .carrierStatusUpdatedAt(now).createdAt(now).build());
    }

    private static ShipmentFilter filter(String city, String counterparty, String search, Instant from, Instant to) {
        return new ShipmentFilter(null, null, null, city, counterparty, search, from, to, false, false, false, false);
    }

    private List<String> waybills(ShipmentFilter filter) {
        return shipmentRepository.findAll(ShipmentSpecifications.of(filter), PageRequest.of(0, 20, Sort.by("id")))
                .getContent().stream().map(Shipment::getWaybillNumber).toList();
    }

    @Test
    @DisplayName("counterparty: процент, подчёркивание и обратный слеш — обычные символы, а не шаблон LIKE")
    void counterpartyEscapesLikeChars() {
        Shipment percent = shipment(null, CarrierCode.PEC, "P", null);
        percent.setSender("Скидка 50%");
        Shipment underscore = shipment(null, CarrierCode.PEC, "U", null);
        underscore.setReceiver("ООО_Север");
        Shipment backslash = shipment(null, CarrierCode.PEC, "B", null);
        backslash.setSender("ИП\\Юг");
        Shipment plain = shipment(null, CarrierCode.PEC, "N", null);
        plain.setSender("Скидка 500 рублей");
        plain.setReceiver("ООО Север");
        shipmentRepository.saveAll(List.of(percent, underscore, backslash, plain));

        assertThat(waybills(filter(null, "50%", null, null, null))).containsExactly("P");
        assertThat(waybills(filter(null, "%", null, null, null))).containsExactly("P");
        assertThat(waybills(filter(null, "ооо_север", null, null, null))).containsExactly("U");
        assertThat(waybills(filter(null, "_", null, null, null))).containsExactly("U");
        assertThat(waybills(filter(null, "ип\\юг", null, null, null))).containsExactly("B");
        assertThat(waybills(filter(null, "\\", null, null, null))).containsExactly("B");
    }

    @Test
    @DisplayName("search: процент, подчёркивание и обратный слеш в номере накладной — обычные символы")
    void searchEscapesLikeChars() {
        shipment(null, CarrierCode.PEC, "A%1", null);
        shipment(null, CarrierCode.PEC, "A_2", null);
        shipment(null, CarrierCode.PEC, "A\\3", null);
        shipment(null, CarrierCode.PEC, "AB4", null);
        Order order = order(OrderStatus.SHIPPED);
        shipment(order, CarrierCode.PEC, "ORD", null);

        assertThat(waybills(filter(null, null, "a%", null, null))).containsExactly("A%1");
        assertThat(waybills(filter(null, null, "a_", null, null))).containsExactly("A_2");
        assertThat(waybills(filter(null, null, "a\\", null, null))).containsExactly("A\\3");
        assertThat(waybills(filter(null, null, "spc-_", null, null))).isEmpty();
        assertThat(waybills(filter(null, null, order.getOrderNumber(), null, null))).containsExactly("ORD");
    }

    @Test
    @DisplayName("city: процент, подчёркивание и обратный слеш в маршруте — обычные символы")
    void cityEscapesLikeChars() {
        Shipment from = shipment(null, CarrierCode.PEC, "F", null);
        from.setRouteFrom("Усть_Кут");
        Shipment to = shipment(null, CarrierCode.PEC, "T", null);
        to.setRouteTo("100%город");
        Shipment slash = shipment(null, CarrierCode.PEC, "S", null);
        slash.setRouteTo("А\\Б");
        Shipment plain = shipment(null, CarrierCode.PEC, "N", null);
        plain.setRouteFrom("Усть Кут");
        plain.setRouteTo("Москва");
        shipmentRepository.saveAll(List.of(from, to, slash, plain));

        assertThat(waybills(filter("усть_кут", null, null, null, null))).containsExactly("F");
        assertThat(waybills(filter("_", null, null, null, null))).containsExactly("F");
        assertThat(waybills(filter("%", null, null, null, null))).containsExactly("T");
        assertThat(waybills(filter("\\", null, null, null, null))).containsExactly("S");
    }

    @Test
    @DisplayName("период: from включительно, to исключительно — отправление ровно на границе to не попадает")
    void periodBoundaries() {
        Instant boundary = Instant.parse("2026-10-10T12:00:00Z");
        Shipment onBoundary = shipment(null, CarrierCode.PEC, "EQ", null);
        onBoundary.setShippedAt(boundary);
        Shipment before = shipment(null, CarrierCode.PEC, "BEFORE", null);
        before.setShippedAt(boundary.minusSeconds(1));
        Shipment after = shipment(null, CarrierCode.PEC, "AFTER", null);
        after.setShippedAt(boundary.plusSeconds(1));
        shipmentRepository.saveAll(List.of(onBoundary, before, after));

        assertThat(waybills(filter(null, null, null, null, boundary))).containsExactly("BEFORE");
        assertThat(waybills(filter(null, null, null, boundary, null))).containsExactlyInAnyOrder("EQ", "AFTER");
    }

    @Test
    @DisplayName("findPollable: пропускает финальные, остановленные и отправления завершённых заказов")
    void pollableQuery() {
        Order active = order(OrderStatus.SHIPPED);
        Order completed = order(OrderStatus.COMPLETED);
        Order cancelled = order(OrderStatus.CANCELLED);
        shipment(active, CarrierCode.DELLIN, "ok-active", ShipmentStatusCode.IN_TRANSIT);
        shipment(null, CarrierCode.DELLIN, "ok-no-order", null);
        shipment(active, CarrierCode.DELLIN, "final-delivered", ShipmentStatusCode.DELIVERED);
        shipment(active, CarrierCode.DELLIN, "final-cancelled", ShipmentStatusCode.CANCELLED);
        shipment(completed, CarrierCode.DELLIN, "order-completed", ShipmentStatusCode.IN_TRANSIT);
        shipment(cancelled, CarrierCode.DELLIN, "order-cancelled", ShipmentStatusCode.IN_TRANSIT);
        Shipment stopped = shipment(null, CarrierCode.DELLIN, "stopped", ShipmentStatusCode.IN_TRANSIT);
        stopped.setPollingStopped(true);
        shipmentRepository.save(stopped);
        shipment(null, CarrierCode.PEC, "other-carrier", null);

        List<Shipment> due = shipmentRepository.findPollable(CarrierCode.DELLIN,
                List.of(ShipmentStatusCode.DELIVERED, ShipmentStatusCode.CANCELLED),
                List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.REFUNDED));

        assertThat(due).extracting(Shipment::getWaybillNumber).containsExactlyInAnyOrder("ok-active", "ok-no-order");
    }
}
