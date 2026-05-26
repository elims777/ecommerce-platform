import { useNavigate } from 'react-router-dom';

const IntegrationPage = () => {
    const navigate = useNavigate();

    return (
        <div>
            <h2 style={{ fontFamily: 'var(--font-head)', fontSize: 22, fontWeight: 600, marginBottom: 20 }}>
                Интеграция 1С
            </h2>

            <div style={{ background: 'var(--navy-tint)', border: '1px solid var(--brand-navy)', borderRadius: 'var(--r-3)', padding: '14px 18px', marginBottom: 24, display: 'flex', gap: 12, alignItems: 'flex-start', color: 'var(--brand-navy)', fontSize: 13 }}>
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none" style={{ flexShrink: 0, marginTop: 1 }}>
                    <circle cx="8" cy="8" r="7.5" stroke="currentColor"/>
                    <path d="M8 7v4M8 5.5v.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
                </svg>
                <span>Обмен данными с 1С настроен через протокол CommerceML 2.10. Товары из 1С попадают в категорию «Импорт из 1С» и требуют ручного распределения по категориям и активации.</span>
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
                        <code className="rf-mono" style={{ fontSize: 12, background: 'var(--surface-2)', padding: '2px 6px', borderRadius: 3 }}>
                            http://ВАШ_ДОМЕН:8085/1c-exchange
                        </code>
                        <button
                            onClick={() => navigator.clipboard.writeText('http://ВАШ_ДОМЕН:8085/1c-exchange')}
                            style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '2px 4px', color: 'var(--ink-3)', fontSize: 12 }}
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

            <div className="rf-card" style={{ marginBottom: 24 }}>
                <div className="rf-card-header"><h3>Импортированные товары</h3></div>
                <div className="rf-card-body">
                    <p style={{ margin: '0 0 10px', fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.6 }}>
                        Товары, загруженные из 1С, попадают в категорию <strong>«Импорт из 1С»</strong> в неактивном состоянии. Для публикации на сайте необходимо:
                    </p>
                    <ol style={{ paddingLeft: 20, fontSize: 13, color: 'var(--ink-2)', lineHeight: 2, margin: 0 }}>
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
                    <p style={{ margin: '0 0 10px', fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.6 }}>
                        Для настройки обмена в 1С (УНФ / УТ / Бухгалтерия):
                    </p>
                    <ol style={{ paddingLeft: 20, fontSize: 13, color: 'var(--ink-2)', lineHeight: 2, margin: 0 }}>
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
