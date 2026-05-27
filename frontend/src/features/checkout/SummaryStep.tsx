import { Button, Typography, Divider, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { CartItemDto } from '@/api/cart';

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

const SummaryStep = ({ items, totalAmount, loading }: SummaryStepProps) => (
    <>
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
    </>
);

export default SummaryStep;
