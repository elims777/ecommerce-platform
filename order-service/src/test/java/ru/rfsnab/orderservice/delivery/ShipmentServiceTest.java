package ru.rfsnab.orderservice.delivery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rfsnab.orderservice.BaseIntegrationTest;
import ru.rfsnab.orderservice.exception.InvalidOrderStateException;
import ru.rfsnab.orderservice.exception.OrderNotFoundException;
import ru.rfsnab.orderservice.kafka.OrderKafkaProducer;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.enums.CustomerType;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;
import ru.rfsnab.orderservice.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ShipmentService на реальной БД: режимы, внесение, удаление, привязка, журнал с фильтрами, опрос, «не найдено».
 * Клиенты ТК, продвижение статуса заказа и Kafka — моки.
 */
@DisplayName("ShipmentService")
class ShipmentServiceTest extends BaseIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private ShipmentRepository shipmentRepository;
    @Autowired
    private CarrierRepository carrierRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final OrderStatusAdvancer advancer = mock(OrderStatusAdvancer.class);
    private final OrderKafkaProducer kafka = mock(OrderKafkaProducer.class);
    private CarrierClient dellin;
    private ShipmentService service;

    @BeforeEach
    void setUp() {
        shipmentRepository.deleteAll();
        dellin = mock(CarrierClient.class);
        when(dellin.code()).thenReturn(CarrierCode.DELLIN);
        when(dellin.supportsStatusTracking()).thenReturn(true);
        CarrierClient pec = mock(CarrierClient.class);
        when(pec.code()).thenReturn(CarrierCode.PEC);
        when(pec.supportsStatusTracking()).thenReturn(false);
        service = new ShipmentService(shipmentRepository, carrierRepository, orderRepository,
                List.of(dellin, pec), advancer, kafka, transactionTemplate);
    }

    @AfterEach
    void cleanUp() {
        shipmentRepository.deleteAll();
        orderRepository.deleteAll();
    }

    private Order order(OrderStatus status) {
        return orderRepository.save(Order.builder().userId(1L).orderNumber("SHP-" + COUNTER.incrementAndGet())
                .status(status).paymentMethod(PaymentMethod.INVOICE).deliveryMethod(DeliveryMethod.CARRIER_ADDRESS)
                .totalAmount(BigDecimal.TEN).customerEmail("a@b.ru").customerType(CustomerType.B2B).build());
    }

    private Shipment shipment(Order order, CarrierCode carrier, String waybill, ShipmentStatusCode code) {
        Instant now = Instant.now();
        return shipmentRepository.save(Shipment.builder().order(order).carrierCode(carrier).waybillNumber(waybill)
                .direction(ShipmentDirection.OUTGOING).source(ShipmentSource.MANUAL).carrierStatusCode(code)
                .carrierStatusUpdatedAt(now).createdAt(now).build());
    }

    private static AddShipmentRequest add(CarrierCode carrier, String waybill, ShipmentMode mode) {
        return new AddShipmentRequest(carrier, waybill, mode);
    }

    private static CarrierShipmentStatus status(ShipmentStatusCode code, String raw) {
        return new CarrierShipmentStatus(code, raw, "Москва", "Новосибирск");
    }

    @Nested
    @DisplayName("Внесение накладной")
    class Adding {

        @Test
        @DisplayName("первая накладная без режима — InvalidOrderStateException (400), ничего не сохранено")
        void firstWithoutModeRejected() {
            Order order = order(OrderStatus.PAID);

            assertThatThrownBy(() -> service.add(order.getId(), add(CarrierCode.PEC, "100", null), 7L))
                    .isInstanceOf(InvalidOrderStateException.class);

            assertThat(shipmentRepository.count()).isZero();
        }

        @Test
        @DisplayName("первая накладная: MANUAL, created_by, режим заказа, продвижение статуса; ТК без трекинга не опрашивается")
        void firstWaybillStored() {
            Order order = order(OrderStatus.PAID);

            ShipmentDto dto = service.add(order.getId(), add(CarrierCode.PEC, " 100 ", ShipmentMode.SINGLE), 7L);

            assertThat(dto.waybillNumber()).isEqualTo("100");
            assertThat(dto.source()).isEqualTo(ShipmentSource.MANUAL);
            assertThat(dto.carrierName()).isEqualTo("ПЭК");
            assertThat(dto.orderNumber()).isEqualTo(order.getOrderNumber());
            assertThat(shipmentRepository.findById(dto.id()).orElseThrow().getCreatedBy()).isEqualTo(7L);
            assertThat(orderRepository.findById(order.getId()).orElseThrow().getShipmentMode()).isEqualTo(ShipmentMode.SINGLE);
            verify(advancer).advance(any(Order.class));
        }

        @Test
        @DisplayName("проверка в ТК сразу после внесения: статус применяется и виден в ответе")
        void checkedInCarrier() {
            Order order = order(OrderStatus.PAID);
            when(dellin.fetchStatuses(List.of("200"))).thenReturn(Map.of("200", status(ShipmentStatusCode.IN_TRANSIT, "В пути")));

            ShipmentDto dto = service.add(order.getId(), add(CarrierCode.DELLIN, "200", ShipmentMode.SINGLE), 7L);

            assertThat(dto.carrierStatusCode()).isEqualTo(ShipmentStatusCode.IN_TRANSIT);
            assertThat(dto.carrierStatus()).isEqualTo("В пути");
            assertThat(dto.routeFrom()).isEqualTo("Москва");
            assertThat(dto.notFound()).isFalse();
            verify(advancer, times(2)).advance(any(Order.class));
        }

        @Test
        @DisplayName("ТК не знает номер: NOT_FOUND и not_found_since")
        void unknownWaybillMarkedNotFound() {
            Order order = order(OrderStatus.PAID);
            when(dellin.fetchStatuses(List.of("404"))).thenReturn(Map.of());

            ShipmentDto dto = service.add(order.getId(), add(CarrierCode.DELLIN, "404", ShipmentMode.SINGLE), 7L);

            assertThat(dto.notFound()).isTrue();
            assertThat(dto.carrierStatusCode()).isEqualTo(ShipmentStatusCode.NOT_FOUND);
            assertThat(shipmentRepository.findById(dto.id()).orElseThrow().getNotFoundSince()).isNotNull();
        }

        @Test
        @DisplayName("ТК недоступна при проверке: накладная всё равно внесена")
        void carrierFailureDoesNotBlockAdding() {
            Order order = order(OrderStatus.PAID);
            when(dellin.fetchStatuses(any())).thenThrow(new CarrierApiException("ТК недоступна"));

            ShipmentDto dto = service.add(order.getId(), add(CarrierCode.DELLIN, "300", ShipmentMode.SINGLE), 7L);

            assertThat(dto.id()).isNotNull();
            assertThat(dto.carrierStatusCode()).isNull();
        }

        @Test
        @DisplayName("вторая накладная при SINGLE — 409 SHIPMENT_MODE_SWITCH_REQUIRED, с MULTIPLE — переключение")
        void secondWaybillRequiresSwitch() {
            Order order = order(OrderStatus.PAID);
            service.add(order.getId(), add(CarrierCode.PEC, "1", ShipmentMode.SINGLE), 7L);

            assertThatThrownBy(() -> service.add(order.getId(), add(CarrierCode.PEC, "2", null), 7L))
                    .isInstanceOf(ShipmentModeSwitchRequiredException.class)
                    .hasMessageContaining("SHIPMENT_MODE_SWITCH_REQUIRED");
            assertThatThrownBy(() -> service.add(order.getId(), add(CarrierCode.PEC, "2", ShipmentMode.SINGLE), 7L))
                    .isInstanceOf(ShipmentModeSwitchRequiredException.class);
            assertThat(shipmentRepository.countByOrderId(order.getId())).isEqualTo(1);

            service.add(order.getId(), add(CarrierCode.PEC, "2", ShipmentMode.MULTIPLE), 7L);

            assertThat(shipmentRepository.countByOrderId(order.getId())).isEqualTo(2);
            assertThat(orderRepository.findById(order.getId()).orElseThrow().getShipmentMode()).isEqualTo(ShipmentMode.MULTIPLE);
        }

        @Test
        @DisplayName("в режиме MULTIPLE следующая накладная не требует режима")
        void multipleNeedsNoMode() {
            Order order = order(OrderStatus.PAID);
            service.add(order.getId(), add(CarrierCode.PEC, "1", ShipmentMode.MULTIPLE), 7L);

            service.add(order.getId(), add(CarrierCode.PEC, "2", null), 7L);

            assertThat(shipmentRepository.countByOrderId(order.getId())).isEqualTo(2);
        }

        @Test
        @DisplayName("та же накладная той же ТК — ShipmentAlreadyExistsException")
        void duplicateRejected() {
            Order order = order(OrderStatus.PAID);
            service.add(order.getId(), add(CarrierCode.PEC, "1", ShipmentMode.MULTIPLE), 7L);

            assertThatThrownBy(() -> service.add(order.getId(), add(CarrierCode.PEC, "1", null), 7L))
                    .isInstanceOf(ShipmentAlreadyExistsException.class);
        }

        @Test
        @DisplayName("несуществующий заказ — OrderNotFoundException")
        void unknownOrder() {
            assertThatThrownBy(() -> service.add(java.util.UUID.randomUUID(), add(CarrierCode.PEC, "1", ShipmentMode.SINGLE), 7L))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Удаление, complete, привязка")
    class Management {

        @Test
        @DisplayName("удаление накладной заказа; чужая накладная — ShipmentNotFoundException")
        void deleteChecksOwnership() {
            Order order = order(OrderStatus.SHIPPED);
            Order other = order(OrderStatus.SHIPPED);
            Shipment s = shipment(order, CarrierCode.PEC, "1", null);

            assertThatThrownBy(() -> service.delete(other.getId(), s.getId())).isInstanceOf(ShipmentNotFoundException.class);
            assertThat(shipmentRepository.existsById(s.getId())).isTrue();

            service.delete(order.getId(), s.getId());

            assertThat(shipmentRepository.existsById(s.getId())).isFalse();
        }

        @Test
        @DisplayName("complete: флаг и пересчёт статуса; без отправлений — InvalidOrderStateException")
        void completeSetsFlag() {
            Order order = order(OrderStatus.SHIPPED);
            assertThatThrownBy(() -> service.complete(order.getId())).isInstanceOf(InvalidOrderStateException.class);
            shipment(order, CarrierCode.PEC, "1", null);

            Order result = service.complete(order.getId());

            assertThat(result.isShipmentsComplete()).isTrue();
            assertThat(orderRepository.findById(order.getId()).orElseThrow().isShipmentsComplete()).isTrue();
            verify(advancer).advance(any(Order.class));
        }

        @Test
        @DisplayName("удаление последней накладной сбрасывает режим и «все внесены», статус заказа не откатывается")
        void deleteLastResetsModeAndComplete() {
            Order order = order(OrderStatus.SHIPPED);
            order.setShipmentMode(ShipmentMode.SINGLE);
            order.setShipmentsComplete(true);
            orderRepository.save(order);
            Shipment s = shipment(order, CarrierCode.PEC, "1", null);

            service.delete(order.getId(), s.getId());

            Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(reloaded.getShipmentMode()).isNull();
            assertThat(reloaded.isShipmentsComplete()).isFalse();
            assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("удаление одной из накладных MULTIPLE снимает «все внесены», режим остаётся")
        void deleteOneOfMultipleResetsComplete() {
            Order order = order(OrderStatus.SHIPPED);
            order.setShipmentMode(ShipmentMode.MULTIPLE);
            order.setShipmentsComplete(true);
            orderRepository.save(order);
            Shipment first = shipment(order, CarrierCode.PEC, "1", null);
            shipment(order, CarrierCode.PEC, "2", null);

            service.delete(order.getId(), first.getId());

            Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(reloaded.getShipmentMode()).isEqualTo(ShipmentMode.MULTIPLE);
            assertThat(reloaded.isShipmentsComplete()).isFalse();
        }

        @Test
        @DisplayName("link: накладная, уже привязанная к другому заказу, — ShipmentAlreadyExistsException, привязка не меняется")
        void linkToOtherOrderRejected() {
            Order owner = order(OrderStatus.PAID);
            Order other = order(OrderStatus.PAID);
            Shipment s = shipment(owner, CarrierCode.PEC, "1", null);

            assertThatThrownBy(() -> service.link(s.getId(), other.getOrderNumber()))
                    .isInstanceOf(ShipmentAlreadyExistsException.class);

            assertThat(shipmentRepository.findWithOrderById(s.getId()).orElseThrow().getOrder().getId()).isEqualTo(owner.getId());
        }

        @Test
        @DisplayName("link: привязка по номеру заказа и продвижение статуса; неизвестный номер — OrderNotFoundException")
        void linkByOrderNumber() {
            Order order = order(OrderStatus.PAID);
            Shipment imported = shipmentRepository.save(Shipment.builder().carrierCode(CarrierCode.DELLIN).waybillNumber("9")
                    .direction(ShipmentDirection.UNKNOWN).source(ShipmentSource.IMPORT)
                    .carrierStatusUpdatedAt(Instant.now()).createdAt(Instant.now()).build());

            assertThatThrownBy(() -> service.link(imported.getId(), "NOPE-1")).isInstanceOf(OrderNotFoundException.class);

            ShipmentDto dto = service.link(imported.getId(), " " + order.getOrderNumber() + " ");

            assertThat(dto.orderId()).isEqualTo(order.getId());
            assertThat(dto.orderNumber()).isEqualTo(order.getOrderNumber());
            verify(advancer).advance(any(Order.class));
        }
    }

    @Nested
    @DisplayName("Журнал")
    class Journal {

        private Shipment journalItem(Order order, CarrierCode carrier, String waybill, ShipmentDirection direction,
                                     ShipmentStatusCode code, String from, String to) {
            Shipment s = shipment(order, carrier, waybill, code);
            s.setDirection(direction);
            s.setRouteFrom(from);
            s.setRouteTo(to);
            return shipmentRepository.save(s);
        }

        private Page<ShipmentDto> find(ShipmentFilter filter) {
            return service.getJournal(filter, PageRequest.of(0, 20, Sort.by("id")));
        }

        private ShipmentFilter filter() {
            return new ShipmentFilter(null, null, null, null, null, null, false, false, false, false);
        }

        @Test
        @DisplayName("фильтры: ТК, направление, статус, город, проблемы, не найдена, нет движения")
        void filters() {
            Order order = order(OrderStatus.SHIPPED);
            journalItem(order, CarrierCode.DELLIN, "A", ShipmentDirection.OUTGOING, ShipmentStatusCode.IN_TRANSIT, "Москва", "Омск");
            journalItem(null, CarrierCode.PEC, "B", ShipmentDirection.INCOMING, ShipmentStatusCode.PROBLEM, "Томск", "Новосибирск");
            journalItem(null, CarrierCode.PEC, "C", ShipmentDirection.UNKNOWN, ShipmentStatusCode.NOT_FOUND, null, null);
            Shipment stale = journalItem(null, CarrierCode.CDEK, "D", ShipmentDirection.OUTGOING, ShipmentStatusCode.IN_TRANSIT, "Омск", "Омск");
            stale.setPollingStopped(true);
            shipmentRepository.save(stale);

            assertThat(find(filter()).getTotalElements()).isEqualTo(4);
            assertThat(waybills(filter(CarrierCode.PEC, null, null, null))).containsExactly("B", "C");
            assertThat(waybills(filter(null, ShipmentDirection.INCOMING, null, null))).containsExactly("B");
            assertThat(waybills(filter(null, null, ShipmentStatusCode.IN_TRANSIT, null))).containsExactly("A", "D");
            assertThat(waybills(filter(null, null, null, "МОСКВ"))).containsExactly("A");
            assertThat(waybills(new ShipmentFilter(null, null, null, null, null, null, true, false, false, false))).containsExactly("B");
            assertThat(waybills(new ShipmentFilter(null, null, null, null, null, null, false, true, false, false))).containsExactly("C");
            assertThat(waybills(new ShipmentFilter(null, null, null, null, null, null, false, false, true, false))).containsExactly("D");
        }

        @Test
        @DisplayName("«ожидаются ещё отправления»: MULTIPLE и не нажато «все внесены»")
        void awaitingMore() {
            Order waiting = order(OrderStatus.SHIPPED);
            waiting.setShipmentMode(ShipmentMode.MULTIPLE);
            orderRepository.save(waiting);
            Order done = order(OrderStatus.SHIPPED);
            done.setShipmentMode(ShipmentMode.MULTIPLE);
            done.setShipmentsComplete(true);
            orderRepository.save(done);
            Order single = order(OrderStatus.SHIPPED);
            single.setShipmentMode(ShipmentMode.SINGLE);
            orderRepository.save(single);
            shipment(waiting, CarrierCode.PEC, "W", null);
            shipment(done, CarrierCode.PEC, "D", null);
            shipment(single, CarrierCode.PEC, "S", null);
            shipment(null, CarrierCode.PEC, "N", null);

            assertThat(waybills(new ShipmentFilter(null, null, null, null, null, null, false, false, false, true)))
                    .containsExactly("W");
        }

        @Test
        @DisplayName("период по дате отгрузки, а без неё по дате внесения; to исключительно")
        void period() {
            Instant now = Instant.now();
            Shipment old = shipment(null, CarrierCode.PEC, "OLD", null);
            old.setShippedAt(now.minus(Duration.ofDays(10)));
            shipmentRepository.save(old);
            shipment(null, CarrierCode.PEC, "NEW", null);

            assertThat(waybills(new ShipmentFilter(null, null, null, null, now.minus(Duration.ofDays(1)), null,
                    false, false, false, false))).containsExactly("NEW");
            assertThat(waybills(new ShipmentFilter(null, null, null, null, null, now.minus(Duration.ofDays(1)),
                    false, false, false, false))).containsExactly("OLD");
        }

        @Test
        @DisplayName("пагинация: totalElements считается отдельно от страницы, номер заказа в DTO")
        void pagination() {
            Order order = order(OrderStatus.SHIPPED);
            for (int i = 0; i < 5; i++) {
                shipment(i == 0 ? order : null, CarrierCode.PEC, "P" + i, null);
            }

            Page<ShipmentDto> page = service.getJournal(filter(), PageRequest.of(0, 2, Sort.by("id")));

            assertThat(page.getTotalElements()).isEqualTo(5);
            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getContent().get(0).orderNumber()).isEqualTo(order.getOrderNumber());
            assertThat(page.getContent().get(1).orderNumber()).isNull();
        }

        @Test
        @DisplayName("отправления нескольких заказов — одним запросом, сгруппированы по заказу")
        void byOrderIds() {
            Order first = order(OrderStatus.SHIPPED);
            Order second = order(OrderStatus.SHIPPED);
            order(OrderStatus.SHIPPED);
            shipment(first, CarrierCode.PEC, "1", null);
            shipment(first, CarrierCode.PEC, "2", null);
            shipment(second, CarrierCode.DELLIN, "3", null);

            Map<java.util.UUID, List<ShipmentDto>> result = service.getByOrderIds(List.of(first.getId(), second.getId()));

            assertThat(result.get(first.getId())).extracting(ShipmentDto::waybillNumber).containsExactly("1", "2");
            assertThat(result.get(second.getId())).extracting(ShipmentDto::carrierName).containsExactly("Деловые Линии");
            assertThat(service.getByOrderId(java.util.UUID.randomUUID())).isEmpty();
        }

        private ShipmentFilter filter(CarrierCode carrier, ShipmentDirection direction, ShipmentStatusCode code, String city) {
            return new ShipmentFilter(carrier, direction, code, city, null, null, false, false, false, false);
        }

        private List<String> waybills(ShipmentFilter filter) {
            return find(filter).getContent().stream().map(ShipmentDto::waybillNumber).toList();
        }
    }

    @Nested
    @DisplayName("Опрос и «накладная не найдена»")
    class Polling {

        @Test
        @DisplayName("фоновый опрос без изменений: 60 дней без движения — опрос останавливается")
        void staleStopsPolling() {
            Shipment s = shipment(null, CarrierCode.DELLIN, "1", ShipmentStatusCode.IN_TRANSIT);
            s.setCarrierStatus("В пути");
            s.setCarrierStatusUpdatedAt(Instant.now().minus(ShipmentService.STALE_AFTER).minusSeconds(60));
            shipmentRepository.save(s);

            service.applyPolled(s.getId(), Optional.of(status(ShipmentStatusCode.IN_TRANSIT, "В пути")), false);

            Shipment saved = shipmentRepository.findById(s.getId()).orElseThrow();
            assertThat(saved.isPollingStopped()).isTrue();
            assertThat(saved.getPolledAt()).isNotNull();
        }

        @Test
        @DisplayName("свежий статус без изменений опрос не останавливает")
        void freshStatusKeepsPolling() {
            Shipment s = shipment(null, CarrierCode.DELLIN, "1", ShipmentStatusCode.IN_TRANSIT);
            s.setCarrierStatus("В пути");
            shipmentRepository.save(s);

            service.applyPolled(s.getId(), Optional.of(status(ShipmentStatusCode.IN_TRANSIT, "В пути")), false);

            assertThat(shipmentRepository.findById(s.getId()).orElseThrow().isPollingStopped()).isFalse();
        }

        @Test
        @DisplayName("ручной refresh с изменением статуса сбрасывает «нет движения» и двигает заказ")
        void manualRefreshResumes() {
            Order order = order(OrderStatus.SHIPPED);
            Shipment s = shipment(order, CarrierCode.DELLIN, "1", ShipmentStatusCode.IN_TRANSIT);
            s.setPollingStopped(true);
            s.setCarrierStatusUpdatedAt(Instant.now().minus(Duration.ofDays(90)));
            shipmentRepository.save(s);
            when(dellin.fetchStatuses(List.of("1"))).thenReturn(Map.of("1", status(ShipmentStatusCode.DELIVERED, "Доставлен")));

            ShipmentDto dto = service.refresh(s.getId());

            assertThat(dto.pollingStopped()).isFalse();
            assertThat(dto.carrierStatusCode()).isEqualTo(ShipmentStatusCode.DELIVERED);
            verify(advancer).advance(any(Order.class));
        }

        @Test
        @DisplayName("ручной refresh без изменений не останавливает и не двигает заказ")
        void manualRefreshWithoutChange() {
            Order order = order(OrderStatus.SHIPPED);
            Shipment s = shipment(order, CarrierCode.DELLIN, "1", ShipmentStatusCode.IN_TRANSIT);
            s.setCarrierStatus("В пути");
            s.setCarrierStatusUpdatedAt(Instant.now().minus(Duration.ofDays(90)));
            shipmentRepository.save(s);
            when(dellin.fetchStatuses(List.of("1"))).thenReturn(Map.of("1", status(ShipmentStatusCode.IN_TRANSIT, "В пути")));

            ShipmentDto dto = service.refresh(s.getId());

            assertThat(dto.pollingStopped()).isFalse();
            verify(advancer, never()).advance(any(Order.class));
        }

        @Test
        @DisplayName("refresh для ТК без трекинга — InvalidOrderStateException; сбой ТК — CarrierApiException")
        void refreshErrors() {
            Shipment pec = shipment(null, CarrierCode.PEC, "1", null);
            Shipment dl = shipment(null, CarrierCode.DELLIN, "2", null);
            when(dellin.fetchStatuses(any())).thenThrow(new org.springframework.web.client.ResourceAccessException("timeout"));

            assertThatThrownBy(() -> service.refresh(pec.getId())).isInstanceOf(InvalidOrderStateException.class);
            assertThatThrownBy(() -> service.refresh(dl.getId())).isInstanceOf(CarrierApiException.class);
            assertThatThrownBy(() -> service.refresh(-1L)).isInstanceOf(ShipmentNotFoundException.class);
        }

        @Test
        @DisplayName("не найдена меньше 24 часов — письма нет; через 24 часа — одно событие, повтор не шлёт")
        void notFoundNotifiedOnce() {
            Order order = order(OrderStatus.SHIPPED);
            Shipment s = shipment(order, CarrierCode.DELLIN, "X1", null);

            service.applyPolled(s.getId(), Optional.empty(), false);
            verify(kafka, never()).sendShipmentNotFound(any());
            Shipment marked = shipmentRepository.findById(s.getId()).orElseThrow();
            assertThat(marked.getCarrierStatusCode()).isEqualTo(ShipmentStatusCode.NOT_FOUND);
            assertThat(marked.getNotFoundSince()).isNotNull();

            marked.setNotFoundSince(Instant.now().minus(ShipmentService.NOT_FOUND_NOTIFY_AFTER).minusSeconds(60));
            shipmentRepository.save(marked);
            service.applyPolled(s.getId(), Optional.empty(), false);
            service.applyPolled(s.getId(), Optional.empty(), false);

            ArgumentCaptor<ShipmentNotFoundEvent> captor = ArgumentCaptor.forClass(ShipmentNotFoundEvent.class);
            verify(kafka, times(1)).sendShipmentNotFound(captor.capture());
            ShipmentNotFoundEvent event = captor.getValue();
            assertThat(event.eventType()).isEqualTo("SHIPMENT_NOT_FOUND");
            assertThat(event.orderNumber()).isEqualTo(order.getOrderNumber());
            assertThat(event.carrierName()).isEqualTo("Деловые Линии");
            assertThat(event.waybillNumber()).isEqualTo("X1");
            assertThat(shipmentRepository.findById(s.getId()).orElseThrow().isNotFoundNotified()).isTrue();
        }

        @Test
        @DisplayName("накладная нашлась: пометка и флаг письма снимаются")
        void foundClearsMark() {
            Shipment s = shipment(null, CarrierCode.DELLIN, "X2", ShipmentStatusCode.NOT_FOUND);
            s.setNotFoundSince(Instant.now().minus(Duration.ofHours(30)));
            s.setNotFoundNotified(true);
            shipmentRepository.save(s);

            service.applyPolled(s.getId(), Optional.of(status(ShipmentStatusCode.IN_TRANSIT, "В пути")), false);

            Shipment saved = shipmentRepository.findById(s.getId()).orElseThrow();
            assertThat(saved.getCarrierStatusCode()).isEqualTo(ShipmentStatusCode.IN_TRANSIT);
            assertThat(saved.getNotFoundSince()).isNull();
            assertThat(saved.isNotFoundNotified()).isFalse();
        }

        @Test
        @DisplayName("неизвестный статус ТК: текст сохраняется, код не меняется; из NOT_FOUND код сбрасывается")
        void unknownStatusKeepsCode() {
            Shipment s = shipment(null, CarrierCode.DELLIN, "X3", ShipmentStatusCode.IN_TRANSIT);

            service.applyPolled(s.getId(), Optional.of(status(null, "Странный статус")), false);

            Shipment saved = shipmentRepository.findById(s.getId()).orElseThrow();
            assertThat(saved.getCarrierStatus()).isEqualTo("Странный статус");
            assertThat(saved.getCarrierStatusCode()).isEqualTo(ShipmentStatusCode.IN_TRANSIT);

            Shipment lost = shipment(null, CarrierCode.DELLIN, "X4", ShipmentStatusCode.NOT_FOUND);
            service.applyPolled(lost.getId(), Optional.of(status(null, "Странный статус")), false);
            assertThat(shipmentRepository.findById(lost.getId()).orElseThrow().getCarrierStatusCode()).isNull();
        }

        @Test
        @DisplayName("IMPORT-накладная «не найдена» игнорируется; уже известная ТК накладная не затирается")
        void importAndKnownNotMarked() {
            Shipment imported = shipmentRepository.save(Shipment.builder().carrierCode(CarrierCode.DELLIN).waybillNumber("I1")
                    .direction(ShipmentDirection.UNKNOWN).source(ShipmentSource.IMPORT)
                    .carrierStatusUpdatedAt(Instant.now()).createdAt(Instant.now()).build());
            Shipment known = shipment(null, CarrierCode.DELLIN, "K1", ShipmentStatusCode.IN_TRANSIT);

            service.applyPolled(imported.getId(), Optional.empty(), false);
            service.applyPolled(known.getId(), Optional.empty(), false);

            assertThat(shipmentRepository.findById(imported.getId()).orElseThrow().getCarrierStatusCode()).isNull();
            assertThat(shipmentRepository.findById(known.getId()).orElseThrow().getCarrierStatusCode())
                    .isEqualTo(ShipmentStatusCode.IN_TRANSIT);
        }

        @Test
        @DisplayName("запрос на опрос: пропускает финальные, остановленные и отправления завершённых заказов")
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

    @Nested
    @DisplayName("Импорт из журнала")
    class JournalImport {

        private JournalItem item(String waybill, String siteOrderNumber, CarrierShipmentStatus status) {
            return new JournalItem(waybill, "ООО Мы", "7700", "ИП Клиент", "5400", "Москва", "Омск",
                    Instant.parse("2026-10-01T00:00:00Z"), null, siteOrderNumber, status);
        }

        @Test
        @DisplayName("upsert: новая IMPORT-запись с направлением и автопривязкой, повторный импорт обновляет, а не дублирует")
        void upsertCreatesThenUpdates() {
            Order order = order(OrderStatus.PAID);

            service.upsertFromJournal(CarrierCode.DELLIN, item("J1", order.getOrderNumber(), status(ShipmentStatusCode.IN_TRANSIT, "В пути")),
                    ShipmentDirection.OUTGOING);
            service.upsertFromJournal(CarrierCode.DELLIN, item("J1", order.getOrderNumber(), status(ShipmentStatusCode.DELIVERED, "Доставлен")),
                    ShipmentDirection.OUTGOING);

            List<Shipment> all = shipmentRepository.findAll();
            assertThat(all).hasSize(1);
            Shipment s = shipmentRepository.findByCarrierAndWaybill(CarrierCode.DELLIN, "J1").orElseThrow();
            assertThat(s.getSource()).isEqualTo(ShipmentSource.IMPORT);
            assertThat(s.getDirection()).isEqualTo(ShipmentDirection.OUTGOING);
            assertThat(s.getOrder().getId()).isEqualTo(order.getId());
            assertThat(s.getSender()).isEqualTo("ООО Мы");
            assertThat(s.getRouteTo()).isEqualTo("Омск");
            assertThat(s.getCarrierStatusCode()).isEqualTo(ShipmentStatusCode.DELIVERED);
            verify(advancer, times(2)).advance(any(Order.class));
        }

        @Test
        @DisplayName("повторный импорт с null в полях не затирает уже известные значения")
        void upsertDoesNotOverwriteWithNulls() {
            service.upsertFromJournal(CarrierCode.DELLIN, item("J4", null, null), ShipmentDirection.OUTGOING);

            service.upsertFromJournal(CarrierCode.DELLIN,
                    new JournalItem("J4", null, null, null, null, null, null, null, null, null, null), ShipmentDirection.OUTGOING);

            Shipment s = shipmentRepository.findByCarrierAndWaybill(CarrierCode.DELLIN, "J4").orElseThrow();
            assertThat(s.getSender()).isEqualTo("ООО Мы");
            assertThat(s.getReceiver()).isEqualTo("ИП Клиент");
            assertThat(s.getRouteFrom()).isEqualTo("Москва");
            assertThat(s.getRouteTo()).isEqualTo("Омск");
            assertThat(s.getShippedAt()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        }

        @Test
        @DisplayName("номер заказа клиента не совпал с заказом сайта: запись без заказа")
        void unknownOrderNumberStaysUnlinked() {
            service.upsertFromJournal(CarrierCode.DELLIN, item("J2", "NOPE-1", null), ShipmentDirection.UNKNOWN);

            Shipment s = shipmentRepository.findByCarrierAndWaybill(CarrierCode.DELLIN, "J2").orElseThrow();
            assertThat(s.getOrder()).isNull();
            assertThat(s.getDirection()).isEqualTo(ShipmentDirection.UNKNOWN);
            verify(advancer, never()).advance(any(Order.class));
        }

        @Test
        @DisplayName("ручная накладная получает данные журнала, но остаётся MANUAL и со своим заказом")
        void manualShipmentKeepsSourceAndOrder() {
            Order order = order(OrderStatus.SHIPPED);
            shipment(order, CarrierCode.DELLIN, "J3", null);

            service.upsertFromJournal(CarrierCode.DELLIN, item("J3", null, status(ShipmentStatusCode.IN_TRANSIT, "В пути")),
                    ShipmentDirection.UNKNOWN);

            Shipment s = shipmentRepository.findByCarrierAndWaybill(CarrierCode.DELLIN, "J3").orElseThrow();
            assertThat(s.getSource()).isEqualTo(ShipmentSource.MANUAL);
            assertThat(s.getDirection()).isEqualTo(ShipmentDirection.OUTGOING);
            assertThat(s.getOrder().getId()).isEqualTo(order.getId());
            assertThat(s.getCarrierStatusCode()).isEqualTo(ShipmentStatusCode.IN_TRANSIT);
        }
    }
}
