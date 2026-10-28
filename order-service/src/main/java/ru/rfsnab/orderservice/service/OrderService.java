package ru.rfsnab.orderservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import ru.rfsnab.orderservice.delivery.models.entity.Carrier;
import ru.rfsnab.orderservice.delivery.models.entity.CarrierTerminal;
import ru.rfsnab.orderservice.delivery.service.CarrierService;
import ru.rfsnab.orderservice.delivery.service.DeliveryService;
import ru.rfsnab.orderservice.exception.*;
import ru.rfsnab.orderservice.kafka.Order1CKafkaProducer;
import ru.rfsnab.orderservice.kafka.OrderKafkaProducer;
import ru.rfsnab.orderservice.mapper.AddressMapper;
import ru.rfsnab.orderservice.mapper.OrderMapper;
import ru.rfsnab.orderservice.models.dto.order.CreateOrderRequest;
import ru.rfsnab.orderservice.models.dto.order.HasDeliveryInfo;
import ru.rfsnab.orderservice.models.dto.order.OrderItemDto;
import ru.rfsnab.orderservice.models.dto.order.OrderStatsResponse;
import ru.rfsnab.orderservice.models.dto.order.UpdateOrderRequest;
import ru.rfsnab.orderservice.models.dto.payment.PaymentInitiationResponse;
import ru.rfsnab.orderservice.models.dto.payment.PaymentLinkResponse;
import ru.rfsnab.orderservice.models.dto.payment.PaymentMethodSettingsDto;
import ru.rfsnab.orderservice.models.dto.product.ProductDto;
import ru.rfsnab.orderservice.models.dto.product.WarehouseDto;
import ru.rfsnab.orderservice.models.entity.*;
import ru.rfsnab.orderservice.models.entity.enums.CustomerType;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;
import ru.rfsnab.orderservice.repository.OrderRepository;
import ru.rfsnab.orderservice.repository.projection.OrderStatsProjection;
import ru.rfsnab.orderservice.service.client.PaymentServiceClient;
import ru.rfsnab.orderservice.service.client.ProductServiceClient;
import ru.rfsnab.orderservice.service.client.UserServiceClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис управления заказами.
 *
 * B2B предоплата 100%: CREATED → PROCESSING → INVOICE_SENT → PENDING_PAYMENT → PAID → SHIPPED → IN_TRANSIT → DELIVERED → COMPLETED
 * B2B предоплата 30%: CREATED → PROCESSING → INVOICE_SENT → PENDING_PAYMENT → PARTIALLY_PAID → SHIPPED → IN_TRANSIT → DELIVERED → PENDING_PAYMENT → PAID → COMPLETED
 * B2B постоплата: CREATED → PROCESSING → INVOICE_SENT → AWAITING_CONFIRMATION → SHIPPED → IN_TRANSIT → DELIVERED → PENDING_PAYMENT → PAID → COMPLETED
 * B2C: CREATED → PROCESSING → PENDING_PAYMENT → PAID → SHIPPED → IN_TRANSIT → DELIVERED → COMPLETED
 *
 * CREATED — технический статус (клиент заполняет форму, в 1С не отправляется).
 * Отправка в 1С происходит только при confirmOrder (CREATED → PROCESSING).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String ORDER_NOT_FOUND = "Заказ не найден: ";
    private static final String B2B_ORDER_NUMBER_SEED_PREFIX = "B2B:";

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final WarehousePointService warehousePointService;
    private final CarrierService carrierService;
    private final DeliveryService deliveryService;
    private final ProductServiceClient productServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final UserServiceClient userServiceClient;
    private final OrderKafkaProducer kafkaProducer;
    private final Order1CKafkaProducer order1CKafkaProducer;
    private final PaymentMethodSettingsService paymentMethodSettingsService;

    /** Допустимые переходы между статусами */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS;

    static {
        Map<OrderStatus, Set<OrderStatus>> t = new java.util.EnumMap<>(OrderStatus.class);
        t.put(OrderStatus.CREATED,               Set.of(OrderStatus.PROCESSING, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED));
        t.put(OrderStatus.PROCESSING,            Set.of(OrderStatus.INVOICE_SENT, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED));
        t.put(OrderStatus.INVOICE_SENT,          Set.of(OrderStatus.PENDING_PAYMENT, OrderStatus.AWAITING_CONFIRMATION, OrderStatus.CANCELLED));
        t.put(OrderStatus.PENDING_PAYMENT,       Set.of(OrderStatus.PAID, OrderStatus.PARTIALLY_PAID, OrderStatus.PAYMENT_FAILED, OrderStatus.CANCELLED));
        t.put(OrderStatus.PAYMENT_FAILED,        Set.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED));
        t.put(OrderStatus.AWAITING_CONFIRMATION, Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED));
        t.put(OrderStatus.PAID,                  Set.of(OrderStatus.SHIPPED, OrderStatus.REFUNDED, OrderStatus.COMPLETED));
        t.put(OrderStatus.PARTIALLY_PAID,        Set.of(OrderStatus.SHIPPED, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED));
        t.put(OrderStatus.SHIPPED,               Set.of(OrderStatus.IN_TRANSIT, OrderStatus.ARRIVED_AT_TERMINAL, OrderStatus.DELIVERED));
        t.put(OrderStatus.IN_TRANSIT,            Set.of(OrderStatus.ARRIVED_AT_TERMINAL, OrderStatus.DELIVERED));
        t.put(OrderStatus.ARRIVED_AT_TERMINAL,   Set.of(OrderStatus.DELIVERED));
        t.put(OrderStatus.DELIVERED,             Set.of(OrderStatus.PAID, OrderStatus.PENDING_PAYMENT, OrderStatus.COMPLETED));
        t.put(OrderStatus.CANCELLED,             Set.of(OrderStatus.REFUNDED, OrderStatus.COMPLETED));
        t.put(OrderStatus.REFUNDED,              Set.of(OrderStatus.COMPLETED));
        ALLOWED_TRANSITIONS = java.util.Collections.unmodifiableMap(t);
    }

    /**
     * Допустим ли прямой переход между статусами. Нужен тем, кто решает о переходе заранее,
     * чтобы не ловить исключение внутри чужой транзакции (она стала бы rollback-only).
     */
    public static boolean isTransitionAllowed(OrderStatus from, OrderStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** Статусы, в которых клиент может отменить заказ самостоятельно */
    private static final Set<OrderStatus> CANCELLABLE_STATUSES = Set.of(
            OrderStatus.CREATED,
            OrderStatus.PROCESSING,
            OrderStatus.INVOICE_SENT,
            OrderStatus.PENDING_PAYMENT,
            OrderStatus.PAYMENT_FAILED
    );

    private static final Set<OrderStatus> PAYABLE_STATUSES_B2C = Set.of(
            OrderStatus.PROCESSING,
            OrderStatus.PENDING_PAYMENT,
            OrderStatus.PAYMENT_FAILED
    );

    private static final Set<OrderStatus> PAYABLE_STATUSES_B2B = Set.of(
            OrderStatus.INVOICE_SENT,
            OrderStatus.PENDING_PAYMENT,
            OrderStatus.PAYMENT_FAILED,
            OrderStatus.DELIVERED
    );

    /** Статусы, засчитываемые в сводке как "завершённые" */
    private static final List<OrderStatus> COMPLETED_STATUSES = List.of(
            OrderStatus.DELIVERED,
            OrderStatus.COMPLETED
    );

    /** Статусы, засчитываемые в сводке как "отменённые" */
    private static final List<OrderStatus> CANCELLED_STATUSES = List.of(
            OrderStatus.CANCELLED,
            OrderStatus.REFUNDED
    );

    /** Окно для подсчёта "новых" заказов в сводке админки */
    private static final int NEW_ORDERS_WINDOW_DAYS = 30;

    /** Атрибуты варианта, попадающие в позицию заказа (в порядке вывода) */
    private static final List<String> VARIANT_ATTR_NAMES = List.of("Размер", "Рост");

    private static final String SHIPPING_WAREHOUSE_REQUIRED = "Выберите город отгрузки";

    /**
     * Создание заказа из корзины.
     * Flow:
     * 1. Получаем корзину и валидируем
     * 2. Получаем данные о товарах, проверяем остатки
     * 3. Создаём Order через маппер (базовые поля)
     * 4. Обогащаем items данными из product-service + считаем totalAmount
     * 5. Очищаем корзину
     * 6. Отправляем Kafka event
     */
    @Transactional
    public Order createOrder(Long userId, String customerEmail, String clientType, CreateOrderRequest request) {
        // Для B2B userId — id юрлица, профиля пользователя нет
        if (PriceSelector.parseCustomerType(clientType) != CustomerType.B2B) {
            userServiceClient.requireCompleteProfile(userId);
        }

        CustomerType customerType = PriceSelector.parseCustomerType(clientType);

        // 1. Получаем корзину
        Cart cart = cartService.getCart(userId, customerType);
        if (cart.getItems().isEmpty()) {
            throw new CartEmptyException("Корзина пуста");
        }

        // 2. Получаем данные о товарах и валидируем
        Map<Long, ProductDto> products = fetchAndValidateProducts(cart.getItems());
        rejectSupplierDelivery(request.deliveryMethod(), null);
        ValidatedDelivery delivery = validateDeliveryInfo(request);

        // 3. Создаём заказ — базовые поля через маппер
        Order order = OrderMapper.toEntity(userId, customerEmail, clientType, request);
        applyCarrierSnapshot(order, delivery);
        order.setOrderNumber(generateOrderNumber(userId, customerType));

        // Снапшот имени и телефона заказчика
        if (request.customerName() != null) order.setCustomerName(request.customerName());
        if (request.customerPhone() != null) order.setCustomerPhone(request.customerPhone());

        // B2B validation: companyName and inn are required
        if (CustomerType.B2B == order.getCustomerType()) {
            if (request.companyName() == null || request.inn() == null) {
                throw new InvalidOrderStateException("B2B order requires companyName and inn");
            }
            order.setCompanyName(request.companyName());
            order.setInn(request.inn());
        }

        // 4. Обогащаем items данными из product-service
        BigDecimal totalAmount = addItemsFromCart(order, cart.getItems(), products, clientType);
        order.setTotalAmount(totalAmount);

        order = orderRepository.save(order);
        orderRepository.flush();
        log.info("Заказ создан: {} для пользователя {}", order.getOrderNumber(), userId);

        // 5. Очищаем корзину
        cartService.clearCart(userId, customerType);

        // 6. Kafka event — в 1С заказ уйдёт только после confirmOrder
        kafkaProducer.sendOrderCreated(order);

        return order;
    }

    /**
     * Подтверждение заказа клиентом (CREATED → PROCESSING).
     * Только в этот момент заказ с полными данными отправляется в 1С.
     */
    @Transactional
    public Order confirmOrder(UUID orderId, Long userId, CustomerType customerType) {
        Order order = getOrderAndValidateOwner(orderId, userId, customerType);

        validatePaymentMethodAvailable(order);

        changeStatus(order, OrderStatus.PROCESSING);
        order = orderRepository.save(order);

        log.info("Заказ подтверждён клиентом: {}", order.getOrderNumber());
        kafkaProducer.sendOrderStatusChanged(order);
        order1CKafkaProducer.sendOrderFor1C(order);

        return order;
    }

    /** Для B2C проверяет, что выбранный способ оплаты (CARD/SBP) включён в настройках. */
    private void validatePaymentMethodAvailable(Order order) {
        if (order.getCustomerType() == CustomerType.B2C) {
            PaymentMethodSettingsDto settings = paymentMethodSettingsService.getSettings();
            PaymentMethod method = order.getPaymentMethod();

            if (method == PaymentMethod.CARD && !settings.cardEnabled()) {
                throw new PaymentMethodNotAvailableException("Оплата картой временно недоступна");
            }
            if (method == PaymentMethod.SBP && !settings.sbpEnabled()) {
                throw new PaymentMethodNotAvailableException("Оплата через СБП временно недоступна");
            }
            // INVOICE — оба метода выключены, заказ идёт в 1С как B2B
        }
    }

    /**
     * Обновление заказа. Разрешено в статусах CREATED и PROCESSING.
     * Для PROCESSING заказ повторно отправляется в 1С.
     * Можно изменить: items, адрес, способ доставки, способ оплаты, комментарий.
     */
    @Transactional
    public Order updateOrder(UUID orderId, Long userId, CustomerType customerType, UpdateOrderRequest request) {
        Order order = getOrderAndValidateOwner(orderId, userId, customerType);

        if (order.getStatus() != OrderStatus.CREATED && order.getStatus() != OrderStatus.PROCESSING) {
            throw new InvalidOrderStateException(
                    String.format("Редактирование заказа %s невозможно в статусе %s",
                            order.getOrderNumber(), order.getStatus().getDisplayName()));
        }

        // Валидация доставки — единый метод через HasDeliveryInfo
        rejectSupplierDelivery(request.deliveryMethod(), order.getDeliveryMethod());
        ValidatedDelivery delivery = validateDeliveryInfo(request);

        // Валидация и обогащение items
        Set<Long> productIds = request.items().stream()
                .map(OrderItemDto::productId)
                .collect(Collectors.toSet());
        Map<Long, ProductDto> products = productServiceClient.getProducts(productIds);
        validateStockForItems(request.items(), products);

        // Обновляем поля заказа
        PaymentMethod previousPaymentMethod = order.getPaymentMethod();
        order.setPaymentMethod(request.paymentMethod());
        order.setDeliveryMethod(request.deliveryMethod());
        order.setComment(request.comment());

        // Обновляем адрес / точку самовывоза / ТК
        if (request.deliveryMethod() == DeliveryMethod.PICKUP) {
            order.setWarehousePointId(request.warehousePointId());
            order.setDeliveryAddress(null);
            order.setPickupRecipientName(request.pickupRecipientName());
            order.setPickupRecipientPhone(request.pickupRecipientPhone());
        } else if (request.deliveryMethod() == DeliveryMethod.CARRIER_TERMINAL) {
            order.setDeliveryAddress(null);
            order.setWarehousePointId(null);
            order.setPickupRecipientName(request.pickupRecipientName());
            order.setPickupRecipientPhone(request.pickupRecipientPhone());
        } else {
            order.setDeliveryAddress(AddressMapper.mapToDeliveryAddress(request.deliveryAddress()));
            order.setWarehousePointId(null);
            order.setPickupRecipientName(null);
            order.setPickupRecipientPhone(null);
        }
        // carrierCode есть только у способов с ТК — у них delivery.carrierName() != null
        order.setCarrierCode(delivery.carrierName() != null ? request.carrierCode() : null);
        applyCarrierSnapshot(order, delivery);

        // Обновляем items — orphanRemoval удалит старые
        order.getItems().clear();
        BigDecimal totalAmount = addItemsFromDto(order, request.items(), products, order.getCustomerType());
        order.setTotalAmount(totalAmount);

        // В PROCESSING подтверждения не будет — доступность способа оплаты проверяем здесь,
        // но только при смене способа (иначе нельзя поправить, например, комментарий после отключения способа)
        if (order.getStatus() == OrderStatus.PROCESSING && previousPaymentMethod != order.getPaymentMethod()) {
            validatePaymentMethodAvailable(order);
        }

        order = orderRepository.save(order);
        log.info("Заказ обновлён: {}", order.getOrderNumber());

        if (order.getStatus() == OrderStatus.PROCESSING) {
            order1CKafkaProducer.sendOrderFor1C(order);
        }

        return order;
    }

    /**
     * Повторение заказа — создаёт новый заказ на основе существующего.
     * Проверяет наличие товаров. Если чего-то не хватает — возвращает
     * ошибку с указанием доступного количества.
     */
    @Transactional
    public Order repeatOrder(UUID orderId, Long userId, CustomerType customerType, String customerEmail) {
        Order sourceOrder = getOrderAndValidateOwner(orderId, userId, customerType);

        // Получаем данные о товарах
        Set<Long> productIds = sourceOrder.getItems().stream()
                .map(OrderItem::getProductId)
                .collect(Collectors.toSet());
        Map<Long, ProductDto> products = productServiceClient.getProducts(productIds);

        // Проверяем наличие с информацией о доступном количестве
        validateStockForRepeat(sourceOrder.getItems(), products);

        // Создаём новый заказ — копируем параметры доставки и оплаты
        Order newOrder = Order.builder()
                .userId(userId)
                .orderNumber(generateOrderNumber(userId, customerType))
                .status(OrderStatus.CREATED)
                .paymentMethod(sourceOrder.getPaymentMethod())
                .deliveryMethod(sourceOrder.getDeliveryMethod())
                .carrierCode(sourceOrder.getCarrierCode())
                .carrierName(sourceOrder.getCarrierName())
                .carrierTerminalId(sourceOrder.getCarrierTerminalId())
                .terminalName(sourceOrder.getTerminalName())
                .terminalAddress(sourceOrder.getTerminalAddress())
                .warehousePointId(sourceOrder.getWarehousePointId())
                .pickupRecipientName(sourceOrder.getPickupRecipientName())
                .pickupRecipientPhone(sourceOrder.getPickupRecipientPhone())
                .deliveryAddress(copyDeliveryAddress(sourceOrder.getDeliveryAddress()))
                .customerEmail(customerEmail)
                .customerType(sourceOrder.getCustomerType())
                .comment(sourceOrder.getComment())
                .build();

        // склад могли выключить после исходного заказа — снимок берём заново; у старых заказов склада нет
        WarehouseDto warehouse = null;
        if (sourceOrder.getShippingWarehouseId() != null) {
            warehouse = findActiveWarehouse(sourceOrder.getShippingWarehouseId())
                    .orElseThrow(() -> new InvalidOrderStateException("Город отгрузки больше недоступен, выберите другой"));
        }
        CarrierTerminal terminal = null;
        if (sourceOrder.getDeliveryMethod() == DeliveryMethod.CARRIER_TERMINAL) {
            // терминал и ТК могли отключить после исходного заказа — снимок берём заново
            try {
                carrierService.requireTerminalDelivery(sourceOrder.getCarrierCode());
                terminal = deliveryService.requireActiveTerminal(
                        sourceOrder.getCarrierCode(), sourceOrder.getCarrierTerminalId());
            } catch (InvalidOrderStateException e) {
                throw new InvalidOrderStateException("Терминал больше недоступен, выберите другой");
            }
        }
        applyCarrierSnapshot(newOrder, new ValidatedDelivery(sourceOrder.getCarrierName(), terminal, warehouse));

        // Добавляем items с актуальными ценами
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItem sourceItem : sourceOrder.getItems()) {
            ProductDto product = products.get(sourceItem.getProductId());

            BigDecimal snapshotPrice = PriceSelector.pickPrice(product, customerType);

            OrderItem newItem = OrderItem.builder()
                    .order(newOrder)
                    .productId(sourceItem.getProductId())
                    .productName(product.name())
                    .quantity(sourceItem.getQuantity())
                    .price(snapshotPrice)
                    // склеенный товар уходит в 1С с Ид номенклатуры 1С
                    .externalId(product.oneCExternalId() != null ? product.oneCExternalId() : product.externalId())
                    .sku(product.sku())
                    .unitOfMeasure(product.unitOfMeasure())
                    .categoryExternalId(product.categoryExternalId())
                    .parentProductId(product.parentProductId())
                    .variantAttributes(formatVariantAttributes(product))
                    .build();

            newOrder.getItems().add(newItem);
            totalAmount = totalAmount.add(
                    snapshotPrice.multiply(BigDecimal.valueOf(sourceItem.getQuantity())));
        }
        newOrder.setTotalAmount(totalAmount);

        newOrder = orderRepository.save(newOrder);
        log.info("Повторный заказ создан: {} на основе {}",
                newOrder.getOrderNumber(), sourceOrder.getOrderNumber());

        kafkaProducer.sendOrderCreated(newOrder);

        return newOrder;
    }

    /**
     * Получение заказа по ID с проверкой владельца.
     */
    @Transactional(readOnly = true)
    public Order getOrderByIdAndUser(UUID orderId, Long userId, CustomerType customerType) {
        return getOrderAndValidateOwner(orderId, userId, customerType);
    }

    /**
     * Получение заказа по ID (без проверки владельца — для внутренних вызовов).
     */
    public Order getOrder(UUID orderId) {
        return orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND + orderId));
    }

    /**
     * Получение заказа по номеру.
     */
    @Transactional(readOnly = true)
    public Order getOrderByNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND + orderNumber));
    }

    /**
     * Получение заказов пользователя с пагинацией.
     */
    @Transactional(readOnly = true)
    public Page<Order> getUserOrders(Long userId, CustomerType customerType, Pageable pageable) {
        return orderRepository.findByUserIdAndCustomerTypeOrderByCreatedAtDesc(userId, customerType, pageable);
    }

    /**
     * Получение всех заказов для админа с опциональными фильтрами.
     * Приоритет: dateFrom+dateTo > status > userId > все заказы.
     */
    @Transactional(readOnly = true)
    public Page<Order> getAdminOrders(
            OrderStatus status,
            Long userId,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            Pageable pageable) {
        if (dateFrom != null && dateTo != null) {
            return orderRepository.findByCreatedAtBetween(dateFrom, dateTo, pageable);
        }
        if (status != null) {
            return orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        }
        if (userId != null) {
            // userId здесь — id пользователя (B2C); у B2B userId = id юрлица, числа могут совпасть
            return orderRepository.findByUserIdAndCustomerTypeOrderByCreatedAtDesc(userId, CustomerType.B2C, pageable);
        }
        return orderRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * Сводка по заказам для админки — один проход по таблице.
     */
    @Transactional(readOnly = true)
    public OrderStatsResponse getOrderStats() {
        LocalDateTime since = LocalDateTime.now().minusDays(NEW_ORDERS_WINDOW_DAYS);
        OrderStatsProjection stats = orderRepository.fetchOrderStats(
                OrderStatus.finalStatuses(), COMPLETED_STATUSES, CANCELLED_STATUSES, since);
        return new OrderStatsResponse(
                stats.getTotal(),
                stats.getInProgress(),
                stats.getCompleted(),
                stats.getCancelled(),
                stats.getNewLast30Days());
    }

    /**
     * Подсчёт активных (не финальных) B2C-заказов по userId.
     * Используется при удалении пользователя — блокирует удаление, если есть незавершённые заказы.
     */
    @Transactional(readOnly = true)
    public long countActiveByUserId(Long userId) {
        return orderRepository.countByUserIdAndCustomerTypeAndStatusNotIn(userId, CustomerType.B2C, OrderStatus.finalStatuses());
    }

    /**
     * Подсчёт активных (не финальных) заказов по ИНН юрлица.
     */
    @Transactional(readOnly = true)
    public long countActiveByInn(String inn) {
        return orderRepository.countByInnAndStatusNotIn(inn, OrderStatus.finalStatuses());
    }

    /**
     * Инициация оплаты (PROCESSING/INVOICE_SENT/PAYMENT_FAILED → PENDING_PAYMENT).
     * B2C: вызывается после confirmOrder когда клиент нажимает "Перейти к оплате".
     * B2B: вызывается после INVOICE_SENT когда менеджер выбирает предоплату.
     */
    @Transactional
    public Order initiatePayment(UUID orderId, Long userId, CustomerType customerType) {
        Order order = getOrderAndValidateOwner(orderId, userId, customerType);
        changeStatus(order, OrderStatus.PENDING_PAYMENT);
        order = orderRepository.save(order);

        log.info("Оплата инициирована для заказа: {}", order.getOrderNumber());
        kafkaProducer.sendOrderStatusChanged(order);

        return order;
    }

    /**
     * Подтверждение оплаты (PENDING_PAYMENT → PAID).
     * Вызывается через Kafka event от payment-service.
     */
    @Transactional
    public Order confirmPayment(UUID orderId) {
        Order order = getOrder(orderId);
        changeStatus(order, OrderStatus.PAID);
        order = orderRepository.save(order);

        log.info("Оплата подтверждена для заказа: {}", order.getOrderNumber());
        kafkaProducer.sendOrderPaid(order);

        return order;
    }

    /**
     * Неудачная оплата (PENDING_PAYMENT → PAYMENT_FAILED).
     */
    @Transactional
    public Order failPayment(UUID orderId) {
        Order order = getOrder(orderId);
        changeStatus(order, OrderStatus.PAYMENT_FAILED);
        order = orderRepository.save(order);
        kafkaProducer.sendOrderStatusChanged(order);
        return order;
    }

    /**
     * Обновление статуса заказа (для админов).
     */
    @Transactional
    public Order updateStatus(UUID orderId, OrderStatus newStatus) {
        Order order = getOrder(orderId);
        changeStatus(order, newStatus);
        order = orderRepository.save(order);
        kafkaProducer.sendOrderStatusChanged(order);
        return order;
    }

    /**
     * Обновление статусов заказа для обмена с 1С
     */
    @Transactional
    public Order syncFrom1C(UUID orderId, String externalId, OrderStatus newStatus) {
        Order order = getOrder(orderId);
        order.setExternalId(externalId);
        order.setStatus(newStatus);
        return orderRepository.save(order);
    }

    /**
     * То же, что syncFrom1C, но ищет заказ по orderNumber (на сайте это AAB32389-NNNNN).
     * Используется при импорте статусов из 1С — 1С шлёт <Номер>, а свой UUID в <Ид>.
     */
    @Transactional
    public Order syncFrom1CByOrderNumber(String orderNumber, String externalId, OrderStatus newStatus) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("Заказ не найден по номеру: " + orderNumber));
        order.setExternalId(externalId);
        order.setStatus(newStatus);
        return orderRepository.save(order);
    }

    /**
     * Отмена заказа пользователем. Возможна только до статуса SHIPPED.
     */
    @Transactional
    public Order cancelOrder(UUID orderId, Long userId, CustomerType customerType) {
        Order order = getOrderAndValidateOwner(orderId, userId, customerType);

        if (!CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new InvalidOrderStateException(
                    String.format("Заказ %s нельзя отменить в статусе %s",
                            order.getOrderNumber(), order.getStatus().getDisplayName()));
        }
        changeStatus(order, OrderStatus.CANCELLED);
        order = orderRepository.save(order);

        log.info("Заказ отменён: {}", order.getOrderNumber());
        kafkaProducer.sendOrderCancelled(order);

        return order;
    }

    @Transactional
    public PaymentInitiationResponse pay(UUID orderId, Long userId, CustomerType customerType) {
        Order order = getOrderAndValidateOwner(orderId, userId, customerType);
        validatePayable(order);

        return switch (order.getPaymentMethod()) {
            case CARD, SBP -> initiateOnlinePayment(order);
            case CASH_ON_DELIVERY -> recordCashOnDeliveryIntent(order);
            case INVOICE -> throw new PaymentMethodNotAvailableException(
                    "Онлайн-оплата недоступна: заказ будет обработан менеджером через 1С");
        };
    }

    public String getPaymentStatus(UUID orderId, Long userId, CustomerType customerType) {
        getOrderAndValidateOwner(orderId, userId, customerType);
        var response = paymentServiceClient.getPaymentStatus(orderId);
        return response.status();
    }

    @Transactional
    public Order confirmCashPayment(UUID orderId) {
        Order order = getOrder(orderId);
        if (order.getPaymentMethod() != PaymentMethod.CASH_ON_DELIVERY) {
            throw new InvalidOrderStateException("Заказ не является cash-on-delivery");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidOrderStateException(
                    "Фиксация наличных возможна только в статусе PENDING_PAYMENT");
        }
        paymentServiceClient.recordCashPayment(orderId, order.getTotalAmount());
        changeStatus(order, OrderStatus.PAID);
        order = orderRepository.save(order);
        kafkaProducer.sendOrderPaid(order);
        log.info("Cash payment confirmed for order: {}", order.getOrderNumber());
        return order;
    }

    @Transactional
    public Order refund(UUID orderId) {
        Order order = getOrder(orderId);
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException(
                    "Возврат возможен только для оплаченных или отменённых заказов");
        }
        paymentServiceClient.refundPayment(orderId);
        changeStatus(order, OrderStatus.REFUNDED);
        order = orderRepository.save(order);
        kafkaProducer.sendOrderStatusChanged(order);
        log.info("Refund initiated for order: {}", order.getOrderNumber());
        return order;
    }

    private PaymentInitiationResponse initiateOnlinePayment(Order order) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            changeStatus(order, OrderStatus.PENDING_PAYMENT);
            order = orderRepository.save(order);
            kafkaProducer.sendOrderStatusChanged(order);
        }
        PaymentLinkResponse resp = paymentServiceClient.createPayment(order);
        log.info("Online payment initiated: orderId={}, mode={}, link={}",
                order.getId(), order.getPaymentMethod(), resp.paymentLink());
        return new PaymentInitiationResponse(resp.paymentLink(), order.getPaymentMethod(), order.getStatus());
    }

    private PaymentInitiationResponse recordCashOnDeliveryIntent(Order order) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            changeStatus(order, OrderStatus.PENDING_PAYMENT);
            order = orderRepository.save(order);
            kafkaProducer.sendOrderStatusChanged(order);
        }
        log.info("Cash-on-delivery intent recorded for order: {}", order.getOrderNumber());
        return new PaymentInitiationResponse(null, PaymentMethod.CASH_ON_DELIVERY, order.getStatus());
    }

    private void validatePayable(Order order) {
        boolean isB2B = order.getCustomerType() == CustomerType.B2B;
        Set<OrderStatus> allowed = isB2B ? PAYABLE_STATUSES_B2B : PAYABLE_STATUSES_B2C;
        if (!allowed.contains(order.getStatus())) {
            throw new InvalidOrderStateException(
                    "Оплата невозможна в статусе " + order.getStatus().getDisplayName());
        }
        if (order.getPaymentMethod() == null) {
            throw new InvalidOrderStateException("Способ оплаты не задан");
        }
    }

    // ==================== Private methods ====================

    /**
     * Итог проверки доставки: название ТК и терминал для снимка в заказе (оба null, если способ без ТК)
     * и склад отгрузки (null только в repeatOrder для старого заказа без склада).
     */
    private record ValidatedDelivery(String carrierName, CarrierTerminal terminal, WarehouseDto warehouse) {
    }

    /**
     * Единая валидация данных доставки.
     * Работает с любым DTO через sealed interface HasDeliveryInfo.
     * PICKUP → warehousePointId обязателен и точка должна быть активна.
     * CARRIER_ADDRESS → carrierCode (ТК включена для доставки до адреса) + deliveryAddress.
     * CARRIER_TERMINAL → carrierCode (ТК включена для доставки до терминала) + активный терминал этой ТК
     * + ФИО и телефон получателя (адрес не нужен).
     * SUPPLIER_DELIVERY → deliveryAddress обязателен.
     */
    private ValidatedDelivery validateDeliveryInfo(HasDeliveryInfo request) {
        WarehouseDto warehouse = requireShippingWarehouse(request);
        return switch (request.deliveryMethod()) {
            case PICKUP -> {
                if (request.warehousePointId() == null) {
                    throw new InvalidOrderStateException(
                            "Для самовывоза необходимо указать точку получения");
                }
                warehousePointService.getActivePoint(request.warehousePointId());
                if (!warehouse.pickupAvailable()) {
                    throw new InvalidOrderStateException("Самовывоз недоступен для выбранного города");
                }
                yield new ValidatedDelivery(null, null, warehouse);
            }
            case CARRIER_ADDRESS -> {
                if (request.carrierCode() == null) {
                    throw new InvalidOrderStateException("Выберите транспортную компанию");
                }
                Carrier carrier = carrierService.requireAddressDelivery(request.carrierCode());
                requireDeliveryAddress(request);
                yield new ValidatedDelivery(carrier.getName(), null, warehouse);
            }
            case CARRIER_TERMINAL -> {
                if (request.carrierCode() == null) {
                    throw new InvalidOrderStateException("Выберите транспортную компанию");
                }
                if (request.carrierTerminalId() == null) {
                    throw new InvalidOrderStateException("Выберите терминал транспортной компании");
                }
                if (!StringUtils.hasText(request.pickupRecipientName())
                        || !StringUtils.hasText(request.pickupRecipientPhone())) {
                    throw new InvalidOrderStateException("Укажите ФИО и телефон получателя");
                }
                Carrier carrier = carrierService.requireTerminalDelivery(request.carrierCode());
                CarrierTerminal terminal = deliveryService.requireActiveTerminal(
                        request.carrierCode(), request.carrierTerminalId());
                yield new ValidatedDelivery(carrier.getName(), terminal, warehouse);
            }
            case SUPPLIER_DELIVERY -> {
                requireDeliveryAddress(request);
                yield new ValidatedDelivery(null, null, warehouse);
            }
        };
    }

    private WarehouseDto requireShippingWarehouse(HasDeliveryInfo request) {
        if (request.shippingWarehouseId() == null) {
            throw new InvalidOrderStateException(SHIPPING_WAREHOUSE_REQUIRED);
        }
        return findActiveWarehouse(request.shippingWarehouseId())
                .orElseThrow(() -> new InvalidOrderStateException(SHIPPING_WAREHOUSE_REQUIRED));
    }

    private Optional<WarehouseDto> findActiveWarehouse(Long warehouseId) {
        return productServiceClient.getActiveWarehouses().stream()
                .filter(w -> w.id().equals(warehouseId))
                .findFirst();
    }

    /** Снимок ТК, терминала и склада отгрузки в заказе — из БД/product-service, а не из запроса клиента. */
    private void applyCarrierSnapshot(Order order, ValidatedDelivery delivery) {
        order.setCarrierName(delivery.carrierName());
        CarrierTerminal terminal = delivery.terminal();
        order.setCarrierTerminalId(terminal != null ? terminal.getId() : null);
        order.setTerminalName(terminal != null ? terminal.getName() : null);
        order.setTerminalAddress(terminal != null ? terminal.getAddress() : null);
        WarehouseDto warehouse = delivery.warehouse();
        order.setShippingWarehouseId(warehouse != null ? warehouse.id() : null);
        order.setShippingWarehouseName(warehouse != null ? warehouse.name() : null);
        order.setShippingWarehouseCity(warehouse != null ? warehouse.city() : null);
    }

    private void requireDeliveryAddress(HasDeliveryInfo request) {
        if (request.deliveryAddress() == null) {
            throw new InvalidOrderStateException("Для доставки необходимо указать адрес");
        }
    }

    /**
     * «Доставка поставщиком» назначает только менеджер. Клиент может лишь оставить её,
     * если заказ уже в этом способе.
     *
     * @param current текущий способ заказа; null — заказ создаётся
     */
    private void rejectSupplierDelivery(DeliveryMethod requested, DeliveryMethod current) {
        if (requested == DeliveryMethod.SUPPLIER_DELIVERY && current != DeliveryMethod.SUPPLIER_DELIVERY) {
            throw new InvalidOrderStateException(
                    "Способ «Доставка поставщиком» назначает менеджер — выберите другой способ");
        }
    }

    /**
     * Строка "Размер: X, Рост: Y" для варианта товара; null, если не вариант или атрибутов нет.
     */
    private String formatVariantAttributes(ProductDto product) {
        if (product.parentProductId() == null || product.attributes() == null) {
            return null;
        }
        String result = VARIANT_ATTR_NAMES.stream()
                .flatMap(name -> product.attributes().stream()
                        .filter(a -> name.equals(a.attributeName()) && a.attributeValue() != null)
                        .findFirst().stream())
                .map(a -> a.attributeName() + ": " + a.attributeValue())
                .collect(Collectors.joining(", "));
        return result.isEmpty() ? null : result;
    }

    /**
     * Добавление items из корзины в заказ с обогащением данными из product-service.
     * Фиксирует snapshot цен на момент создания.
     *
     * @return totalAmount заказа
     */
    private BigDecimal addItemsFromCart(Order order, List<CartItem> cartItems,
                                        Map<Long, ProductDto> products, String clientType) {
        BigDecimal totalAmount = BigDecimal.ZERO;

        CustomerType customerType = PriceSelector.parseCustomerType(clientType);

        for (CartItem cartItem : cartItems) {
            ProductDto product = products.get(cartItem.getProductId());

            BigDecimal snapshotPrice = PriceSelector.pickPrice(product, customerType);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productId(cartItem.getProductId())
                    .productName(product.name())
                    .quantity(cartItem.getQuantity())
                    .price(snapshotPrice)
                    // склеенный товар уходит в 1С с Ид номенклатуры 1С
                    .externalId(product.oneCExternalId() != null ? product.oneCExternalId() : product.externalId())
                    .sku(product.sku())
                    .unitOfMeasure(product.unitOfMeasure())
                    .categoryExternalId(product.categoryExternalId())
                    .parentProductId(product.parentProductId())
                    .variantAttributes(formatVariantAttributes(product))
                    .build();

            order.getItems().add(orderItem);
            totalAmount = totalAmount.add(snapshotPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        return totalAmount;
    }

    /**
     * Добавление items из DTO в заказ с обогащением данными из product-service.
     * productName и price берутся из product-service (актуальные данные), не из DTO.
     *
     * @return totalAmount заказа
     */
    private BigDecimal addItemsFromDto(Order order, List<OrderItemDto> items,
                                       Map<Long, ProductDto> products, CustomerType customerType) {
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemDto itemDto : items) {
            ProductDto product = products.get(itemDto.productId());

            BigDecimal snapshotPrice = PriceSelector.pickPrice(product, customerType);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productId(itemDto.productId())
                    .productName(product.name())
                    .quantity(itemDto.quantity())
                    .price(snapshotPrice)
                    // склеенный товар уходит в 1С с Ид номенклатуры 1С
                    .externalId(product.oneCExternalId() != null ? product.oneCExternalId() : product.externalId())
                    .sku(product.sku())
                    .unitOfMeasure(product.unitOfMeasure())
                    .categoryExternalId(product.categoryExternalId())
                    .parentProductId(product.parentProductId())
                    .variantAttributes(formatVariantAttributes(product))
                    .build();

            order.getItems().add(orderItem);
            totalAmount = totalAmount.add(
                    snapshotPrice.multiply(BigDecimal.valueOf(itemDto.quantity())));
        }

        return totalAmount;
    }

    /**
     * Получение и валидация товаров из корзины.
     */
    private Map<Long, ProductDto> fetchAndValidateProducts(List<CartItem> cartItems) {
        Set<Long> productIds = cartItems.stream()
                .map(CartItem::getProductId)
                .collect(Collectors.toSet());

        Map<Long, ProductDto> products = productServiceClient.getProducts(productIds);
        validateStock(cartItems, products);
        return products;
    }

    /**
     * Валидация наличия товаров из корзины.
     */
    private void validateStock(List<CartItem> items, Map<Long, ProductDto> products) {
        for (CartItem item : items) {
            ProductDto product = products.get(item.getProductId());

            if (product == null || !Boolean.TRUE.equals(product.isActive())) {
                throw new ProductNotFoundException(
                        "Товар недоступен: " + item.getProductId());
            }
        }
    }

    /**
     * Валидация наличия товаров из OrderItemDto (для update).
     */
    private void validateStockForItems(List<OrderItemDto> items, Map<Long, ProductDto> products) {
        for (OrderItemDto item : items) {
            ProductDto product = products.get(item.productId());

            if (product == null || !Boolean.TRUE.equals(product.isActive())) {
                throw new ProductNotFoundException(
                        "Товар недоступен: " + item.productId());
            }
        }
    }

    /**
     * Валидация наличия товаров для повторного заказа.
     * При недостатке — собирает все проблемы в один ответ с доступным количеством.
     */
    private void validateStockForRepeat(List<OrderItem> items, Map<Long, ProductDto> products) {
        List<String> unavailableItems = new ArrayList<>();

        for (OrderItem item : items) {
            ProductDto product = products.get(item.getProductId());

            if (product == null || !Boolean.TRUE.equals(product.isActive())) {
                unavailableItems.add(
                        String.format("Товар '%s' (ID: %d) больше недоступен",
                                item.getProductName(), item.getProductId()));
            }
        }

        if (!unavailableItems.isEmpty()) {
            throw new InsufficientStockException(
                    "Невозможно повторить заказ. Товары недоступны:\n"
                            + String.join("\n", unavailableItems));
        }
    }

    /**
     * Копирование DeliveryAddress (embedded) для повторного заказа.
     * Создаёт новый экземпляр, чтобы не ссылаться на embedded из другого Order.
     */
    private DeliveryAddress copyDeliveryAddress(DeliveryAddress source) {
        if (source == null) {
            return null;
        }
        return DeliveryAddress.builder()
                .city(source.getCity())
                .street(source.getStreet())
                .building(source.getBuilding())
                .apartment(source.getApartment())
                .postalCode(source.getPostalCode())
                .phone(source.getPhone())
                .recipientName(source.getRecipientName())
                .build();
    }

    /**
     * Генерация номера заказа.
     * Формат: {UUID-prefix}-{порядковый номер клиента}
     */
    private String generateOrderNumber(Long userId, CustomerType customerType) {
        // B2C сохраняет прежний сид; B2B с тем же числовым id получает другой префикс, иначе номера коллизят
        String seed = customerType == CustomerType.B2B ? B2B_ORDER_NUMBER_SEED_PREFIX + userId : userId.toString();
        String prefix = UUID.nameUUIDFromBytes(seed.getBytes())
                .toString().substring(0, 8).toUpperCase();
        long orderCount = orderRepository.countByUserIdAndCustomerType(userId, customerType) + 1;
        return prefix + "-" + String.format("%05d", orderCount);
    }

    /**
     * Валидация статусного перехода.
     */
    private void changeStatus(Order order, OrderStatus newStatus) {
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(order.getStatus(), Set.of());

        if (!allowed.contains(newStatus)) {
            throw new InvalidOrderStateException(
                    String.format("Недопустимый переход: %s → %s для заказа %s",
                            order.getStatus().getDisplayName(),
                            newStatus.getDisplayName(),
                            order.getOrderNumber()));
        }

        order.setStatus(newStatus);
    }

    /**
     * Получение заказа с проверкой владельца.
     */
    private Order getOrderAndValidateOwner(UUID orderId, Long userId, CustomerType customerType) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND + orderId));

        if (!order.getUserId().equals(userId) || order.getCustomerType() != customerType) {
            throw new OrderNotFoundException(ORDER_NOT_FOUND + orderId);
        }

        return order;
    }
}