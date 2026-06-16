import { Button, Typography, Divider, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { CartItemDto } from '@/api/cart';
import { useAuthStore } from '@/store/authStore';

const { Title, Text } = Typography;

const formatPrice = (price: number): string =>
    new Intl.NumberFormat('ru-RU', {
        style: 'currency',
        currency: 'RUB',
        minimumFractionDigits: 0,
        maximumFractionDigits: 2,
    }).format(price);

const columns: ColumnsType<CartItemDto> = [
    { title: 'Товар', dataIndex: 'productName', key: 'productName' },
    { title: 'Кол-во', dataIndex: 'quantity', key: 'quantity', width: 80 },
    {
        title: 'Сумма',
        dataIndex: 'subtotal',
        key: 'subtotal',
        width: 130,
        render: (subtotal: number) => formatPrice(subtotal),
    },
];

interface SummaryStepProps {
    items: CartItemDto[];
    totalAmount: number;
    loading: boolean;
}

const SummaryStep = ({ items, totalAmount, loading }: SummaryStepProps) => {
    const user = useAuthStore((s) => s.user);
    return (
    <>
        {user?.clientType === 'B2B' && user.companyName && (
            <div style={{ marginBottom: 16, padding: 12, background: 'var(--surface-2)', borderRadius: 6, border: '1px solid var(--line-1)' }}>
                <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-3)', marginBottom: 6, textTransform: 'uppercase', letterSpacing: '0.04em' }}>Покупатель</div>
                <div style={{ fontSize: 14, fontWeight: 500 }}>{user.companyName}</div>
                {user.inn && <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>ИНН: {user.inn}</div>}
            </div>
        )}
        <Table<CartItemDto>
            columns={columns}
            dataSource={items}
            rowKey="productId"
            pagination={false}
            size="small"
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
            <Title level={3} style={{ margin: 0, color: '#1677ff' }}>
                {formatPrice(totalAmount)}
            </Title>
        </div>

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
