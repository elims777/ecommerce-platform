import { useState } from 'react';
import { Table, Skeleton, Pagination, Spin, Button, message, Modal, QRCode } from 'antd';
import { ShoppingOutlined, CreditCardOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, Link } from 'react-router-dom';
import { getMyOrders, getOrderById, initiatePayment, cancelOrder } from '@/api/orders';
import { OrderStatusLabels, PaymentMethodLabels, DeliveryMethodLabels, PaymentMethod, OrderStatus } from '@/types/order';
import { extractEnumCode, extractEnumDisplayName } from '@/utils/enumUtils';
import type { OrderSummaryDto, OrderDto, OrderItemDto } from '@/types/order';
import type { ColumnsType } from 'antd/es/table';
import { formatPriceOrPlaceholder, isPriceAvailable } from '@/utils/priceUtils';
import EditOrderModal from './EditOrderModal';

/** Статусы, в которых заказ ещё можно отменить (до отгрузки) */
const CANCELLABLE_STATUSES: OrderStatus[] = [
    OrderStatus.CREATED,
    OrderStatus.PROCESSING,
    OrderStatus.INVOICE_SENT,
    OrderStatus.PENDING_PAYMENT,
    OrderStatus.PAYMENT_FAILED,
];

const formatPrice = formatPriceOrPlaceholder;

const formatDate = (dateStr: string): string =>
    new Date(dateStr).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });

const StatusBadge = ({ status }: { status: string }) => {
    const cfg: Record<string, { bg: string; color: string }> = {
        DRAFT: { bg: 'var(--surface-3)', color: 'var(--ink-2)' },
        CREATED: { bg: 'var(--surface-3)', color: 'var(--ink-2)' },
        AWAITING_CONFIRMATION: { bg: 'var(--warn-tint)', color: 'var(--warn)' },
        PENDING_PAYMENT: { bg: 'var(--warn-tint)', color: 'var(--warn)' },
        CONFIRMED: { bg: 'var(--navy-tint)', color: 'var(--brand-navy)' },
        PROCESSING: { bg: 'var(--navy-tint)', color: 'var(--brand-navy)' },
        INVOICE_SENT: { bg: 'var(--navy-tint)', color: 'var(--brand-navy)' },
        PAID: { bg: 'var(--navy-tint)', color: 'var(--brand-navy)' },
        SHIPPED: { bg: 'var(--warn-tint)', color: 'var(--warn)' },
        IN_TRANSIT: { bg: 'var(--warn-tint)', color: 'var(--warn)' },
        DELIVERED: { bg: 'var(--brand-green-soft)', color: 'var(--brand-green)' },
        COMPLETED: { bg: 'var(--brand-green-soft)', color: 'var(--brand-green)' },
        CANCELLED: { bg: 'var(--red-tint)', color: 'var(--brand-red)' },
        REFUNDED: { bg: 'var(--red-tint)', color: 'var(--brand-red)' },
        PAYMENT_FAILED: { bg: 'var(--red-tint)', color: 'var(--brand-red)' },
    };
    const { bg, color } = cfg[status] || { bg: 'var(--surface-3)', color: 'var(--ink-2)' };
    return (
        <span style={{
            display: 'inline-flex', alignItems: 'center', gap: 5,
            height: 22, padding: '0 8px', borderRadius: 11,
            fontSize: 'var(--text-sm)', fontWeight: 500,
            background: bg, color,
        }}>
            <span style={{ width: 6, height: 6, borderRadius: 3, background: 'currentColor', flexShrink: 0 }} />
            {OrderStatusLabels[status as OrderStatus] || status}
        </span>
    );
};

const OrdersPage = () => {
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const [currentPage, setCurrentPage] = useState(1);
    const [expandedOrderDetails, setExpandedOrderDetails] = useState<Record<string, OrderDto>>({});
    const [loadingDetails, setLoadingDetails] = useState<Record<string, boolean>>({});
    const [payingOrderId, setPayingOrderId] = useState<string | null>(null);
    const [sbpModal, setSbpModal] = useState<{ orderNumber: string; paymentLink: string } | null>(null);
    const [cancellingOrderId, setCancellingOrderId] = useState<string | null>(null);
    const [editingOrder, setEditingOrder] = useState<OrderDto | null>(null);
    const pageSize = 10;

    const { data: ordersPage, isLoading, isError } = useQuery({
        queryKey: ['myOrders', currentPage],
        queryFn: () => getMyOrders(currentPage - 1, pageSize),
    });

    const handleCancelOrder = (orderId: string, orderNumber: string) => {
        Modal.confirm({
            title: `Отменить заказ ${orderNumber}?`,
            content: 'Действие нельзя отменить.',
            okText: 'Отменить заказ',
            okButtonProps: { danger: true },
            cancelText: 'Не отменять',
            onOk: async () => {
                setCancellingOrderId(orderId);
                try {
                    const updated = await cancelOrder(orderId);
                    setExpandedOrderDetails((prev) => ({ ...prev, [orderId]: updated }));
                    message.success('Заказ отменён');
                    queryClient.invalidateQueries({ queryKey: ['myOrders'] });
                } catch {
                    message.error('Не удалось отменить заказ. Попробуйте позже.');
                } finally {
                    setCancellingOrderId(null);
                }
            },
        });
    };

    const handlePayCard = async (orderId: string) => {
        setPayingOrderId(orderId);
        try {
            const { paymentLink } = await initiatePayment(orderId);
            if (paymentLink) {
                window.location.href = paymentLink;
            }
        } catch {
            message.error('Не удалось инициировать оплату. Попробуйте позже.');
            setPayingOrderId(null);
        }
    };

    const handlePaySbp = async (orderId: string, orderNumber: string) => {
        setPayingOrderId(orderId);
        try {
            const { paymentLink } = await initiatePayment(orderId);
            if (paymentLink) {
                setSbpModal({ orderNumber, paymentLink });
            }
        } catch {
            message.error('Не удалось получить QR для оплаты. Попробуйте позже.');
        } finally {
            setPayingOrderId(null);
        }
    };

    const loadOrderDetails = async (orderId: string) => {
        if (expandedOrderDetails[orderId]) return;
        setLoadingDetails((prev) => ({ ...prev, [orderId]: true }));
        try {
            const order = await getOrderById(orderId);
            setExpandedOrderDetails((prev) => ({ ...prev, [orderId]: order }));
        } finally {
            setLoadingDetails((prev) => ({ ...prev, [orderId]: false }));
        }
    };

    const columns: ColumnsType<OrderSummaryDto> = [
        {
            title: '№ заказа',
            dataIndex: 'orderNumber',
            key: 'orderNumber',
            render: (orderNumber: string) => (
                <span style={{ fontFamily: 'var(--font-mono)', fontSize: 'var(--text-base)', fontWeight: 600, color: 'var(--ink-1)' }}>{orderNumber}</span>
            ),
        },
        {
            title: 'Дата',
            dataIndex: 'createdAt',
            key: 'createdAt',
            width: 180,
            render: (date: string) => <span style={{ fontSize: 'var(--text-base)', color: 'var(--ink-2)' }}>{formatDate(date)}</span>,
        },
        {
            title: 'Статус',
            dataIndex: 'status',
            key: 'status',
            width: 200,
            render: (status: unknown) => {
                const code = extractEnumCode(status);
                return <StatusBadge status={code} />;
            },
        },
        {
            title: 'Позиций',
            dataIndex: 'itemsCount',
            key: 'itemsCount',
            width: 100,
            render: (n: number) => <span style={{ fontSize: 'var(--text-base)', color: 'var(--ink-2)' }}>{n}</span>,
        },
        {
            title: 'Сумма',
            dataIndex: 'totalAmount',
            key: 'totalAmount',
            width: 160,
            render: (amount: number, record) => {
                const items = expandedOrderDetails[record.id]?.items;
                const hasUnpriced = items?.some((i) => !isPriceAvailable(i.price));
                return (
                    <div>
                        <span style={{ fontFamily: 'var(--font-head)', fontWeight: 600, fontSize: 'var(--text-lg)', color: 'var(--ink-1)', fontVariantNumeric: 'tabular-nums' }}>
                            {formatPrice(amount)}
                        </span>
                        {hasUnpriced && (
                            <div style={{ fontSize: 'var(--text-xs)', color: 'var(--ink-3)' }}>* цена части товаров уточняется</div>
                        )}
                    </div>
                );
            },
        },
    ];

    const expandedRowRender = (record: OrderSummaryDto) => {
        const order = expandedOrderDetails[record.id];
        const loading = loadingDetails[record.id];

        if (loading) return <div style={{ textAlign: 'center', padding: 24 }}><Spin /></div>;
        if (!order) return null;

        const itemColumns: ColumnsType<OrderItemDto> = [
            {
                title: 'Товар',
                dataIndex: 'productName',
                key: 'productName',
                render: (name: string, item) => (
                    <Link to={`/products/${item.productId}`} style={{ color: 'var(--brand-navy)', fontWeight: 500, textDecoration: 'none' }}>{name}</Link>
                ),
            },
            {
                title: 'Цена',
                dataIndex: 'price',
                key: 'price',
                width: 130,
                render: (price: number) => <span style={{ fontVariantNumeric: 'tabular-nums' }}>{formatPrice(price)}</span>,
            },
            {
                title: 'Кол-во',
                dataIndex: 'quantity',
                key: 'quantity',
                width: 100,
            },
            {
                title: 'Сумма',
                dataIndex: 'subtotal',
                key: 'subtotal',
                width: 130,
                render: (subtotal: number) => <span style={{ fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>{formatPrice(subtotal)}</span>,
            },
        ];

        const paymentCode = extractEnumCode(order.paymentMethod);
        const deliveryCode = extractEnumCode(order.deliveryMethod);

        return (
            <div style={{ padding: '12px 16px', background: 'var(--surface-2)', borderRadius: 'var(--r-3)' }}>
                <Table<OrderItemDto>
                    columns={itemColumns}
                    dataSource={order.items}
                    rowKey="productId"
                    pagination={false}
                    size="small"
                    style={{ marginBottom: 16 }}
                />
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px 24px', fontSize: 'var(--text-base)' }}>
                    <div style={{ display: 'flex', gap: 8 }}>
                        <span style={{ color: 'var(--ink-3)' }}>Оплата:</span>
                        <span style={{ fontWeight: 500 }}>{PaymentMethodLabels[paymentCode as keyof typeof PaymentMethodLabels] || extractEnumDisplayName(order.paymentMethod)}</span>
                    </div>
                    <div style={{ display: 'flex', gap: 8 }}>
                        <span style={{ color: 'var(--ink-3)' }}>Доставка:</span>
                        <span style={{ fontWeight: 500 }}>{DeliveryMethodLabels[deliveryCode as keyof typeof DeliveryMethodLabels] || extractEnumDisplayName(order.deliveryMethod)}</span>
                    </div>
                    {order.deliveryAddress && (
                        <>
                            <div style={{ display: 'flex', gap: 8 }}>
                                <span style={{ color: 'var(--ink-3)' }}>Получатель:</span>
                                <span style={{ fontWeight: 500 }}>{order.deliveryAddress.recipientName}</span>
                            </div>
                            <div style={{ display: 'flex', gap: 8 }}>
                                <span style={{ color: 'var(--ink-3)' }}>Телефон:</span>
                                <span style={{ fontWeight: 500 }}>{order.deliveryAddress.phone}</span>
                            </div>
                            <div style={{ display: 'flex', gap: 8, gridColumn: 'span 2' }}>
                                <span style={{ color: 'var(--ink-3)' }}>Адрес:</span>
                                <span style={{ fontWeight: 500 }}>
                                    {[order.deliveryAddress.postalCode, order.deliveryAddress.city, order.deliveryAddress.street, `д. ${order.deliveryAddress.building}`, order.deliveryAddress.apartment ? `кв. ${order.deliveryAddress.apartment}` : null].filter(Boolean).join(', ')}
                                </span>
                            </div>
                        </>
                    )}
                    {order.warehousePoint && (
                        <div style={{ display: 'flex', gap: 8, gridColumn: 'span 2' }}>
                            <span style={{ color: 'var(--ink-3)' }}>Самовывоз:</span>
                            <span style={{ fontWeight: 500 }}>{order.warehousePoint.name} — {order.warehousePoint.city}, {order.warehousePoint.street}, д. {order.warehousePoint.building}</span>
                        </div>
                    )}
                    {order.trackingNumber && (
                        <div style={{ display: 'flex', gap: 8, gridColumn: 'span 2' }}>
                            <span style={{ color: 'var(--ink-3)' }}>Трекинг:</span>
                            <span style={{ fontWeight: 500, fontFamily: 'var(--font-mono)' }}>{order.trackingNumber}</span>
                        </div>
                    )}
                    {order.comment && (
                        <div style={{ display: 'flex', gap: 8, gridColumn: 'span 2' }}>
                            <span style={{ color: 'var(--ink-3)' }}>Комментарий:</span>
                            <span>{order.comment}</span>
                        </div>
                    )}
                </div>
                {(extractEnumCode(order.status) === 'CREATED' || extractEnumCode(order.status) === 'PENDING_PAYMENT') && (
                    <div style={{ marginTop: 16, display: 'flex', gap: 8 }}>
                        {extractEnumCode(order.paymentMethod) === PaymentMethod.CARD && (
                            <Button
                                type="primary"
                                icon={<CreditCardOutlined />}
                                loading={payingOrderId === order.id}
                                onClick={() => handlePayCard(order.id)}
                            >
                                Оплатить картой
                            </Button>
                        )}
                        {extractEnumCode(order.paymentMethod) === PaymentMethod.SBP && (
                            <Button
                                type="primary"
                                loading={payingOrderId === order.id}
                                onClick={() => handlePaySbp(order.id, order.orderNumber)}
                            >
                                Оплатить по СБП
                            </Button>
                        )}
                    </div>
                )}
                {CANCELLABLE_STATUSES.includes(extractEnumCode(order.status) as OrderStatus) && (
                    <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
                        {extractEnumCode(order.status) === OrderStatus.CREATED && (
                            <Button onClick={() => setEditingOrder(order)}>
                                Редактировать заказ
                            </Button>
                        )}
                        <Button
                            danger
                            loading={cancellingOrderId === order.id}
                            onClick={() => handleCancelOrder(order.id, order.orderNumber)}
                        >
                            Отменить заказ
                        </Button>
                    </div>
                )}
            </div>
        );
    };

    if (isLoading) return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12, padding: '24px 0' }}>
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} style={{ background: 'var(--surface)', borderRadius: 'var(--r-5)', padding: 20, border: '1px solid var(--line-1)' }}>
            <Skeleton active paragraph={{ rows: 2 }} title={{ width: '40%' }} />
          </div>
        ))}
      </div>
    );

    if (isError) {
        return (
            <div style={{ textAlign: 'center', padding: '80px 0' }}>
                <div style={{ fontFamily: 'var(--font-head)', fontSize: 18, fontWeight: 600, color: 'var(--ink-1)', marginBottom: 8 }}>Не удалось загрузить заказы</div>
            </div>
        );
    }

    if (!ordersPage || ordersPage.empty) {
        return (
            <div style={{ textAlign: 'center', padding: '80px 0' }}>
                <div style={{
                    width: 72, height: 72, borderRadius: '50%', background: 'var(--surface-2)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    margin: '0 auto 20px',
                }}>
                    <ShoppingOutlined style={{ fontSize: 32, color: 'var(--ink-3)' }} />
                </div>
                <div style={{ fontFamily: 'var(--font-head)', fontSize: 22, fontWeight: 600, color: 'var(--ink-1)', marginBottom: 8 }}>
                    Заказов пока нет
                </div>
                <div style={{ fontSize: 'var(--text-md)', color: 'var(--ink-3)', maxWidth: 360, margin: '0 auto 8px' }}>
                    После оформления заказа он появится здесь. Можно отслеживать статус, скачивать документы и повторять заказы.
                </div>
                <div style={{ fontSize: 'var(--text-base)', color: 'var(--ink-4)', marginBottom: 24 }}>
                    Работаем по 44-ФЗ и 223-ФЗ, участвуем в тендерах.
                </div>
                <button
                    onClick={() => navigate('/catalog')}
                    style={{
                        height: 'var(--btn-h-base)', padding: '0 28px', background: 'var(--brand-red)', color: '#fff',
                        border: 'none', borderRadius: 'var(--r-3)', fontSize: 'var(--text-lg)', fontWeight: 500,
                        cursor: 'pointer', fontFamily: 'var(--font-body)',
                    }}
                >
                    Перейти в каталог
                </button>
            </div>
        );
    }

    return (
        <div style={{ paddingTop: 20, paddingBottom: 60 }}>
            <Modal
                open={!!sbpModal}
                onCancel={() => setSbpModal(null)}
                footer={[
                    <Button key="link" onClick={() => { window.location.href = sbpModal!.paymentLink; }}>
                        Открыть ссылку СБП
                    </Button>,
                    <Button key="close" type="primary" onClick={() => setSbpModal(null)}>
                        Закрыть
                    </Button>,
                ]}
                title={`Оплата заказа ${sbpModal?.orderNumber}`}
                centered
            >
                <div style={{ textAlign: 'center', padding: '16px 0' }}>
                    <div style={{ marginBottom: 16, fontSize: 'var(--text-md)', color: 'rgba(0,0,0,0.45)' }}>
                        Отсканируйте QR-кодом в банковском приложении
                    </div>
                    {sbpModal && <QRCode value={sbpModal.paymentLink} size={220} />}
                    <div style={{ marginTop: 12, fontSize: 'var(--text-sm)', color: 'rgba(0,0,0,0.35)' }}>
                        Сумма уже указана — вводить вручную не нужно
                    </div>
                </div>
            </Modal>
            {editingOrder && (
                <EditOrderModal
                    order={editingOrder}
                    open={!!editingOrder}
                    onClose={() => setEditingOrder(null)}
                    onSuccess={(updated) => setExpandedOrderDetails((prev) => ({ ...prev, [updated.id]: updated }))}
                />
            )}
            <h1 style={{ fontFamily: 'var(--font-head)', fontSize: 'var(--text-5xl)', fontWeight: 600, letterSpacing: '-0.02em', color: 'var(--ink-1)', marginBottom: 24 }}>
                Мои заказы
            </h1>

            <div style={{ border: '1px solid var(--line-1)', borderRadius: 'var(--r-4)', background: 'var(--surface)', overflow: 'hidden' }}>
                <Table<OrderSummaryDto>
                    columns={columns}
                    dataSource={ordersPage.content}
                    rowKey="id"
                    pagination={false}
                    expandable={{
                        expandedRowRender,
                        expandRowByClick: true,
                        onExpand: (expanded, record) => { if (expanded) loadOrderDetails(record.id); },
                    }}
                />
            </div>

            {ordersPage.totalPages > 1 && (
                <div style={{ textAlign: 'center', marginTop: 24 }}>
                    <Pagination
                        current={currentPage}
                        total={ordersPage.totalElements}
                        pageSize={pageSize}
                        onChange={setCurrentPage}
                        showTotal={(total) => `Всего ${total} заказов`}
                    />
                </div>
            )}
        </div>
    );
};

export default OrdersPage;
