import { Card, Switch, Typography, App, Spin } from 'antd';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getPaymentSettings, updatePaymentSettings } from '@/api/paymentSettings';
import type { PaymentMethodSettings } from '@/api/paymentSettings';

const { Title, Text } = Typography;

const AdminSettingsPage = () => {
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();

    const { data: settings, isLoading } = useQuery({
        queryKey: ['paymentSettings'],
        queryFn: getPaymentSettings,
    });

    const mutation = useMutation({
        mutationFn: updatePaymentSettings,
        onMutate: async (newSettings) => {
            await queryClient.cancelQueries({ queryKey: ['paymentSettings'] });
            const previous = queryClient.getQueryData<PaymentMethodSettings>(['paymentSettings']);
            queryClient.setQueryData(['paymentSettings'], newSettings);
            return { previous };
        },
        onError: (_err, _vars, context) => {
            if (context?.previous) {
                queryClient.setQueryData(['paymentSettings'], context.previous);
            }
            messageApi.error('Не удалось сохранить настройки');
        },
        onSettled: () => {
            queryClient.invalidateQueries({ queryKey: ['paymentSettings'] });
        },
    });

    const handleToggle = (field: keyof PaymentMethodSettings, value: boolean) => {
        if (!settings) return;
        mutation.mutate({ ...settings, [field]: value });
    };

    if (isLoading) return <Spin />;

    return (
        <div>
            <Title level={2} style={{ marginBottom: 24 }}>Настройки</Title>

            <Card title="Методы оплаты для физлиц" style={{ maxWidth: 520 }}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span>Оплата банковской картой</span>
                        <Switch
                            checked={settings?.cardEnabled ?? false}
                            onChange={(val) => handleToggle('cardEnabled', val)}
                            loading={mutation.isPending}
                        />
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span>Оплата через СБП</span>
                        <Switch
                            checked={settings?.sbpEnabled ?? false}
                            onChange={(val) => handleToggle('sbpEnabled', val)}
                            loading={mutation.isPending}
                        />
                    </div>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                        Если оба метода выключены — заказы физлиц передаются менеджеру через 1С
                    </Text>
                </div>
            </Card>
        </div>
    );
};

export default AdminSettingsPage;
