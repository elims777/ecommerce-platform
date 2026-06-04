import { useState } from 'react';

interface DeliveryMethod {
    id: number;
    name: string;
    description: string;
    enabled: boolean;
    minDays: number;
    maxDays: number;
    price: number | null;
}

const INITIAL_METHODS: DeliveryMethod[] = [
    { id: 1, name: 'Самовывоз', description: 'Г. Москва, ул. Промышленная, д. 12, склад №3', enabled: true, minDays: 0, maxDays: 0, price: 0 },
    { id: 2, name: 'Доставка по Москве', description: 'Курьером до адреса в пределах МКАД', enabled: true, minDays: 1, maxDays: 2, price: 500 },
    { id: 3, name: 'Доставка по МО', description: 'Курьером до адреса в Московской области', enabled: true, minDays: 2, maxDays: 3, price: 900 },
    { id: 4, name: 'Доставка по России (СДЭК)', description: 'Через службу доставки СДЭК', enabled: false, minDays: 3, maxDays: 7, price: null },
    { id: 5, name: 'Доставка по России (Деловые Линии)', description: 'Через службу доставки Деловые Линии', enabled: false, minDays: 3, maxDays: 10, price: null },
];

const inputStyle: React.CSSProperties = {
    height: 34, border: '1px solid var(--line-2)', borderRadius: 5,
    fontFamily: 'var(--font-body)', fontSize: 13, padding: '0 10px',
    width: '100%', outline: 'none', background: '#fff',
};

const LogisticsPage = () => {
    const [methods, setMethods] = useState<DeliveryMethod[]>(INITIAL_METHODS);
    const [editId, setEditId] = useState<number | null>(null);
    const [saved, setSaved] = useState(false);

    const toggleEnabled = (id: number) => {
        setMethods(prev => prev.map(m => m.id === id ? { ...m, enabled: !m.enabled } : m));
        flashSaved();
    };

    const updateField = (id: number, field: keyof DeliveryMethod, value: string | number | boolean | null) => {
        setMethods(prev => prev.map(m => m.id === id ? { ...m, [field]: value } : m));
    };

    const flashSaved = () => {
        setSaved(true);
        setTimeout(() => setSaved(false), 2000);
    };

    return (
        <div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
                <h2 style={{ fontFamily: 'var(--font-head)', fontSize: 22, fontWeight: 600, margin: 0 }}>
                    Логистика
                </h2>
                {saved && (
                    <span style={{ fontSize: 13, color: 'var(--success)', display: 'flex', alignItems: 'center', gap: 5 }}>
                        <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                            <circle cx="7" cy="7" r="6.5" stroke="currentColor" strokeWidth="1.2"/>
                            <path d="M4.5 7l2 2 3-3" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round"/>
                        </svg>
                        Изменения сохранены
                    </span>
                )}
            </div>

            <div style={{ background: 'var(--navy-tint)', border: '1px solid var(--brand-navy)', borderRadius: 'var(--r-3)', padding: '14px 18px', marginBottom: 24, display: 'flex', gap: 12, alignItems: 'flex-start', color: 'var(--brand-navy)', fontSize: 13 }}>
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none" style={{ flexShrink: 0, marginTop: 1 }}>
                    <circle cx="8" cy="8" r="7.5" stroke="currentColor"/>
                    <path d="M8 7v4M8 5.5v.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
                </svg>
                <span>Настройки доставки отображаются клиентам на странице оформления заказа. Отключённые способы скрыты, но данные сохраняются.</span>
            </div>

            <div className="rf-card">
                <div className="rf-card-header"><h3>Способы доставки</h3></div>
                <div className="rf-card-body" style={{ padding: 0 }}>
                    {methods.map((method, i) => (
                        <div
                            key={method.id}
                            style={{
                                padding: '14px 18px',
                                borderBottom: i < methods.length - 1 ? '1px solid var(--line-1)' : 'none',
                                display: 'flex', alignItems: 'flex-start', gap: 14,
                                opacity: method.enabled ? 1 : 0.55,
                                transition: 'opacity 0.15s',
                            }}
                        >
                            {/* Тогл */}
                            <button
                                onClick={() => toggleEnabled(method.id)}
                                style={{
                                    width: 38, height: 22, borderRadius: 11, border: 'none', cursor: 'pointer',
                                    background: method.enabled ? 'var(--brand-red)' : 'var(--line-2)',
                                    position: 'relative', flexShrink: 0, marginTop: 2,
                                    transition: 'background 0.15s',
                                }}
                                title={method.enabled ? 'Отключить' : 'Включить'}
                            >
                                <span style={{
                                    display: 'block', width: 16, height: 16, borderRadius: '50%', background: '#fff',
                                    position: 'absolute', top: 3,
                                    left: method.enabled ? 19 : 3,
                                    transition: 'left 0.15s',
                                    boxShadow: '0 1px 3px rgba(0,0,0,.2)',
                                }} />
                            </button>

                            {/* Данные */}
                            <div style={{ flex: 1 }}>
                                {editId === method.id ? (
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                                            <div>
                                                <label style={{ fontSize: 11, color: 'var(--ink-3)', display: 'block', marginBottom: 3 }}>Название</label>
                                                <input style={inputStyle} value={method.name}
                                                    onChange={e => updateField(method.id, 'name', e.target.value)} />
                                            </div>
                                            <div>
                                                <label style={{ fontSize: 11, color: 'var(--ink-3)', display: 'block', marginBottom: 3 }}>Стоимость (₽, пусто = по запросу)</label>
                                                <input style={inputStyle} type="number" min={0}
                                                    value={method.price ?? ''}
                                                    onChange={e => updateField(method.id, 'price', e.target.value === '' ? null : Number(e.target.value))} />
                                            </div>
                                        </div>
                                        <div>
                                            <label style={{ fontSize: 11, color: 'var(--ink-3)', display: 'block', marginBottom: 3 }}>Описание / адрес</label>
                                            <input style={inputStyle} value={method.description}
                                                onChange={e => updateField(method.id, 'description', e.target.value)} />
                                        </div>
                                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                                            <div>
                                                <label style={{ fontSize: 11, color: 'var(--ink-3)', display: 'block', marginBottom: 3 }}>Срок от (дней)</label>
                                                <input style={inputStyle} type="number" min={0} value={method.minDays}
                                                    onChange={e => updateField(method.id, 'minDays', Number(e.target.value))} />
                                            </div>
                                            <div>
                                                <label style={{ fontSize: 11, color: 'var(--ink-3)', display: 'block', marginBottom: 3 }}>Срок до (дней)</label>
                                                <input style={inputStyle} type="number" min={0} value={method.maxDays}
                                                    onChange={e => updateField(method.id, 'maxDays', Number(e.target.value))} />
                                            </div>
                                        </div>
                                        <div style={{ display: 'flex', gap: 8 }}>
                                            <button className="rf-btn rf-btn-primary rf-btn-sm"
                                                onClick={() => { setEditId(null); flashSaved(); }}>
                                                Сохранить
                                            </button>
                                            <button className="rf-btn rf-btn-sm"
                                                onClick={() => setEditId(null)}>
                                                Отмена
                                            </button>
                                        </div>
                                    </div>
                                ) : (
                                    <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
                                        <div style={{ flex: 1 }}>
                                            <div style={{ fontWeight: 600, fontSize: 14, color: 'var(--ink-1)', marginBottom: 2 }}>
                                                {method.name}
                                            </div>
                                            <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>{method.description}</div>
                                            <div style={{ display: 'flex', gap: 12, marginTop: 6, fontSize: 12, color: 'var(--ink-2)' }}>
                                                <span>
                                                    {method.minDays === 0 && method.maxDays === 0
                                                        ? 'Без ожидания'
                                                        : `${method.minDays}–${method.maxDays} дн.`}
                                                </span>
                                                <span style={{ color: 'var(--ink-3)' }}>·</span>
                                                <span>
                                                    {method.price === 0 ? 'Бесплатно' : method.price != null ? `${method.price} ₽` : 'По запросу'}
                                                </span>
                                            </div>
                                        </div>
                                        <button className="rf-btn rf-btn-sm"
                                            onClick={() => setEditId(method.id)}
                                            style={{ flexShrink: 0 }}>
                                            Изменить
                                        </button>
                                    </div>
                                )}
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
};

export default LogisticsPage;
