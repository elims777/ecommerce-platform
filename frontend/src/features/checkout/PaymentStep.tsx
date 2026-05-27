import { Form, Input, Select } from 'antd';
import { PaymentMethodLabels } from '@/types/order';

const { TextArea } = Input;

const PaymentStep = () => (
    <>
        <Form.Item
            name="paymentMethod"
            rules={[{ required: true, message: 'Выберите способ оплаты' }]}
        >
            <Select size="large">
                {Object.entries(PaymentMethodLabels).map(([key, label]) => (
                    <Select.Option key={key} value={key}>
                        {label}
                    </Select.Option>
                ))}
            </Select>
        </Form.Item>

        <Form.Item name="comment" label="Комментарий к заказу">
            <TextArea
                rows={3}
                placeholder="Дополнительные пожелания по заказу..."
                maxLength={500}
                showCount
            />
        </Form.Item>
    </>
);

export default PaymentStep;
