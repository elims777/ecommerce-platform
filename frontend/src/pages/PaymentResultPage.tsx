import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Result, Spin } from 'antd';
import { NavLink } from '@/components/navigation';
import { getPaymentStatus } from '@/api/orders';

const PaymentResultPage = () => {
    const [searchParams] = useSearchParams();
    const orderId = searchParams.get('orderId');
    const [status, setStatus] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        if (!orderId) {
            setStatus('error');
            setLoading(false);
            return;
        }
        getPaymentStatus(orderId)
            .then(({ status: s }) => setStatus(s))
            .catch(() => setStatus('error'))
            .finally(() => setLoading(false));
    }, [orderId]);

    if (loading) {
        return (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
                <Spin size="large" />
            </div>
        );
    }

    const resultStatus = status === 'APPROVED' ? 'success' : status === 'FAILED' ? 'error' : 'warning';

    const titles: Record<string, string> = {
        success: 'Оплата прошла успешно',
        error: 'Ошибка оплаты',
        warning: 'Платёж обрабатывается',
    };

    const subtitles: Record<string, string> = {
        success: 'Заказ оплачен и передан в обработку. Мы уведомим вас по email.',
        error: 'Не удалось провести оплату. Попробуйте ещё раз или выберите другой способ оплаты.',
        warning: 'Платёж ещё обрабатывается. Статус заказа обновится автоматически.',
    };

    return (
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh', padding: '40px 16px' }}>
            <Result
                status={resultStatus}
                title={titles[resultStatus]}
                subTitle={subtitles[resultStatus]}
                extra={[
                    <NavLink key="orders" to="/orders" variant="button-primary">
                        Мои заказы
                    </NavLink>,
                    <NavLink key="catalog" to="/catalog" variant="button-secondary">
                        В каталог
                    </NavLink>,
                ]}
            />
        </div>
    );
};

export default PaymentResultPage;
