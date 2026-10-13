package ru.rfsnab.orderservice.delivery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import ru.rfsnab.orderservice.exception.InvalidOrderStateException;
import ru.rfsnab.orderservice.exception.OrderNotFoundException;
import ru.rfsnab.orderservice.kafka.OrderKafkaProducer;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.repository.OrderRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Отправления (накладные ТК): внесение менеджером, журнал, ручное и фоновое обновление статусов,
 * привязка к заказу. Сетевые вызовы к ТК выполняются вне транзакций; каждое изменение отправления — в своей.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentService {

    /** Сколько статус не должен меняться, чтобы остановить опрос («Нет движения»). */
    public static final Duration STALE_AFTER = Duration.ofDays(60);

    /** Сколько накладная должна быть не найдена в ТК, чтобы менеджеру ушло письмо. */
    public static final Duration NOT_FOUND_NOTIFY_AFTER = Duration.ofHours(24);

    private final ShipmentRepository shipmentRepository;
    private final CarrierRepository carrierRepository;
    private final OrderRepository orderRepository;
    private final List<CarrierClient> clients;
    private final OrderStatusAdvancer advancer;
    private final OrderKafkaProducer kafkaProducer;
    private final TransactionTemplate transactionTemplate;

    /**
     * Вносит накладную к заказу и сразу проверяет её в ТК (если ТК поддерживает статусы).
     * Первая накладная заказа требует режим; вторая при SINGLE — переключения на MULTIPLE.
     * Недоступность ТК при проверке не мешает внесению: накладную подхватит опрос.
     *
     * @throws InvalidOrderStateException          первая накладная без режима
     * @throws ShipmentModeSwitchRequiredException вторая накладная при режиме SINGLE без shipmentMode=MULTIPLE
     * @throws ShipmentAlreadyExistsException      такая накладная этой ТК уже есть
     * @throws OrderNotFoundException              заказа нет
     */
    public ShipmentDto add(UUID orderId, AddShipmentRequest request, Long userId) {
        Long id = transactionTemplate.execute(status -> create(orderId, request, userId));
        CarrierClient client = clientFor(request.carrierCode());
        if (client.supportsStatusTracking()) {
            try {
                return refreshChecked(id, client);
            } catch (CarrierApiException e) {
                log.warn("Проверка накладной {} в {} не удалась, подхватит опрос: {}",
                        request.waybillNumber(), request.carrierCode(), e.getMessage());
            }
        }
        return toDto(shipmentRepository.findWithOrderById(id).orElseThrow());
    }

    /**
     * Удаляет ошибочно внесённую накладную заказа. Если накладных не осталось — сбрасывает режим и признак
     * «все внесены»; при режиме MULTIPLE признак сбрасывается всегда. Статус заказа не откатывается.
     *
     * @throws ShipmentNotFoundException если у этого заказа такой накладной нет
     */
    public void delete(UUID orderId, Long shipmentId) {
        transactionTemplate.executeWithoutResult(status -> {
            Shipment shipment = shipmentRepository.findWithOrderById(shipmentId)
                    .filter(s -> s.getOrder() != null && s.getOrder().getId().equals(orderId))
                    .orElseThrow(() -> new ShipmentNotFoundException("Отправление не найдено: " + shipmentId));
            Order order = shipment.getOrder();
            shipmentRepository.delete(shipment);
            // статус заказа назад не откатываем; сбрасываем только признаки внесения
            if (shipmentRepository.countByOrderId(orderId) == 0) {
                order.setShipmentMode(null);
                order.setShipmentsComplete(false);
            } else if (order.getShipmentMode() == ShipmentMode.MULTIPLE) {
                order.setShipmentsComplete(false);
            }
        });
    }

    /**
     * «Все отправления внесены»: фиксирует флаг и пересчитывает статус заказа.
     *
     * @throws InvalidOrderStateException если у заказа нет отправлений
     */
    public Order complete(UUID orderId) {
        return transactionTemplate.execute(status -> {
            Order order = orderRepository.findWithItemsById(orderId)
                    .orElseThrow(() -> new OrderNotFoundException("Заказ не найден: " + orderId));
            if (shipmentRepository.countByOrderId(orderId) == 0) {
                throw new InvalidOrderStateException("У заказа нет отправлений");
            }
            order.setShipmentsComplete(true);
            advancer.advance(order);
            return order;
        });
    }

    /** Журнал отправлений с фильтрами и пагинацией: один запрос с join заказа. */
    @Transactional(readOnly = true)
    public Page<ShipmentDto> getJournal(ShipmentFilter filter, Pageable pageable) {
        Map<CarrierCode, Carrier> carriers = carriersByCode();
        return shipmentRepository.findAll(filter.toSpecification(), pageable)
                .map(s -> ShipmentDto.from(s, carriers.get(s.getCarrierCode())));
    }

    /** Отправления заказов одним запросом: id заказа → его отправления. Заказы без отправлений в карту не попадают. */
    @Transactional(readOnly = true)
    public Map<UUID, List<ShipmentDto>> getByOrderIds(Collection<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        Map<CarrierCode, Carrier> carriers = carriersByCode();
        return shipmentRepository.findAllByOrderIds(orderIds).stream()
                .collect(Collectors.groupingBy(s -> s.getOrder().getId(),
                        Collectors.mapping(s -> ShipmentDto.from(s, carriers.get(s.getCarrierCode())), Collectors.toList())));
    }

    /** Отправления одного заказа. */
    public List<ShipmentDto> getByOrderId(UUID orderId) {
        return getByOrderIds(List.of(orderId)).getOrDefault(orderId, List.of());
    }

    /**
     * Ручное «Обновить статус»: опрашивает ТК всегда, независимо от остановки опроса.
     * Если статус изменился, опрос возобновляется.
     *
     * @throws InvalidOrderStateException если ТК не поддерживает опрос статусов
     * @throws CarrierApiException        если ТК недоступна или ответила непонятно
     */
    public ShipmentDto refresh(Long shipmentId) {
        Shipment shipment = shipmentRepository.findWithOrderById(shipmentId)
                .orElseThrow(() -> new ShipmentNotFoundException("Отправление не найдено: " + shipmentId));
        CarrierClient client = clientFor(shipment.getCarrierCode());
        if (!client.supportsStatusTracking()) {
            throw new InvalidOrderStateException("Опрос статусов этой транспортной компании не поддерживается");
        }
        return refreshChecked(shipmentId, client);
    }

    /**
     * Привязывает отправление к заказу сайта по номеру заказа и пересчитывает статус заказа.
     *
     * @throws OrderNotFoundException    если заказа с таким номером нет
     * @throws ShipmentNotFoundException      если отправления нет
     * @throws ShipmentAlreadyExistsException если отправление уже привязано к другому заказу
     */
    public ShipmentDto link(Long shipmentId, String orderNumber) {
        Long id = transactionTemplate.execute(status -> {
            Shipment shipment = shipmentRepository.findWithOrderById(shipmentId)
                    .orElseThrow(() -> new ShipmentNotFoundException("Отправление не найдено: " + shipmentId));
            Order order = orderRepository.findByOrderNumber(orderNumber.trim())
                    .orElseThrow(() -> new OrderNotFoundException("Заказ не найден по номеру: " + orderNumber.trim()));
            if (shipment.getOrder() != null && !shipment.getOrder().getId().equals(order.getId())) {
                throw new ShipmentAlreadyExistsException("Накладная уже привязана к заказу "
                        + shipment.getOrder().getOrderNumber() + " — сначала удалите её там");
            }
            shipment.setOrder(order);
            shipmentRepository.save(shipment);
            advancer.advance(order);
            return shipment.getId();
        });
        return toDto(shipmentRepository.findWithOrderById(id).orElseThrow());
    }

    /**
     * Применяет результат опроса к отправлению в отдельной транзакции: ошибка одного отправления
     * не откатывает остальные. Изменение статуса двигает заказ вперёд; остановленный опрос возобновляется.
     * Не найденная накладная: для MANUAL — пометка NOT_FOUND и через 24 часа одно событие SHIPMENT_NOT_FOUND;
     * для IMPORT — игнорируется (она пришла от самой ТК).
     *
     * @param status статус от ТК; empty — ТК накладную не знает
     * @param manual ручное обновление; при фоновом опросе отправление без движения 60 дней останавливается
     */
    public ShipmentDto applyPolled(Long shipmentId, Optional<CarrierShipmentStatus> status, boolean manual) {
        Shipment saved = transactionTemplate.execute(tx -> {
            Shipment shipment = shipmentRepository.findWithOrderById(shipmentId)
                    .orElseThrow(() -> new ShipmentNotFoundException("Отправление не найдено: " + shipmentId));
            Instant now = Instant.now();
            shipment.setPolledAt(now);
            boolean changed = status.map(s -> applyFound(shipment, s, now)).orElseGet(() -> applyNotFound(shipment, now));
            if (!manual && !changed && shipment.getCarrierStatusUpdatedAt().plus(STALE_AFTER).isBefore(now)) {
                shipment.setPollingStopped(true);
            }
            notifyIfNotFoundTooLong(shipment, now);
            shipmentRepository.save(shipment);
            if (changed && shipment.getOrder() != null) {
                advancer.advance(shipment.getOrder());
            }
            return shipment;
        });
        return toDto(saved);
    }

    /**
     * Добавляет или обновляет отправление из журнала ТК (upsert по ТК и номеру накладной) в отдельной транзакции.
     * Ручную накладную журнал не перетирает: обновляются только данные маршрута, стороны и статус.
     * Заказ привязывается автоматически, если номер заказа клиента в журнале совпал с номером заказа на сайте.
     */
    public void upsertFromJournal(CarrierCode carrier, JournalItem item, ShipmentDirection direction) {
        transactionTemplate.executeWithoutResult(tx -> {
            Instant now = Instant.now();
            Optional<Shipment> existing = shipmentRepository.findByCarrierAndWaybill(carrier, item.waybillNumber());
            boolean created = existing.isEmpty();
            Shipment shipment = existing.orElseGet(() -> Shipment.builder()
                    .carrierCode(carrier).waybillNumber(item.waybillNumber())
                    .source(ShipmentSource.IMPORT).carrierStatusUpdatedAt(now).createdAt(now).build());
            if (created || direction != ShipmentDirection.UNKNOWN) {
                shipment.setDirection(direction);
            }
            // null в ответе ТК не затирает уже известное значение
            setIfPresent(item.sender(), shipment::setSender);
            setIfPresent(item.receiver(), shipment::setReceiver);
            setIfPresent(item.routeFrom(), shipment::setRouteFrom);
            setIfPresent(item.routeTo(), shipment::setRouteTo);
            setIfPresent(item.shippedAt(), shipment::setShippedAt);
            setIfPresent(item.estimatedDeliveryAt(), shipment::setEstimatedDeliveryAt);
            boolean linked = false;
            if (shipment.getOrder() == null && item.siteOrderNumber() != null) {
                Optional<Order> order = orderRepository.findByOrderNumber(item.siteOrderNumber().trim());
                order.ifPresent(shipment::setOrder);
                linked = order.isPresent();
            }
            boolean changed = item.status() != null && applyFound(shipment, item.status(), now);
            if (item.status() != null) {
                shipment.setPolledAt(now);
            }
            shipmentRepository.save(shipment);
            if (shipment.getOrder() != null && (linked || changed)) {
                advancer.advance(shipment.getOrder());
            }
        });
    }

    private Long create(UUID orderId, AddShipmentRequest request, Long userId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Заказ не найден: " + orderId));
        String waybill = request.waybillNumber().trim();
        if (shipmentRepository.existsByCarrierCodeAndWaybillNumber(request.carrierCode(), waybill)) {
            throw new ShipmentAlreadyExistsException(
                    "Накладная " + waybill + " уже есть в журнале — привяжите её к заказу там");
        }
        if (shipmentRepository.countByOrderId(orderId) == 0) {
            if (request.shipmentMode() == null) {
                throw new InvalidOrderStateException("Для первой накладной выберите режим: одним или несколькими отправлениями");
            }
            order.setShipmentMode(request.shipmentMode());
        } else if (order.getShipmentMode() != ShipmentMode.MULTIPLE) {
            if (request.shipmentMode() != ShipmentMode.MULTIPLE) {
                throw new ShipmentModeSwitchRequiredException();
            }
            order.setShipmentMode(ShipmentMode.MULTIPLE);
        }
        Instant now = Instant.now();
        Shipment shipment;
        try {
            shipment = shipmentRepository.saveAndFlush(Shipment.builder()
                    .order(order).carrierCode(request.carrierCode()).waybillNumber(waybill)
                    .direction(ShipmentDirection.OUTGOING).source(ShipmentSource.MANUAL).createdBy(userId)
                    .carrierStatusUpdatedAt(now).createdAt(now).build());
        } catch (DataIntegrityViolationException e) {
            // гонка: такую же накладную успели внести между проверкой existsBy и вставкой (uq_shipments_carrier_waybill)
            throw new ShipmentAlreadyExistsException(
                    "Накладная " + waybill + " уже есть в журнале — привяжите её к заказу там");
        }
        advancer.advance(order);
        return shipment.getId();
    }

    /** Запрос статуса в ТК вне транзакции и применение результата. */
    private ShipmentDto refreshChecked(Long shipmentId, CarrierClient client) {
        Shipment shipment = shipmentRepository.findWithOrderById(shipmentId).orElseThrow();
        String waybill = shipment.getWaybillNumber();
        Map<String, CarrierShipmentStatus> statuses;
        try {
            statuses = client.fetchStatuses(List.of(waybill));
        } catch (RestClientException e) {
            throw new CarrierApiException("Транспортная компания недоступна или вернула ошибку");
        }
        return applyPolled(shipmentId, Optional.ofNullable(statuses.get(waybill)), true);
    }

    /** @return изменился ли статус отправления */
    private boolean applyFound(Shipment shipment, CarrierShipmentStatus status, Instant now) {
        ShipmentStatusCode was = shipment.getCarrierStatusCode();
        boolean changed = false;
        if (status.rawText() != null && !Objects.equals(status.rawText(), shipment.getCarrierStatus())) {
            shipment.setCarrierStatus(status.rawText());
            changed = true;
        }
        // неизвестный сырой статус код не меняет; но пометка «не найдена» с накладной снимается в любом случае
        ShipmentStatusCode code = status.code() != null ? status.code() : (was == ShipmentStatusCode.NOT_FOUND ? null : was);
        if (code != was) {
            shipment.setCarrierStatusCode(code);
            changed = true;
        }
        if (was == ShipmentStatusCode.NOT_FOUND) {
            shipment.setNotFoundSince(null);
            shipment.setNotFoundNotified(false);
        }
        if (shipment.getRouteFrom() == null) {
            shipment.setRouteFrom(status.routeFrom());
        }
        if (shipment.getRouteTo() == null) {
            shipment.setRouteTo(status.routeTo());
        }
        if (changed) {
            shipment.setCarrierStatusUpdatedAt(now);
            shipment.setPollingStopped(false);
        }
        return changed;
    }

    /** @return изменился ли статус отправления; накладную, статус которой ТК уже сообщала, «не найдено» не затирает */
    private boolean applyNotFound(Shipment shipment, Instant now) {
        if (shipment.getSource() == ShipmentSource.IMPORT || shipment.getCarrierStatusCode() != null) {
            return false;
        }
        shipment.setCarrierStatusCode(ShipmentStatusCode.NOT_FOUND);
        shipment.setNotFoundSince(now);
        shipment.setCarrierStatusUpdatedAt(now);
        shipment.setPollingStopped(false);
        return true;
    }

    private void notifyIfNotFoundTooLong(Shipment shipment, Instant now) {
        if (shipment.getCarrierStatusCode() != ShipmentStatusCode.NOT_FOUND || shipment.isNotFoundNotified()
                || shipment.getNotFoundSince() == null
                || shipment.getNotFoundSince().plus(NOT_FOUND_NOTIFY_AFTER).isAfter(now)) {
            return;
        }
        String carrierName = carrierRepository.findById(shipment.getCarrierCode())
                .map(Carrier::getName).orElse(shipment.getCarrierCode().name());
        kafkaProducer.sendShipmentNotFound(ShipmentNotFoundEvent.of(
                shipment.getOrder() == null ? null : shipment.getOrder().getOrderNumber(),
                carrierName, shipment.getWaybillNumber(), shipment.getNotFoundSince()));
        shipment.setNotFoundNotified(true);
    }

    private ShipmentDto toDto(Shipment shipment) {
        return ShipmentDto.from(shipment, carrierRepository.findById(shipment.getCarrierCode()).orElse(null));
    }

    private static <T> void setIfPresent(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private Map<CarrierCode, Carrier> carriersByCode() {
        return carrierRepository.findAll().stream().collect(Collectors.toMap(Carrier::getCode, Function.identity()));
    }

    private CarrierClient clientFor(CarrierCode code) {
        return clients.stream().filter(c -> c.code() == code).findFirst()
                .orElseThrow(() -> new IllegalStateException("No CarrierClient for " + code));
    }
}
