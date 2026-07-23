import { Alert, Spin, App } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { getMyPriceLists, downloadPriceList } from '@/api/priceLists';
import type { PriceListResponse, PriceListStatus } from '@/api/priceLists';
import { isAxiosError } from 'axios';

const sectionStyle: React.CSSProperties = {
    border: '1px solid var(--line-1)', borderRadius: 'var(--r-4)',
    background: 'var(--surface)', overflow: 'hidden', marginBottom: 16,
};
const rowStyle: React.CSSProperties = {
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    padding: '16px 20px', borderBottom: '1px solid var(--line-1)', gap: 16,
};
const rowLast: React.CSSProperties = { ...rowStyle, borderBottom: 'none' };

const formatDate = (dateStr: string): string =>
    new Date(dateStr).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });

const STATUS_CFG: Record<PriceListStatus, { label: string; bg: string; color: string }> = {
    PENDING: { label: 'Формируется…', bg: 'var(--warn-tint)', color: 'var(--warn)' },
    READY: { label: 'Готов', bg: 'var(--brand-green-soft)', color: 'var(--brand-green)' },
    FAILED: { label: 'Ошибка', bg: 'var(--red-tint)', color: 'var(--brand-red)' },
    EXPIRED: { label: 'Истёк', bg: 'var(--surface-3)', color: 'var(--ink-3)' },
};

const StatusBadge = ({ status }: { status: PriceListStatus }) => {
    const { label, bg, color } = STATUS_CFG[status];
    return (
        <span style={{
            display: 'inline-flex', alignItems: 'center', gap: 6,
            height: 22, padding: '0 8px', borderRadius: 11,
            fontSize: 'var(--text-sm)', fontWeight: 500,
            background: bg, color,
        }}>
            {status === 'PENDING' && <Spin size="small" />}
            {label}
        </span>
    );
};

const PriceListRow = ({ item, last }: { item: PriceListResponse; last?: boolean }) => {
    const { message: messageApi } = App.useApp();

    const handleDownload = async () => {
        try {
            const blob = await downloadPriceList(item.id);
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `price-list-${item.createdAt.slice(0, 10)}.xlsx`;
            document.body.appendChild(a);
            a.click();
            a.remove();
            URL.revokeObjectURL(url);
        } catch (err) {
            if (isAxiosError(err) && err.response?.status === 409) {
                messageApi.warning('Прайс ещё не готов.');
            } else {
                messageApi.error('Не удалось скачать прайс-лист.');
            }
        }
    };

    return (
        <div style={last ? rowLast : rowStyle}>
            <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 'var(--text-md)', fontWeight: 500, color: 'var(--ink-1)', marginBottom: 4 }}>
                    {item.categoryNames.join(', ') || 'Все категории'}
                </div>
                <div style={{ fontSize: 'var(--text-sm)', color: 'var(--ink-3)' }}>
                    {formatDate(item.createdAt)}
                    {item.status === 'READY' && item.rowCount != null && ` · ${item.rowCount} позиций`}
                    {item.status === 'FAILED' && ' · Попробуйте сформировать заново'}
                </div>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexShrink: 0 }}>
                <StatusBadge status={item.status} />
                {item.status === 'READY' && (
                    <button
                        onClick={handleDownload}
                        style={{
                            height: 'var(--btn-h-md)', padding: '0 14px', background: 'var(--brand-red)', color: '#fff',
                            border: 'none', borderRadius: 'var(--r-3)', fontSize: 'var(--text-base)', fontWeight: 500,
                            cursor: 'pointer', fontFamily: 'var(--font-body)',
                        }}
                    >
                        Скачать
                    </button>
                )}
            </div>
        </div>
    );
};

const PriceListsPage = () => {
    const { data: priceLists = [], isLoading } = useQuery({
        queryKey: ['priceLists'],
        queryFn: getMyPriceLists,
        refetchInterval: (query) => {
            const hasPending = query.state.data?.some((p) => p.status === 'PENDING');
            return hasPending ? 4000 : false;
        },
    });

    return (
        <div style={{ maxWidth: 700, margin: '0 auto', paddingTop: 20, paddingBottom: 60 }}>
            <h1 style={{ fontFamily: 'var(--font-head)', fontSize: 'var(--text-5xl)', fontWeight: 600, letterSpacing: '-0.02em', color: 'var(--ink-1)', marginBottom: 24 }}>
                Прайс-листы
            </h1>

            <Alert
                type="info"
                showIcon
                style={{ marginBottom: 16 }}
                message="Готовые прайс-листы хранятся 3 дня, затем удаляются. При необходимости сформируйте новый — цены будут актуальными."
            />

            {isLoading ? (
                <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 60 }}>
                    <Spin size="large" />
                </div>
            ) : priceLists.length === 0 ? (
                <div style={sectionStyle}>
                    <div style={{ padding: 20, color: 'var(--ink-3)', textAlign: 'center' }}>
                        Вы ещё не заказывали прайс-листы.
                    </div>
                </div>
            ) : (
                <div style={sectionStyle}>
                    {priceLists.map((item, i) => (
                        <PriceListRow key={item.id} item={item} last={i === priceLists.length - 1} />
                    ))}
                </div>
            )}
        </div>
    );
};

export default PriceListsPage;
