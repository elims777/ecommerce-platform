import { Alert, Form, Input, Select } from 'antd';
import { PaymentMethod } from '@/types/order';
import type { PaymentMethodSettings } from '@/api/paymentSettings';

const { TextArea } = Input;

interface PaymentStepProps {
    settings: PaymentMethodSettings | undefined;
    isB2C: boolean;
}

const PaymentStep = ({ settings, isB2C }: PaymentStepProps) => {
    const availableMethods = (() => {
        if (!isB2C) {
            return [PaymentMethod.CARD, PaymentMethod.SBP];
        }
        if (!settings) return [];
        const methods: PaymentMethod[] = [];
        if (settings.cardEnabled) methods.push(PaymentMethod.CARD);
        if (settings.sbpEnabled) methods.push(PaymentMethod.SBP);
        return methods;
    })();

    const labels: Record<string, string> = {
        [PaymentMethod.CARD]: 'Банковская карта',
        [PaymentMethod.SBP]: 'Система быстрых платежей (СБП)',
    };

    const noOnlinePayment = !isB2C || (settings !== undefined && availableMethods.length === 0);

    return (
        <>
            {noOnlinePayment ? (
                <>
                    {/* Скрытое поле — значение INVOICE ставится через useEffect в CheckoutPage */}
                    <Form.Item name="paymentMethod" hidden>
                        <Input />
                    </Form.Item>
                    <Alert
                        type="info"
                        showIcon
                        message="Заказ будет передан менеджеру — счёт выставят отдельно"
                        style={{ marginBottom: 16 }}
                    />
                </>
            ) : availableMethods.length === 1 ? (
                <Form.Item name="paymentMethod" label="Способ оплаты">
                    <Select size="large">
                        <Select.Option value={availableMethods[0]}>
                            {labels[availableMethods[0]]}
                        </Select.Option>
                    </Select>
                </Form.Item>
            ) : availableMethods.length > 1 ? (
                <Form.Item
                    name="paymentMethod"
                    label="Способ оплаты"
                    rules={[{ required: true, message: 'Выберите способ оплаты' }]}
                >
                    <Select size="large" placeholder="Выберите способ оплаты">
                        {availableMethods.map((method) => (
                            <Select.Option key={method} value={method}>
                                {labels[method]}
                            </Select.Option>
                        ))}
                    </Select>
                </Form.Item>
            ) : (
                // settings ещё не загружены — показываем пустой Select
                <Form.Item
                    name="paymentMethod"
                    label="Способ оплаты"
                    rules={[{ required: true, message: 'Выберите способ оплаты' }]}
                >
                    <Select size="large" loading placeholder="Загрузка…" />
                </Form.Item>
            )}

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
};

export default PaymentStep;
