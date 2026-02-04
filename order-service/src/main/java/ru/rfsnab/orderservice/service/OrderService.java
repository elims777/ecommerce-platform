package ru.rfsnab.orderservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.orderservice.exception.*;
import ru.rfsnab.orderservice.kafka.OrderKafkaProducer;
import ru.rfsnab.orderservice.mapper.AddressMapper;
import ru.rfsnab.orderservice.models.dto.order.AddressDto;
import ru.rfsnab.orderservice.models.dto.order.CreateOrderRequest;
import ru.rfsnab.orderservice.models.dto.product.ProductDto;
import ru.rfsnab.orderservice.models.entity.*;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.repository.OrderRepository;
import ru.rfsnab.orderservice.service.client.ProductServiceClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Сервис управления заказами.
 *
 * Жизненный цикл заказа:
 * CREATED → PENDING_PAYMENT → PAID → PROCESSING → SHIPPED → IN_TRANSIT → DELIVERED
 *
 * Отмена возможна до статуса SHIPPED.
 * Переход PAID → PROCESSING — автоматический.
 * TTL на оплату — 2 дня, после чего заказ отменяется.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final WarehousePointService warehousePointService;
    private final ProductServiceClient productServiceClient;
    private final OrderKafkaProducer kafkaProducer;

    /** Допустимые переходы между статусами */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.CREATED, Set.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED),
            OrderStatus.PENDING_PAYMENT, Set.of(OrderStatus.PAID, OrderStatus.PAYMENT_FAILED, OrderStatus.CANCELLED),
            OrderStatus.PAYMENT_FAILED, Set.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED),
            OrderStatus.PAID, Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
            OrderStatus.PROCESSING, Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, Set.of(OrderStatus.IN_TRANSIT),
            OrderStatus.IN_TRANSIT, Set.of(OrderStatus.DELIVERED),
            OrderStatus.CANCELLED, Set.of(OrderStatus.REFUNDED)
    );

    /** Статусы, в которых пользователь может отменить заказ */
    private static final Set<OrderStatus> CANCELLABLE_STATUSES = Set.of(
            OrderStatus.CREATED,
            OrderStatus.PENDING_PAYMENT,
            OrderStatus.PAID,
            OrderStatus.PROCESSING
    );

    /**
     * Создание заказа из корзины.
     *
     * Flow:
     * 1. Получаем корзину
     * 2. Валидируем наличие товаров
     * 3. Создаём заказ с snapshot цен
     * 4. Очищаем корзину
     * 5. Отправляем Kafka event
     *
     * @param userId идентификатор пользователя
     * @param request параметры заказа (способ оплаты, доставки, адрес)
     * @return созданный заказ
     * @throws CartEmptyException если корзина пуста
     * @throws InsufficientStockException если недостаточно товара
     */
    @Transactional
    public Order createOrder(Long userId, CreateOrderRequest request){
        //1. Получаем корзину
        Cart cart = cartService.getCart(userId);
        if(cart.getItems().isEmpty()){
            throw new CartEmptyException("Корзина пуста");
        }
        // 2. Получаем данные о товарах и валидируем остатки
        Set<Long> productIds = cart.getItems().stream()
                .map(CartItem::getProductId)
                .collect(Collectors.toSet());

        Map<Long, ProductDto> products = productServiceClient.getProducts(productIds);
        validateStock(cart.getItems(), products);
        validateDeliveryInfo(request);

        // 3. Создаём заказ
        Order order = buildOrder(userId, request, cart, products);
        order = orderRepository.save(order);

        // 4. Очищаем корзину
        cartService.clearCart(userId);

        // 5. Kafka event
        kafkaProducer.sendOrderCreated(order);

        return order;
    }

    /**
     * Получение заказа по ID.
     *
     * @param orderId идентификатор заказа
     * @return заказ
     * @throws OrderNotFoundException если заказ не найден
     */
    public Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Заказ не найден: " + orderId));
    }

    /**
     * Получение заказа по номеру.
     *
     * @param orderNumber номер заказа
     * @return заказ
     * @throws OrderNotFoundException если заказ не найден
     */
    public Order getOrderByNumber(String orderNumber){
        return orderRepository.findByOrderNumber(orderNumber).orElseThrow(
                ()-> new OrderNotFoundException("Заказ не найден "+ orderNumber));
    }

    /**
     * Получение заказов пользователя с пагинацией.
     *
     * @param userId идентификатор пользователя
     * @param pageable параметры пагинации
     * @return страница заказов
     */
    public Page<Order> getUserOrders(Long userId, Pageable pageable){
        return orderRepository.findByUserId(userId, pageable);
    }

    /**
     * Инициация оплаты (CREATED → PENDING_PAYMENT).
     * Вызывается по кнопке "Оплатить".
     *
     * @param orderId идентификатор заказа
     * @param userId идентификатор пользователя (для проверки владельца)
     * @return обновлённый заказ
     */
    @Transactional
    public Order initiatePayment(UUID orderId, Long userId){
        Order order = getOrderAndValidateOwner(orderId, userId);
        changeStatus(order, OrderStatus.PENDING_PAYMENT);
        order = orderRepository.save(order);

        log.info("Оплата инициирована для заказа: {}", order.getOrderNumber());
        kafkaProducer.sendOrderStatusChanged(order);

        return order;
    }

    /**
     * Подтверждение оплаты (PENDING_PAYMENT → PAID → PROCESSING).
     * Переход PAID → PROCESSING — автоматический.
     * Вызывается через Kafka event от payment-service.
     *
     * @param orderId идентификатор заказа
     * @return обновлённый заказ
     */
    @Transactional
    public Order confirmPayment(UUID orderId) {
        Order order = getOrder(orderId);

        // PENDING_PAYMENT → PAID
        changeStatus(order, OrderStatus.PAID);
        log.info("Оплата подтверждена для заказа: {}", order.getOrderNumber());

        // PAID → PROCESSING (автоматический переход)
        changeStatus(order, OrderStatus.PROCESSING);
        log.info("Заказ {} переведён в обработку", order.getOrderNumber());

        order = orderRepository.save(order);
        kafkaProducer.sendOrderPaid(order);

        return order;
    }

    /**
     * Неудачная оплата (PENDING_PAYMENT → PAYMENT_FAILED).
     * Пользователь может повторить оплату.
     *
     * @param orderId идентификатор заказа
     * @return обновлённый заказ
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
     * PROCESSING → SHIPPED → IN_TRANSIT → DELIVERED
     *
     * @param orderId идентификатор заказа
     * @param newStatus новый статус
     * @return обновлённый заказ
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
     * Отмена заказа пользователем.
     * Возможна только до статуса SHIPPED.
     *
     * @param orderId идентификатор заказа
     * @param userId идентификатор пользователя (для проверки владельца)
     * @return отменённый заказ
     * @throws InvalidOrderStateException если заказ нельзя отменить
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
        kafkaProducer.sendOrderCancelled(order);

        return order;
    }

    /**
     * Сборка Order entity из корзины и данных о товарах.
     * Фиксирует snapshot цен на момент создания заказа.
     */
    private Order buildOrder(Long userId, CreateOrderRequest request,
                             Cart cart, Map<Long, ProductDto> products) {
        Order order = Order.builder()
                .userId(userId)
                .orderNumber(generateOrderNumber(userId))
                .status(OrderStatus.CREATED)
                .paymentMethod(request.paymentMethod())
                .deliveryMethod(request.deliveryMethod())
                .deliveryAddress(buildDeliveryAddress(request.deliveryAddress()))
                .comment(request.comment())
                .build();

        if (request.deliveryMethod() == DeliveryMethod.PICKUP) {
            order.setWarehousePointId(request.warehousePointId());
            order.setDeliveryAddress(null);
        } else {
            order.setDeliveryAddress(AddressMapper.mapToDeliveryAddress(request.deliveryAddress()));
            order.setWarehousePointId(null);
        }

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            ProductDto product = products.get(cartItem.getProductId());

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productId(cartItem.getProductId())
                    .productName(product.name())
                    .quantity(cartItem.getQuantity())
                    .price(product.price())
                    .build();

            order.getItems().add(orderItem);

            BigDecimal subtotal = product.price().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalAmount = totalAmount.add(subtotal);
        }

        order.setTotalAmount(totalAmount);

        return order;
    }

    /**
     * Маппинг AddressDto → DeliveryAddress (embeddable).
     */
    private DeliveryAddress buildDeliveryAddress(
            AddressDto dto) {
        if (dto == null) {
            return null;
        }
        return AddressMapper.mapToDeliveryAddress(dto);
    }

    /**
     * Генерация номера заказа.
     * Формат: {UUID-prefix}-{порядковый номер клиента}
     * UUID-prefix детерминирован для каждого userId.
     */
    private String generateOrderNumber(Long userId) {
        String prefix = UUID.nameUUIDFromBytes(userId.toString().getBytes())
                .toString().substring(0, 8).toUpperCase();
        long orderCount = orderRepository.countByUserId(userId) + 1;
        return prefix + "-" + String.format("%05d", orderCount);
    }

    /**
     * Валидация статусного перехода.
     *
     * @throws InvalidOrderStateException если переход недопустим
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
     * Валидация наличия всех товаров на складе.
     *
     * @throws ProductNotFoundException если товар не найден или неактивен
     * @throws InsufficientStockException если недостаточно товара
     */
    private void validateStock(List<CartItem> items, Map<Long, ProductDto> products) {
        for (CartItem item : items) {
            ProductDto product = products.get(item.getProductId());

            if (product == null || !product.active()) {
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
     * Получение заказа с проверкой владельца.
     *
     * @throws OrderNotFoundException если заказ не найден
     * @throws InvalidOrderStateException если заказ принадлежит другому пользователю
     */
    private Order getOrderAndValidateOwner(UUID orderId, Long userId) {
        Order order = getOrder(orderId);

        if (!order.getUserId().equals(userId)) {
            throw new InvalidOrderStateException("Нет доступа к заказу: " + orderId);
        }

        return order;
    }

    /**
     * ДОБАВЛЕНО: Валидация данных доставки.
     * PICKUP → warehousePointId обязателен
     * DELIVERY/COURIER → deliveryAddress обязателен
     */
    private void validateDeliveryInfo(CreateOrderRequest request) {
        if (request.deliveryMethod() == DeliveryMethod.PICKUP) {
            if (request.warehousePointId() == null) {
                throw new InvalidOrderStateException("Для самовывоза необходимо указать точку получения");
            }
            // Проверка через сервис — выбросит WarehousePointNotFoundException если не найдена/неактивна
            warehousePointService.getActivePoint(request.warehousePointId());
        } else {
            if (request.deliveryAddress() == null) {
                throw new InvalidOrderStateException("Для доставки необходимо указать адрес");
            }
        }
    }
}
