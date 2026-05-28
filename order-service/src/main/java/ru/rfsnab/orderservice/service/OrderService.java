package ru.rfsnab.orderservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.orderservice.exception.*;
import ru.rfsnab.orderservice.kafka.Order1CKafkaProducer;
import ru.rfsnab.orderservice.kafka.OrderKafkaProducer;
import ru.rfsnab.orderservice.mapper.AddressMapper;
import ru.rfsnab.orderservice.mapper.OrderMapper;
import ru.rfsnab.orderservice.models.dto.order.CreateOrderRequest;
import ru.rfsnab.orderservice.models.dto.order.HasDeliveryInfo;
import ru.rfsnab.orderservice.models.dto.order.OrderItemDto;
import ru.rfsnab.orderservice.models.dto.order.UpdateOrderRequest;
import ru.rfsnab.orderservice.models.dto.payment.PaymentInitiationResponse;
import ru.rfsnab.orderservice.models.dto.payment.PaymentMethodSettingsDto;
import ru.rfsnab.orderservice.models.dto.payment.PaymentLinkResponse;
import ru.rfsnab.orderservice.models.dto.product.ProductDto;
import ru.rfsnab.orderservice.models.entity.*;
import ru.rfsnab.orderservice.models.entity.enums.CustomerType;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;
import ru.rfsnab.orderservice.repository.OrderRepository;
import ru.rfsnab.orderservice.service.client.PaymentServiceClient;
import ru.rfsnab.orderservice.service.client.ProductServiceClient;

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

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final WarehousePointService warehousePointService;
    private final ProductServiceClient productServiceClient;
    private final PaymentServiceClient paymentServiceClient;
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
        t.put(OrderStatus.PAID,                  Set.of(OrderStatus.SHIPPED, OrderStatus.REFUNDED));
        t.put(OrderStatus.PARTIALLY_PAID,        Set.of(OrderStatus.SHIPPED, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED));
        t.put(OrderStatus.SHIPPED,               Set.of(OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED));
        t.put(OrderStatus.IN_TRANSIT,            Set.of(OrderStatus.DELIVERED));
        t.put(OrderStatus.DELIVERED,             Set.of(OrderStatus.PAID, OrderStatus.PENDING_PAYMENT, OrderStatus.COMPLETED));
        t.put(OrderStatus.CANCELLED,             Set.of(OrderStatus.REFUNDED));
        t.put(OrderStatus.REFUNDED,              Set.of(OrderStatus.COMPLETED));
        ALLOWED_TRANSITIONS = java.util.Collections.unmodifiableMap(t);
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
        // 1. Получаем корзину
        Cart cart = cartService.getCart(userId);
        if (cart.getItems().isEmpty()) {
            throw new CartEmptyException("Корзина пуста");
        }

        // 2. Получаем данные о товарах и валидируем
        Map<Long, ProductDto> products = fetchAndValidateProducts(cart.getItems());
        validateDeliveryInfo(request);

        // 3. Создаём заказ — базовые поля через маппер
        Order order = OrderMapper.toEntity(userId, customerEmail, clientType, request);
        order.setOrderNumber(generateOrderNumber(userId));

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
        cartService.clearCart(userId);

        // 6. Kafka event — в 1С заказ уйдёт только после confirmOrder
        kafkaProducer.sendOrderCreated(order);

        return order;
    }

    /**
     * Подтверждение заказа клиентом (CREATED → PROCESSING).
     * Только в этот момент заказ с полными данными отправляется в 1С.
     */
    @Transactional
    public Order confirmOrder(UUID orderId, Long userId) {
        Order order = getOrderAndValidateOwner(orderId, userId);

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

        changeStatus(order, OrderStatus.PROCESSING);
        order = orderRepository.save(order);

        log.info("Заказ подтверждён клиентом: {}", order.getOrderNumber());
        kafkaProducer.sendOrderStatusChanged(order);
        order1CKafkaProducer.sendOrderFor1C(order);

        return order;
    }

    /**
     * Обновление заказа. Разрешено только в статусе CREATED.
     * Можно изменить: items, адрес, способ доставки, способ оплаты, комментарий.
     */
    @Transactional
    public Order updateOrder(UUID orderId, Long userId, UpdateOrderRequest request) {
        Order order = getOrderAndValidateOwner(orderId, userId);

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new InvalidOrderStateException(
                    String.format("Редактирование заказа %s невозможно в статусе %s",
                            order.getOrderNumber(), order.getStatus().getDisplayName()));
        }

        // Валидация доставки — единый метод через HasDeliveryInfo
        validateDeliveryInfo(request);

        // Валидация и обогащение items
        Set<Long> productIds = request.items().stream()
                .map(OrderItemDto::productId)
                .collect(Collectors.toSet());
        Map<Long, ProductDto> products = productServiceClient.getProducts(productIds);
        validateStockForItems(request.items(), products);

        // Обновляем поля заказа
        order.setPaymentMethod(request.paymentMethod());
        order.setDeliveryMethod(request.deliveryMethod());
        order.setComment(request.comment());

        // Обновляем адрес / точку самовывоза
        if (request.deliveryMethod() == DeliveryMethod.PICKUP) {
            order.setWarehousePointId(request.warehousePointId());
            order.setDeliveryAddress(null);
        } else {
            order.setDeliveryAddress(AddressMapper.mapToDeliveryAddress(request.deliveryAddress()));
            order.setWarehousePointId(null);
        }

        // Обновляем items — orphanRemoval удалит старые
        order.getItems().clear();
        BigDecimal totalAmount = addItemsFromDto(order, request.items(), products);
        order.setTotalAmount(totalAmount);

        order = orderRepository.save(order);
        log.info("Заказ обновлён: {}", order.getOrderNumber());

        return order;
    }

    /**
     * Повторение заказа — создаёт новый заказ на основе существующего.
     * Проверяет наличие товаров. Если чего-то не хватает — возвращает
     * ошибку с указанием доступного количества.
     */
    @Transactional
    public Order repeatOrder(UUID orderId, Long userId, String customerEmail) {
        Order sourceOrder = getOrderAndValidateOwner(orderId, userId);

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
                .orderNumber(generateOrderNumber(userId))
                .status(OrderStatus.CREATED)
                .paymentMethod(sourceOrder.getPaymentMethod())
                .deliveryMethod(sourceOrder.getDeliveryMethod())
                .warehousePointId(sourceOrder.getWarehousePointId())
                .deliveryAddress(copyDeliveryAddress(sourceOrder.getDeliveryAddress()))
                .customerEmail(customerEmail)
                .customerType(sourceOrder.getCustomerType())
                .comment(sourceOrder.getComment())
                .build();

        // Добавляем items с актуальными ценами
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItem sourceItem : sourceOrder.getItems()) {
            ProductDto product = products.get(sourceItem.getProductId());

            OrderItem newItem = OrderItem.builder()
                    .order(newOrder)
                    .productId(sourceItem.getProductId())
                    .productName(product.name())
                    .quantity(sourceItem.getQuantity())
                    .price(product.price())
                    .externalId(product.externalId())
                    .build();

            newOrder.getItems().add(newItem);
            totalAmount = totalAmount.add(
                    product.price().multiply(BigDecimal.valueOf(sourceItem.getQuantity())));
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
    public Order getOrderByIdAndUser(UUID orderId, Long userId) {
        return getOrderAndValidateOwner(orderId, userId);
    }

    /**
     * Получение заказа по ID (без проверки владельца — для внутренних вызовов).
     */
    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId) {
        return orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Заказ не найден: " + orderId));
    }

    /**
     * Получение заказа по номеру.
     */
    @Transactional(readOnly = true)
    public Order getOrderByNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("Заказ не найден: " + orderNumber));
    }

    /**
     * Получение заказов пользователя с пагинацией.
     */
    @Transactional(readOnly = true)
    public Page<Order> getUserOrders(Long userId, Pageable pageable) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
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
            return orderRepository.findByUserId(userId, pageable);
        }
        return orderRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * Инициация оплаты (PROCESSING/INVOICE_SENT/PAYMENT_FAILED → PENDING_PAYMENT).
     * B2C: вызывается после confirmOrder когда клиент нажимает "Перейти к оплате".
     * B2B: вызывается после INVOICE_SENT когда менеджер выбирает предоплату.
     */
    @Transactional
    public Order initiatePayment(UUID orderId, Long userId) {
        Order order = getOrderAndValidateOwner(orderId, userId);
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
     * Отмена заказа пользователем. Возможна только до статуса SHIPPED.
     */
    @Transactional
    public Order cancelOrder(UUID orderId, Long userId) {
        Order order = getOrderAndValidateOwner(orderId, userId);

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
    public PaymentInitiationResponse pay(UUID orderId, Long userId) {
        Order order = getOrderAndValidateOwner(orderId, userId);
        validatePayable(order);

        return switch (order.getPaymentMethod()) {
            case CARD, SBP -> initiateOnlinePayment(order);
            case CASH_ON_DELIVERY -> recordCashOnDeliveryIntent(order);
            case INVOICE -> throw new PaymentMethodNotAvailableException(
                    "Онлайн-оплата недоступна: заказ будет обработан менеджером через 1С");
        };
    }

    public String getPaymentStatus(UUID orderId, Long userId) {
        getOrderByIdAndUser(orderId, userId);
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
     * Единая валидация данных доставки.
     * Работает с любым DTO через sealed interface HasDeliveryInfo.
     * PICKUP → warehousePointId обязателен и точка должна быть активна.
     * SUPPLIER_DELIVERY → deliveryAddress обязателен.
     */
    private void validateDeliveryInfo(HasDeliveryInfo request) {
        if (request.deliveryMethod() == DeliveryMethod.PICKUP) {
            if (request.warehousePointId() == null) {
                throw new InvalidOrderStateException(
                        "Для самовывоза необходимо указать точку получения");
            }
            warehousePointService.getActivePoint(request.warehousePointId());
        } else {
            if (request.deliveryAddress() == null) {
                throw new InvalidOrderStateException(
                        "Для доставки необходимо указать адрес");
            }
        }
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

        for (CartItem cartItem : cartItems) {
            ProductDto product = products.get(cartItem.getProductId());

            BigDecimal snapshotPrice = CustomerType.B2B.name().equals(clientType)
                    ? product.price()
                    : (product.wholesalePrice() != null ? product.wholesalePrice() : product.price());

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productId(cartItem.getProductId())
                    .productName(product.name())
                    .quantity(cartItem.getQuantity())
                    .price(snapshotPrice)
                    .externalId(product.externalId())
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
                                       Map<Long, ProductDto> products) {
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemDto itemDto : items) {
            ProductDto product = products.get(itemDto.productId());

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productId(itemDto.productId())
                    .productName(product.name())
                    .quantity(itemDto.quantity())
                    .price(product.price())
                    .externalId(product.externalId())
                    .build();

            order.getItems().add(orderItem);
            totalAmount = totalAmount.add(
                    product.price().multiply(BigDecimal.valueOf(itemDto.quantity())));
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

            if (product == null || !product.isActive()) {
                throw new ProductNotFoundException(
                        "Товар недоступен: " + item.getProductId());
            }

            if (product.stockQuantity() < item.getQuantity()) {
                throw new InsufficientStockException(
                        String.format("Недостаточно товара %s. Доступно: %d, Запрошено: %d",
                                product.name(), product.stockQuantity(), item.getQuantity()));
            }
        }
    }

    /**
     * Валидация наличия товаров из OrderItemDto (для update).
     */
    private void validateStockForItems(List<OrderItemDto> items, Map<Long, ProductDto> products) {
        for (OrderItemDto item : items) {
            ProductDto product = products.get(item.productId());

            if (product == null || !product.isActive()) {
                throw new ProductNotFoundException(
                        "Товар недоступен: " + item.productId());
            }

            if (product.stockQuantity() < item.quantity()) {
                throw new InsufficientStockException(
                        String.format("Недостаточно товара %s. Доступно: %d, Запрошено: %d",
                                product.name(), product.stockQuantity(), item.quantity()));
            }
        }
    }

    /**
     * Валидация наличия товаров для повторного заказа.
     * При недостатке — собирает все проблемы в один ответ с доступным количеством.
     */
    private void validateStockForRepeat(List<OrderItem> items, Map<Long, ProductDto> products) {
        List<String> insufficientItems = new ArrayList<>();

        for (OrderItem item : items) {
            ProductDto product = products.get(item.getProductId());

            if (product == null || !product.isActive()) {
                insufficientItems.add(
                        String.format("Товар '%s' (ID: %d) больше недоступен",
                                item.getProductName(), item.getProductId()));
                continue;
            }

            if (product.stockQuantity() < item.getQuantity()) {
                insufficientItems.add(
                        String.format("Товар '%s': запрошено %d, доступно %d",
                                product.name(), item.getQuantity(), product.stockQuantity()));
            }
        }

        if (!insufficientItems.isEmpty()) {
            throw new InsufficientStockException(
                    "Невозможно повторить заказ. Проблемы с наличием:\n"
                            + String.join("\n", insufficientItems));
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
    private String generateOrderNumber(Long userId) {
        String prefix = UUID.nameUUIDFromBytes(userId.toString().getBytes())
                .toString().substring(0, 8).toUpperCase();
        long orderCount = orderRepository.countByUserId(userId) + 1;
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
    private Order getOrderAndValidateOwner(UUID orderId, Long userId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Заказ не найден: " + orderId));

        if (!order.getUserId().equals(userId)) {
            throw new InvalidOrderStateException("Нет доступа к заказу: " + orderId);
        }

        return order;
    }
}