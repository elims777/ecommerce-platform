import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import apiClient from '@/api/client';

interface FtkImportResult {
    totalProducts: number;
    created: number;
    updated: number;
    failed: number;
    imagesOk: number;
    imagesFailed: number;
}

interface ImportLogEntry {
    id: number;
    exchangeType: string;
    status: 'SUCCESS' | 'PARTIAL' | 'FAILED';
    totalReceived: number;
    created: number;
    updated: number;
    failed: number;
    imagesProcessed: number;
    imagesFailed: number;
    durationMs: number | null;
    errorMessage: string | null;
    createdAt: string;
}

const STATUS_BADGE: Record<string, { label: string; className: string }> = {
    SUCCESS: { label: 'Успешно',  className: 'rf-badge rf-badge-success' },
    PARTIAL: { label: 'Частично', className: 'rf-badge rf-badge-warn' },
    FAILED:  { label: 'Ошибка',   className: 'rf-badge rf-badge-red' },
};

const TYPE_LABEL: Record<string, string> = {
    CATALOG:      'Каталог 1С',
    OFFERS:       'Цены/остатки',
    ORDER_STATUS: 'Статусы из 1С',
    FTK_CATALOG:  'Каталог ФТК',
    ORDER_EXPORT: 'Заказы → 1С',
};

function formatDate(iso: string) {
    return new Date(iso).toLocaleString('ru-RU', {
        day: '2-digit', month: '2-digit', year: '2-digit',
        hour: '2-digit', minute: '2-digit',
    });
}

const IntegrationPage = () => {
    const navigate = useNavigate();
    const [logs, setLogs] = useState<ImportLogEntry[]>([]);
    const [logsLoading, setLogsLoading] = useState(true);

    const [ftkLoading, setFtkLoading] = useState(false);
    const [ftkResult, setFtkResult] = useState<FtkImportResult | null>(null);
    const [ftkError, setFtkError] = useState<string | null>(null);

    const handleFtkImport = async () => {
        setFtkLoading(true);
        setFtkResult(null);
        setFtkError(null);
        try {
            const r = await apiClient.post<FtkImportResult>('/v1/integration/ftk/import-xml');
            setFtkResult(r.data);
        } catch (e: any) {
            setFtkError(e?.response?.data?.message ?? 'Ошибка импорта');
        } finally {
            setFtkLoading(false);
        }
    };

    useEffect(() => {
        const fetchLogs = (isInitial: boolean = false) => {
            return apiClient.get<ImportLogEntry[]>('/v1/admin/integration/logs')
                .then(r => setLogs(r.data))
                .catch(e => console.warn('logs fetch failed', e))
                .finally(() => { if (isInitial) setLogsLoading(false); });
        };

        fetchLogs(true);

        const interval = setInterval(() => {
            if (document.visibilityState === 'visible') fetchLogs();
        }, 10000);

        return () => clearInterval(interval);
    }, []);

    return (
        <div>
            <h2 style={{ fontFamily: 'var(--font-head)', fontSize: 'var(--text-3xl)', fontWeight: 600, marginBottom: 20 }}>
                Интеграция 1С
            </h2>

            <div style={{ background: 'var(--navy-tint)', border: '1px solid var(--brand-navy)', borderRadius: 'var(--r-3)', padding: '14px 18px', marginBottom: 24, display: 'flex', gap: 12, alignItems: 'flex-start', color: 'var(--brand-navy)', fontSize: 'var(--text-base)' }}>
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none" style={{ flexShrink: 0, marginTop: 1 }}>
                    <circle cx="8" cy="8" r="7.5" stroke="currentColor"/>
                    <path d="M8 7v4M8 5.5v.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
                </svg>
                <span>Обмен данными с 1С настроен через протокол CommerceML 2.10. Товары из 1С попадают в категорию «Импорт из 1С» и требуют ручного распределения по категориям и активации.</span>
            </div>

            {/* Импорт ФТК */}
            <div className="rf-card" style={{ marginBottom: 24 }}>
                <div className="rf-card-header">
                    <svg width="15" height="15" viewBox="0 0 15 15" fill="none" style={{ marginRight: 6 }}>
                        <path d="M7.5 13V3M3 7l4.5-4.5L12 7" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                    <h3>Импорт каталога ФТК (Факел)</h3>
                </div>
                <div className="rf-card-body">
                    <p style={{ margin: '0 0 14px', fontSize: 'var(--text-base)', color: 'var(--ink-2)', lineHeight: 1.6 }}>
                        Импорт каталога с FTP ФТК по протоколу CommerceML XML. Товары попадут в категорию <strong>ФТК — Факел</strong>.
                    </p>

                    <button
                        className="rf-btn rf-btn-primary"
                        onClick={handleFtkImport}
                        disabled={ftkLoading}
                        style={{ opacity: ftkLoading ? 0.6 : 1 }}
                    >
                        {ftkLoading ? 'Импорт...' : 'Запустить импорт'}
                    </button>

                    {ftkLoading && (
                        <div style={{ marginTop: 14, fontSize: 'var(--text-base)', color: 'var(--ink-3)' }}>
                            Идёт импорт, это может занять несколько минут...
                        </div>
                    )}

                    {ftkError && (
                        <div style={{ marginTop: 14, padding: '10px 14px', borderRadius: 'var(--r-2)', background: 'var(--red-tint)', border: '1px solid var(--brand-red)', color: 'var(--brand-red)', fontSize: 'var(--text-base)' }}>
                            {ftkError}
                        </div>
                    )}

                    {ftkResult && (
                        <div style={{ marginTop: 14, padding: '12px 16px', borderRadius: 'var(--r-2)', background: 'var(--green-tint)', border: '1px solid var(--success)', fontSize: 'var(--text-base)' }}>
                            <div style={{ fontWeight: 600, color: 'var(--success)', marginBottom: 8 }}>Импорт завершён</div>
                            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '6px 24px', color: 'var(--ink-2)' }}>
                                <span>Товаров обработано: <strong>{ftkResult.totalProducts}</strong></span>
                                <span>Создано: <strong style={{ color: 'var(--success)' }}>{ftkResult.created}</strong></span>
                                <span>Обновлено: <strong>{ftkResult.updated}</strong></span>
                                <span>Ошибок: <strong style={{ color: ftkResult.failed > 0 ? 'var(--brand-red)' : 'inherit' }}>{ftkResult.failed}</strong></span>
                                <span>Изображений: <strong style={{ color: 'var(--success)' }}>{ftkResult.imagesOk}</strong></span>
                                <span>Ошибок фото: <strong style={{ color: ftkResult.imagesFailed > 0 ? 'var(--brand-red)' : 'inherit' }}>{ftkResult.imagesFailed}</strong></span>
                            </div>
                        </div>
                    )}
                </div>
            </div>

            <div className="rf-card" style={{ marginBottom: 24 }}>
                <div className="rf-card-header">
                    <svg width="15" height="15" viewBox="0 0 15 15" fill="none" style={{ marginRight: 6 }}>
                        <path d="M7.5 1C4 1 1 4 1 7.5S4 14 7.5 14 14 11 14 7.5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round"/>
                        <path d="M11 1l1.5 1.5L11 4M12.5 2.5H9" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                    <h3>Настройки обмена</h3>
                </div>
                <div className="rf-detail-grid">
                    <div className="rf-detail-label">URL обмена</div>
                    <div className="rf-detail-value" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <code className="rf-mono" style={{ fontSize: 'var(--text-sm)', background: 'var(--surface-2)', padding: '2px 6px', borderRadius: 'var(--r-2)' }}>
                            http://ВАШ_ДОМЕН:8085/1c-exchange
                        </code>
                        <button
                            onClick={() => navigator.clipboard.writeText('http://ВАШ_ДОМЕН:8085/1c-exchange')}
                            style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '2px 4px', color: 'var(--ink-3)', fontSize: 'var(--text-sm)' }}
                            title="Скопировать"
                        >
                            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                                <rect x="4.5" y="4.5" width="8" height="8" rx="1.5" stroke="currentColor" strokeWidth="1.2"/>
                                <path d="M2.5 9.5H2a1 1 0 0 1-1-1V2a1 1 0 0 1 1-1h6.5a1 1 0 0 1 1 1v.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round"/>
                            </svg>
                        </button>
                    </div>

                    <div className="rf-detail-label">Протокол</div>
                    <div className="rf-detail-value">CommerceML 2.08</div>

                    <div className="rf-detail-label">Авторизация</div>
                    <div className="rf-detail-value">Basic Auth (логин/пароль из .env)</div>

                    <div className="rf-detail-label">Статус</div>
                    <div className="rf-detail-value">
                        <span className="rf-badge rf-badge-success">Активен</span>
                    </div>
                </div>
            </div>

            {/* История обменов */}
            <div className="rf-card" style={{ marginBottom: 24 }}>
                <div className="rf-card-header">
                    <svg width="15" height="15" viewBox="0 0 15 15" fill="none" style={{ marginRight: 6 }}>
                        <circle cx="7.5" cy="7.5" r="6.5" stroke="currentColor" strokeWidth="1.4"/>
                        <path d="M7.5 4v3.5l2 2" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round"/>
                    </svg>
                    <h3>История обменов</h3>
                </div>
                <div className="rf-card-body" style={{ padding: 0 }}>
                    {logsLoading ? (
                        <div style={{ padding: '24px 18px', textAlign: 'center', fontSize: 'var(--text-base)', color: 'var(--ink-3)' }}>Загрузка...</div>
                    ) : logs.length === 0 ? (
                        <div style={{ padding: '24px 18px', textAlign: 'center', fontSize: 'var(--text-base)', color: 'var(--ink-3)' }}>
                            Обменов пока не было. История появится после первой синхронизации с 1С.
                        </div>
                    ) : (
                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 'var(--text-base)' }}>
                            <thead>
                                <tr style={{ background: 'var(--surface-2)', borderBottom: '1px solid var(--line-1)' }}>
                                    {['Дата', 'Тип', 'Статус', 'Получено', 'Создано', 'Обновлено', 'Ошибок', 'Картинок', 'Ошибок фото', 'Время'].map(h => (
                                        <th key={h} style={{ padding: '8px 14px', textAlign: 'left', fontWeight: 500, color: 'var(--ink-2)', whiteSpace: 'nowrap' }}>{h}</th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {logs.map((log, i) => {
                                    const badge = STATUS_BADGE[log.status] ?? { label: log.status, className: 'rf-badge' };
                                    return (
                                        <tr key={log.id} style={{ borderBottom: i < logs.length - 1 ? '1px solid var(--line-1)' : 'none' }}>
                                            <td style={{ padding: '9px 14px', whiteSpace: 'nowrap', color: 'var(--ink-2)' }}>{formatDate(log.createdAt)}</td>
                                            <td style={{ padding: '9px 14px' }}>{TYPE_LABEL[log.exchangeType] ?? log.exchangeType}</td>
                                            <td style={{ padding: '9px 14px' }}>
                                                <span className={badge.className}>{badge.label}</span>
                                                {log.status === 'FAILED' && log.errorMessage && (
                                                    <span title={log.errorMessage} style={{ marginLeft: 6, cursor: 'help', color: 'var(--ink-3)', fontSize: 'var(--text-xs)' }}>ℹ</span>
                                                )}
                                            </td>
                                            <td style={{ padding: '9px 14px', textAlign: 'center' }}>{log.totalReceived}</td>
                                            <td style={{ padding: '9px 14px', textAlign: 'center', color: 'var(--success)' }}>{log.created}</td>
                                            <td style={{ padding: '9px 14px', textAlign: 'center' }}>{log.updated}</td>
                                            <td style={{ padding: '9px 14px', textAlign: 'center', color: log.failed > 0 ? 'var(--brand-red)' : 'inherit' }}>{log.failed}</td>
                                            <td style={{ padding: '9px 14px', textAlign: 'center', color: 'var(--ink-3)' }}>{log.imagesProcessed > 0 ? log.imagesProcessed : '—'}</td>
                                            <td style={{ padding: '9px 14px', textAlign: 'center', color: log.imagesFailed > 0 ? 'var(--brand-red)' : 'var(--ink-3)' }}>{log.imagesFailed > 0 ? log.imagesFailed : '—'}</td>
                                            <td style={{ padding: '9px 14px', color: 'var(--ink-3)' }}>{log.durationMs != null ? `${(log.durationMs / 1000).toFixed(1)}с` : '—'}</td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    )}
                </div>
            </div>

            <div className="rf-card" style={{ marginBottom: 24 }}>
                <div className="rf-card-header"><h3>Импортированные товары</h3></div>
                <div className="rf-card-body">
                    <p style={{ margin: '0 0 10px', fontSize: 'var(--text-base)', color: 'var(--ink-2)', lineHeight: 1.6 }}>
                        Товары, загруженные из 1С, попадают в категорию <strong>«Импорт из 1С»</strong> в неактивном состоянии. Для публикации на сайте необходимо:
                    </p>
                    <ol style={{ paddingLeft: 20, fontSize: 'var(--text-base)', color: 'var(--ink-2)', lineHeight: 2, margin: 0 }}>
                        <li>Перейти в каталог и выбрать категорию «Импорт из 1С»</li>
                        <li>Выделить нужные товары чекбоксами</li>
                        <li>Нажать «Переместить в категорию» и выбрать целевую категорию</li>
                        <li>Активировать товары переключателем в колонке «Акт.»</li>
                    </ol>
                    <button className="rf-btn rf-btn-primary" onClick={() => navigate('/admin/products')} style={{ marginTop: 12 }}>
                        → Перейти в каталог
                    </button>
                </div>
            </div>

            <div className="rf-card">
                <div className="rf-card-header"><h3>Настройка в 1С</h3></div>
                <div className="rf-card-body">
                    <p style={{ margin: '0 0 10px', fontSize: 'var(--text-base)', color: 'var(--ink-2)', lineHeight: 1.6 }}>
                        Для настройки обмена в 1С (УНФ / УТ / Бухгалтерия):
                    </p>
                    <ol style={{ paddingLeft: 20, fontSize: 'var(--text-base)', color: 'var(--ink-2)', lineHeight: 2, margin: 0 }}>
                        <li>Перейдите в <strong>Администрирование → Обмен данными → Узлы обмена</strong></li>
                        <li>Создайте новый узел обмена с сайтом</li>
                        <li>Укажите URL обмена, логин и пароль</li>
                        <li>Настройте расписание автоматического обмена (рекомендуется каждые 30 минут)</li>
                    </ol>
                </div>
            </div>
        </div>
    );
};

export default IntegrationPage;
