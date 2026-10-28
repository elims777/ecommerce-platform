package ru.rfsnab.orderservice.delivery.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import ru.rfsnab.orderservice.delivery.client.CarrierClient;
import ru.rfsnab.orderservice.delivery.client.CarrierShipmentStatus;
import ru.rfsnab.orderservice.delivery.client.JournalItem;
import ru.rfsnab.orderservice.delivery.client.ShipmentCargo;
import ru.rfsnab.orderservice.delivery.exception.CarrierApiException;
import ru.rfsnab.orderservice.delivery.exception.ShipmentAlreadyExistsException;
import ru.rfsnab.orderservice.delivery.exception.ShipmentModeSwitchRequiredException;
import ru.rfsnab.orderservice.delivery.exception.ShipmentNotFoundException;
import ru.rfsnab.orderservice.delivery.exception.WaybillNotFoundInCarrierException;
import ru.rfsnab.orderservice.delivery.mapper.ShipmentMapper;
import ru.rfsnab.orderservice.delivery.models.dto.AddShipmentRequest;
import ru.rfsnab.orderservice.delivery.models.dto.DocumentFile;
import ru.rfsnab.orderservice.delivery.models.dto.ShipmentDto;
import ru.rfsnab.orderservice.delivery.models.dto.ShipmentFilter;
import ru.rfsnab.orderservice.delivery.models.dto.event.ShipmentNotFoundEvent;
import ru.rfsnab.orderservice.delivery.models.entity.Carrier;
import ru.rfsnab.orderservice.delivery.models.entity.Shipment;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentDocument;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentDirection;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentMode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentSource;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentStatusCode;
import ru.rfsnab.orderservice.delivery.repository.CarrierRepository;
import ru.rfsnab.orderservice.delivery.repository.ShipmentRepository;
import ru.rfsnab.orderservice.delivery.repository.ShipmentSpecifications;
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

    private static final String SHIPMENT_NOT_FOUND = "Отправление не найдено: ";

    private final ShipmentRepository shipmentRepository;
    private final CarrierRepository carrierRepository;
    private final OrderRepository orderRepository;
    private final List<CarrierClient> clients;
    private final OrderStatusAdvancer advancer;
    private final OrderKafkaProducer kafkaProducer;
    private final TransactionTemplate transactionTemplate;
    private final ShipmentMapper shipmentMapper;

    /**
     * Вносит накладную к заказу, предварительно проверив её в ТК: ненайденная накладная не сохраняется.
     * Первая накладная заказа требует режим; вторая при SINGLE — переключения на MULTIPLE.
     * Статус, полученный при проверке, применяется сразу, повторного запроса к ТК нет.
     *
     * @throws WaybillNotFoundInCarrierException  ТК не знает такую накладную
     * @throws CarrierApiException                ТК недоступна, ответила непонятно или не поддерживает опрос статусов
     * @throws InvalidOrderStateException          первая накладная без режима
     * @throws ShipmentModeSwitchRequiredException вторая накладная при режиме SINGLE без shipmentMode=MULTIPLE
     * @throws ShipmentAlreadyExistsException      такая накладная этой ТК уже есть
     * @throws OrderNotFoundException              заказа нет
     */
    public ShipmentDto add(UUID orderId, AddShipmentRequest request, Long userId) {
        String waybill = request.waybillNumber().trim();
        CarrierShipmentStatus status = fetchStatuses(clientFor(request.carrierCode()), waybill).get(waybill);
        if (status == null) {
            String carrierName = carrierRepository.findById(request.carrierCode())
                    .map(Carrier::getName).orElse(request.carrierCode().name());
            throw new WaybillNotFoundInCarrierException("Накладная " + waybill + " не найдена в " + carrierName);
        }
        Long id = transactionTemplate.execute(tx -> create(orderId, request, userId));
        return applyPolled(id, Optional.of(status), true);
    }

    /**
     * Отвязывает накладную от заказа: запись остаётся в журнале. Если накладных у заказа не осталось —
     * сбрасывает режим и признак «все внесены»; при режиме MULTIPLE признак сбрасывается всегда.
     * Статус заказа не откатывается.
     *
     * @throws ShipmentNotFoundException если у этого заказа такой накладной нет
     */
    public void unlink(UUID orderId, Long shipmentId) {
        transactionTemplate.executeWithoutResult(status -> {
            Shipment shipment = shipmentRepository.findWithOrderById(shipmentId)
                    .filter(s -> s.getOrder() != null && s.getOrder().getId().equals(orderId))
                    .orElseThrow(() -> new ShipmentNotFoundException(SHIPMENT_NOT_FOUND + shipmentId));
            Order order = shipment.getOrder();
            shipment.setOrder(null);
            shipmentRepository.save(shipment);
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
        return shipmentRepository.findAll(ShipmentSpecifications.of(filter), pageable)
                .map(s -> shipmentMapper.toDto(s, carriers.get(s.getCarrierCode())));
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
                        Collectors.mapping(s -> shipmentMapper.toDto(s, carriers.get(s.getCarrierCode())), Collectors.toList())));
    }

    /** Отправления одного заказа. */
    @Transactional(readOnly = true)
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
                .orElseThrow(() -> new ShipmentNotFoundException(SHIPMENT_NOT_FOUND + shipmentId));
        CarrierClient client = clientFor(shipment.getCarrierCode());
        if (!client.supportsStatusTracking()) {
            throw new InvalidOrderStateException("Опрос статусов этой транспортной компании не поддерживается");
        }
        // сетевой вызов вне транзакции; номер накладной неизменен, повторное чтение не нужно
        String waybill = shipment.getWaybillNumber();
        return applyPolled(shipmentId, Optional.ofNullable(fetchStatuses(client, waybill).get(waybill)), true);
    }

    /**
     * Скачивает документ отправления в ТК.
     *
     * @throws ShipmentNotFoundException если отправления или документа с таким кодом нет
     * @throws CarrierApiException       если ТК недоступна, не поддерживает скачивание или ответила непонятно
     */
    public DocumentFile downloadDocument(Long shipmentId, String code) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ShipmentNotFoundException(SHIPMENT_NOT_FOUND + shipmentId));
        ShipmentDocument doc = Optional.ofNullable(shipment.getDocuments()).orElse(List.of()).stream()
                .filter(d -> d.code().equals(code)).findFirst()
                .orElseThrow(() -> new ShipmentNotFoundException("Документ не найден: " + code));
        try {
            return new DocumentFile(shipment.getWaybillNumber() + "-" + code + ".pdf",
                    clientFor(shipment.getCarrierCode()).downloadDocument(doc));
        } catch (RestClientException e) {
            throw new CarrierApiException("Транспортная компания недоступна или вернула ошибку");
        }
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
                    .orElseThrow(() -> new ShipmentNotFoundException(SHIPMENT_NOT_FOUND + shipmentId));
            Order order = orderRepository.findByOrderNumber(orderNumber.trim())
                    .orElseThrow(() -> new OrderNotFoundException("Заказ не найден по номеру: " + orderNumber.trim()));
            if (shipment.getOrder() != null && !shipment.getOrder().getId().equals(order.getId())) {
                throw new ShipmentAlreadyExistsException("Накладная уже привязана к заказу "
                        + shipment.getOrder().getOrderNumber() + " — сначала отвяжите её там");
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
                    .orElseThrow(() -> new ShipmentNotFoundException(SHIPMENT_NOT_FOUND + shipmentId));
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
        return toDto(Objects.requireNonNull(saved, "Транзакция applyPolled вернула null"));
    }

    /**
     * Добавляет или обновляет отправление из журнала ТК (upsert по ТК и номеру накладной) в отдельной транзакции.
     * Ручную накладную журнал не перетирает: обновляются только данные маршрута, стороны и статус.
     * Заказ привязывается автоматически только у исходящих: номер заказа клиента в журнале совпал с номером заказа на сайте
     * (у входящих там номер заказа поставщика).
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
            setIfPresent(item.senderInn(), shipment::setSenderInn);
            setIfPresent(item.receiver(), shipment::setReceiver);
            setIfPresent(item.receiverInn(), shipment::setReceiverInn);
            setIfPresent(item.routeFrom(), shipment::setRouteFrom);
            setIfPresent(item.routeTo(), shipment::setRouteTo);
            setIfPresent(item.shippedAt(), shipment::setShippedAt);
            setIfPresent(item.estimatedDeliveryAt(), shipment::setEstimatedDeliveryAt);
            applyCargo(shipment, item.cargo());
            boolean linked = false;
            if (shipment.getOrder() == null && shipment.getDirection() == ShipmentDirection.OUTGOING
                    && item.siteOrderNumber() != null) {
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

    /** Запрос статуса одной накладной; сетевая ошибка превращается в CarrierApiException (502). */
    private Map<String, CarrierShipmentStatus> fetchStatuses(CarrierClient client, String waybill) {
        try {
            return client.fetchStatuses(List.of(waybill));
        } catch (RestClientException e) {
            throw new CarrierApiException("Транспортная компания недоступна или вернула ошибку");
        }
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
        ShipmentStatusCode code = status.code();
        if (code == null && was != ShipmentStatusCode.NOT_FOUND) {
            code = was;
        }
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
        applyCargo(shipment, status.cargo());
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
        kafkaProducer.sendShipmentNotFound(new ShipmentNotFoundEvent(ShipmentNotFoundEvent.TYPE,
                shipment.getOrder() == null ? null : shipment.getOrder().getOrderNumber(),
                carrierName, shipment.getWaybillNumber(), shipment.getNotFoundSince().toString()));
        shipment.setNotFoundNotified(true);
    }

    /** Не-null поля груза перезаписывают значения отправления; null не затирает. */
    private static void applyCargo(Shipment shipment, ShipmentCargo cargo) {
        if (cargo == null) {
            return;
        }
        setIfPresent(cargo.weightKg(), shipment::setWeightKg);
        setIfPresent(cargo.volumeM3(), shipment::setVolumeM3);
        setIfPresent(cargo.placesCount(), shipment::setPlacesCount);
        setIfPresent(cargo.cost(), shipment::setCost);
        setIfPresent(cargo.paid(), shipment::setPaid);
        setIfPresent(cargo.fromAddress(), shipment::setFromAddress);
        setIfPresent(cargo.toAddress(), shipment::setToAddress);
        setIfPresent(cargo.deliveredAt(), shipment::setDeliveredAt);
        setIfPresent(cargo.documents(), shipment::setDocuments);
        setIfPresent(cargo.packages(), shipment::setPackages);
        setIfPresent(cargo.payers(), shipment::setPayers);
    }

    private ShipmentDto toDto(Shipment shipment) {
        return shipmentMapper.toDto(shipment, carrierRepository.findById(shipment.getCarrierCode()).orElse(null));
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
