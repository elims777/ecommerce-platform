import { Button, Typography, Divider, Table, Form } from 'antd';
import type { ProductStock, Warehouse } from '@/types/product';
import type { ColumnsType } from 'antd/es/table';
import type { CartItemDto } from '@/api/cart';
import { NavLink } from '@/components/navigation';
import { useAuthStore } from '@/store/authStore';
import { formatPriceOrPlaceholder, isPriceAvailable } from '@/utils/priceUtils';

const { Title, Text } = Typography;

const formatPrice = formatPriceOrPlaceholder;

interface SummaryStepProps {
    items: CartItemDto[];
    totalAmount: number;
    loading: boolean;
    warehouses: Warehouse[];
    cartStocks: Record<number, ProductStock[]> | undefined;
}

const SummaryStep = ({ items, totalAmount, loading, warehouses, cartStocks }: SummaryStepProps) => {
    const user = useAuthStore((s) => s.user);
    const warehouseId = Form.useWatch('shippingWarehouseId');
    const warehouse = warehouses.find((w) => w.id === warehouseId);
    // Пока остатки не загрузились, не помечаем «под заказ»
    const isBackorder = (item: CartItemDto) => !!warehouse && !!cartStocks
        && (cartStocks[item.productId]?.find((s) => s.warehouseId === warehouse.id)?.quantity ?? 0) < item.quantity;
    const columns: ColumnsType<CartItemDto> = [
        {
            title: 'Товар',
            dataIndex: 'productName',
            key: 'productName',
            render: (name: string, item) => (
                <>
                    <NavLink to={`/products/${item.parentProductId ?? item.productId}`}>{name}</NavLink>
                    {isBackorder(item) && (
                        <span style={{ marginLeft: 8, fontSize: 'var(--text-sm)', color: 'var(--brand-red)' }}>под заказ</span>
                    )}
                </>
            ),
        },
        { title: 'Кол-во', dataIndex: 'quantity', key: 'quantity', width: 80 },
        {
            title: 'Сумма',
            dataIndex: 'subtotal',
            key: 'subtotal',
            width: 130,
            render: (subtotal: number) => formatPrice(subtotal),
        },
    ];
    const hasUnpricedItems = items.some((i) => !isPriceAvailable(i.price));
    return (
    <>
        {user?.clientType === 'B2B' && user.companyName && (
            <div style={{ marginBottom: 16, padding: 12, background: 'var(--surface-2)', borderRadius: 6, border: '1px solid var(--line-1)' }}>
                <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-3)', marginBottom: 6, textTransform: 'uppercase', letterSpacing: '0.04em' }}>Покупатель</div>
                <div style={{ fontSize: 14, fontWeight: 500 }}>{user.companyName}</div>
                {user.inn && <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>ИНН: {user.inn}</div>}
            </div>
        )}
        {warehouse && (
            <div style={{ marginBottom: 12, fontSize: 'var(--text-base)' }}>
                <span style={{ color: 'var(--ink-3)' }}>Город отгрузки: </span>
                <span style={{ fontWeight: 500 }}>{warehouse.city}</span>
            </div>
        )}
        <Table<CartItemDto>
            columns={columns}
            dataSource={items}
            rowKey="productId"
            pagination={false}
            size="small"
            scroll={{ x: 'max-content' }}
            style={{ marginBottom: 16 }}
        />

        <Divider />

        <div
            style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginBottom: 16,
            }}
        >
            <Text style={{ fontSize: 16 }}>Итого:</Text>
            <Title level={3} style={{ margin: 0, color: 'var(--brand-red)' }}>
                {formatPrice(totalAmount)}
            </Title>
        </div>
        {hasUnpricedItems && (
            <div style={{ marginBottom: 12, fontSize: 'var(--text-sm)', color: 'var(--ink-3)' }}>
                * цена части товаров уточняется
            </div>
        )}

        <Button
            type="primary"
            htmlType="submit"
            size="large"
            loading={loading}
            block
        >
            Подтвердить заказ
        </Button>
        <div style={{ marginTop: 10, textAlign: 'center', fontSize: 'var(--text-sm)', color: 'var(--ink-3)' }}>
            Заказ будет передан менеджеру
        </div>
    </>
    );
};

export default SummaryStep;
