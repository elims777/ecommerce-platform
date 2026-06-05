import { useState, useEffect, useRef } from 'react';
import { App } from 'antd';
import type { Slide, SlideProduct, SlideType, GradientPreset } from '@/types/slider';
import { makeSlide, GRADIENT_PRESETS, GRADIENT_LABELS } from '@/types/slider';
import ProductPickerModal from './ProductPickerModal';

// ── Icons ─────────────────────────────────────────────────────
const CloseIcon = () => (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
        <path d="M18 6 6 18M6 6l12 12"/>
    </svg>
);
const TrashIcon = () => (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6M14 11v6M9 6V4h6v2"/>
    </svg>
);
const PlusIcon = () => (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
        <path d="M12 5v14M5 12h14"/>
    </svg>
);
const ImgPlaceholder = () => (
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" style={{ opacity: 0.4 }}>
        <rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/>
        <polyline points="21 15 16 10 5 21"/>
    </svg>
);

// ── Field components ──────────────────────────────────────────
const Label = ({ children }: { children: React.ReactNode }) => (
    <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-3)', marginBottom: 5, textTransform: 'uppercase', letterSpacing: '0.04em' }}>
        {children}
    </div>
);

const inputStyle: React.CSSProperties = {
    width: '100%', height: 36, padding: '0 10px',
    border: '1px solid var(--line-1)', borderRadius: 'var(--r-2)',
    background: 'var(--surface)', fontSize: 13, color: 'var(--ink-1)',
    fontFamily: 'var(--font-body)', outline: 'none', boxSizing: 'border-box',
};

const textareaStyle: React.CSSProperties = {
    width: '100%', padding: '8px 10px',
    border: '1px solid var(--line-1)', borderRadius: 'var(--r-2)',
    background: 'var(--surface)', fontSize: 13, color: 'var(--ink-1)',
    fontFamily: 'var(--font-body)', outline: 'none', resize: 'vertical',
    minHeight: 68, boxSizing: 'border-box', lineHeight: 1.5,
};

// ── Slide preview (mini) ──────────────────────────────────────
const SlidePreview = ({ slide }: { slide: Slide }) => {
    const bg = slide.type === 'image' && slide.imageUrl
        ? `url(${slide.imageUrl}) center/cover`
        : (slide.gradientPreset === 'custom' ? slide.customGradient : GRADIENT_PRESETS[slide.gradientPreset]);

    return (
        <div style={{
            height: 80, borderRadius: 'var(--r-3)', overflow: 'hidden',
            background: bg, position: 'relative', flexShrink: 0,
        }}>
            <div style={{
                position: 'absolute', inset: 0, padding: '8px 12px',
                display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 3,
            }}>
                {slide.eyebrow && (
                    <div style={{ fontSize: 9, fontWeight: 600, color: 'rgba(255,255,255,.8)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                        {slide.eyebrow}
                    </div>
                )}
                {slide.title && (
                    <div style={{ fontSize: 11, fontWeight: 700, color: '#fff', lineHeight: 1.3, maxWidth: 200 }}>
                        {slide.title}
                    </div>
                )}
                {slide.text && (
                    <div style={{ fontSize: 9, color: 'rgba(255,255,255,.7)', lineHeight: 1.3, maxWidth: 220 }}>
                        {slide.text.slice(0, 80)}{slide.text.length > 80 ? '…' : ''}
                    </div>
                )}
            </div>
        </div>
    );
};

// ── Tab button ────────────────────────────────────────────────
const TabBtn = ({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) => (
    <button
        onClick={onClick}
        style={{
            height: 32, padding: '0 14px', border: 0, borderRadius: 'var(--r-2)',
            background: active ? 'var(--brand-red)' : 'transparent',
            color: active ? '#fff' : 'var(--ink-2)',
            fontWeight: active ? 600 : 400,
            fontSize: 13, cursor: 'pointer', fontFamily: 'var(--font-body)',
            transition: 'background .12s, color .12s',
        }}
    >
        {children}
    </button>
);

// ── Main component ────────────────────────────────────────────
interface Props {
    open: boolean;
    initial: Slide | null;
    onSave: (slide: Slide) => void;
    onClose: () => void;
}

type EditorTab = 'background' | 'text' | 'products';

const SlideEditorModal = ({ open, initial, onSave, onClose }: Props) => {
    const { message: messageApi } = App.useApp();
    const [slide, setSlide] = useState<Slide>(makeSlide());
    const [tab, setTab] = useState<EditorTab>('background');
    const [pickerOpen, setPickerOpen] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        if (open) {
            setSlide(initial ? { ...initial } : makeSlide());
            setTab('background');
        }
    }, [open, initial]);

    const set = <K extends keyof Slide>(key: K, value: Slide[K]) =>
        setSlide((prev) => ({ ...prev, [key]: value }));

    const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;
        if (file.size > 5 * 1024 * 1024) {
            messageApi.warning('Файл слишком большой, максимум 5 МБ');
            return;
        }
        const reader = new FileReader();
        reader.onload = (ev) => {
            set('imageUrl', ev.target?.result as string);
            set('type', 'image');
        };
        reader.readAsDataURL(file);
        e.target.value = '';
    };

    const handleProductsConfirm = (products: SlideProduct[]) => {
        const existingMap = new Map(slide.products.map((p) => [p.id, p]));
        const merged = [
            ...slide.products,
            ...products.filter((p) => !existingMap.has(p.id)),
        ];
        set('products', merged);
        setPickerOpen(false);
    };

    const removeProduct = (id: number) =>
        set('products', slide.products.filter((p) => p.id !== id));

    const handleSave = () => {
        if (!slide.title.trim() && slide.type !== 'products') {
            messageApi.warning('Заполните заголовок слайда');
            return;
        }
        if (slide.type === 'products' && slide.products.length === 0) {
            messageApi.warning('Добавьте хотя бы один товар');
            return;
        }
        onSave(slide);
    };

    if (!open) return null;

    const formatPrice = (p: number) =>
        new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', minimumFractionDigits: 0 }).format(p);

    return (
        <>
            <div style={{
                position: 'fixed', inset: 0, zIndex: 1050,
                background: 'rgba(0,0,0,.45)', display: 'flex',
                alignItems: 'center', justifyContent: 'center',
            }}
                onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
            >
                <div style={{
                    background: 'var(--surface)', borderRadius: 'var(--r-5)',
                    width: 640, maxHeight: '90vh', display: 'flex', flexDirection: 'column',
                    boxShadow: 'var(--shadow-4)',
                }}>
                    {/* Header */}
                    <div style={{
                        padding: '16px 20px 12px', borderBottom: '1px solid var(--line-1)',
                        display: 'flex', alignItems: 'center', gap: 12,
                    }}>
                        <div style={{ fontWeight: 700, fontSize: 15, color: 'var(--ink-1)', flex: 1 }}>
                            {initial ? 'Редактировать слайд' : 'Новый слайд'}
                        </div>
                        <button onClick={onClose} style={{
                            width: 32, height: 32, border: 0, borderRadius: 'var(--r-3)',
                            background: 'transparent', cursor: 'pointer', color: 'var(--ink-3)',
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                        }}>
                            <CloseIcon />
                        </button>
                    </div>

                    {/* Preview */}
                    <div style={{ padding: '12px 20px 0' }}>
                        <SlidePreview slide={slide} />
                    </div>

                    {/* Tabs */}
                    <div style={{ padding: '10px 20px 0', display: 'flex', gap: 4, borderBottom: '1px solid var(--line-1)', paddingBottom: 10 }}>
                        <TabBtn active={tab === 'background'} onClick={() => setTab('background')}>Фон</TabBtn>
                        <TabBtn active={tab === 'text'} onClick={() => setTab('text')}>Текст и кнопки</TabBtn>
                        <TabBtn active={tab === 'products'} onClick={() => setTab('products')}>
                            Товары {slide.products.length > 0 ? `(${slide.products.length})` : ''}
                        </TabBtn>
                    </div>

                    {/* Tab content */}
                    <div style={{ flex: 1, overflowY: 'auto', padding: '16px 20px' }}>

                        {/* ── TAB: Background ── */}
                        {tab === 'background' && (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                                <div>
                                    <Label>Тип фона</Label>
                                    <div style={{ display: 'flex', gap: 8 }}>
                                        {(['gradient', 'image'] as SlideType[]).map((t) => (
                                            <button
                                                key={t}
                                                onClick={() => set('type', t)}
                                                style={{
                                                    height: 34, padding: '0 16px', border: '1.5px solid',
                                                    borderColor: slide.type === t ? 'var(--brand-red)' : 'var(--line-1)',
                                                    borderRadius: 'var(--r-2)',
                                                    background: slide.type === t ? 'var(--red-tint)' : 'var(--surface)',
                                                    color: slide.type === t ? 'var(--brand-red)' : 'var(--ink-2)',
                                                    fontWeight: slide.type === t ? 600 : 400,
                                                    fontSize: 13, cursor: 'pointer', fontFamily: 'var(--font-body)',
                                                }}
                                            >
                                                {t === 'gradient' ? 'Градиент' : 'Изображение'}
                                            </button>
                                        ))}
                                    </div>
                                </div>

                                {slide.type === 'gradient' && (
                                    <>
                                        <div>
                                            <Label>Пресет градиента</Label>
                                            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
                                                {(Object.keys(GRADIENT_PRESETS) as GradientPreset[]).map((preset) => (
                                                    <button
                                                        key={preset}
                                                        onClick={() => set('gradientPreset', preset)}
                                                        style={{
                                                            height: 44, border: '2px solid',
                                                            borderColor: slide.gradientPreset === preset ? 'var(--brand-red)' : 'transparent',
                                                            borderRadius: 'var(--r-3)', cursor: 'pointer', overflow: 'hidden',
                                                            background: preset === 'custom'
                                                                ? 'var(--surface-2)'
                                                                : GRADIENT_PRESETS[preset],
                                                            fontSize: 11, fontWeight: 600,
                                                            color: preset === 'custom' ? 'var(--ink-2)' : '#fff',
                                                            fontFamily: 'var(--font-body)',
                                                        }}
                                                    >
                                                        {GRADIENT_LABELS[preset]}
                                                    </button>
                                                ))}
                                            </div>
                                        </div>

                                        {slide.gradientPreset === 'custom' && (
                                            <div>
                                                <Label>CSS градиент</Label>
                                                <input
                                                    style={inputStyle}
                                                    value={slide.customGradient}
                                                    onChange={(e) => set('customGradient', e.target.value)}
                                                    placeholder="linear-gradient(135deg, #1E3A5F 0%, #0d2240 100%)"
                                                />
                                                <div style={{ marginTop: 6, height: 28, borderRadius: 'var(--r-2)', background: slide.customGradient }} />
                                            </div>
                                        )}
                                    </>
                                )}

                                {slide.type === 'image' && (
                                    <div>
                                        <Label>Изображение</Label>
                                        <input ref={fileInputRef} type="file" accept="image/*" style={{ display: 'none' }} onChange={handleImageUpload} />
                                        {slide.imageUrl ? (
                                            <div style={{ position: 'relative' }}>
                                                <img src={slide.imageUrl} alt="" style={{
                                                    width: '100%', height: 140, objectFit: 'cover',
                                                    borderRadius: 'var(--r-3)', display: 'block',
                                                }} />
                                                <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                                                    <button
                                                        onClick={() => fileInputRef.current?.click()}
                                                        style={{
                                                            height: 32, padding: '0 14px', border: '1px solid var(--line-2)',
                                                            borderRadius: 'var(--r-2)', background: 'var(--surface)',
                                                            fontSize: 12, cursor: 'pointer', fontFamily: 'var(--font-body)',
                                                            color: 'var(--ink-2)',
                                                        }}
                                                    >
                                                        Заменить
                                                    </button>
                                                    <button
                                                        onClick={() => set('imageUrl', '')}
                                                        style={{
                                                            height: 32, padding: '0 14px', border: '1px solid var(--line-2)',
                                                            borderRadius: 'var(--r-2)', background: 'var(--surface)',
                                                            fontSize: 12, cursor: 'pointer', fontFamily: 'var(--font-body)',
                                                            color: 'var(--brand-red)',
                                                        }}
                                                    >
                                                        Удалить
                                                    </button>
                                                </div>
                                            </div>
                                        ) : (
                                            <button
                                                onClick={() => fileInputRef.current?.click()}
                                                style={{
                                                    width: '100%', height: 100, border: '2px dashed var(--line-2)',
                                                    borderRadius: 'var(--r-3)', background: 'var(--surface-2)',
                                                    cursor: 'pointer', display: 'flex', flexDirection: 'column',
                                                    alignItems: 'center', justifyContent: 'center', gap: 6,
                                                    color: 'var(--ink-3)', fontSize: 12, fontFamily: 'var(--font-body)',
                                                }}
                                            >
                                                <ImgPlaceholder />
                                                Загрузить изображение (до 5 МБ)
                                            </button>
                                        )}
                                        <div style={{ marginTop: 10 }}>
                                            <Label>Или вставить URL изображения</Label>
                                            <input
                                                style={inputStyle}
                                                value={slide.imageUrl.startsWith('data:') ? '' : slide.imageUrl}
                                                onChange={(e) => { set('imageUrl', e.target.value); if (e.target.value) set('type', 'image'); }}
                                                placeholder="https://example.com/banner.jpg"
                                            />
                                        </div>
                                    </div>
                                )}
                            </div>
                        )}

                        {/* ── TAB: Text ── */}
                        {tab === 'text' && (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                                <div>
                                    <Label>Подпись (eyebrow)</Label>
                                    <input
                                        style={inputStyle}
                                        value={slide.eyebrow}
                                        onChange={(e) => set('eyebrow', e.target.value)}
                                        placeholder="Например: B2B-снабжение"
                                    />
                                </div>
                                <div>
                                    <Label>Заголовок</Label>
                                    <textarea
                                        style={textareaStyle}
                                        value={slide.title}
                                        onChange={(e) => set('title', e.target.value)}
                                        placeholder="Главный заголовок слайда"
                                        rows={2}
                                    />
                                </div>
                                <div>
                                    <Label>Текст</Label>
                                    <textarea
                                        style={textareaStyle}
                                        value={slide.text}
                                        onChange={(e) => set('text', e.target.value)}
                                        placeholder="Описание или уточнение"
                                        rows={3}
                                    />
                                </div>

                                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                                    <div>
                                        <Label>Кнопка 1 — текст</Label>
                                        <input
                                            style={inputStyle}
                                            value={slide.cta1Label}
                                            onChange={(e) => set('cta1Label', e.target.value)}
                                            placeholder="Открыть каталог"
                                        />
                                    </div>
                                    <div>
                                        <Label>Кнопка 1 — ссылка</Label>
                                        <input
                                            style={inputStyle}
                                            value={slide.cta1Link}
                                            onChange={(e) => set('cta1Link', e.target.value)}
                                            placeholder="/catalog"
                                        />
                                    </div>
                                    <div>
                                        <Label>Кнопка 2 — текст</Label>
                                        <input
                                            style={inputStyle}
                                            value={slide.cta2Label}
                                            onChange={(e) => set('cta2Label', e.target.value)}
                                            placeholder="Необязательная кнопка"
                                        />
                                    </div>
                                    <div>
                                        <Label>Кнопка 2 — ссылка</Label>
                                        <input
                                            style={inputStyle}
                                            value={slide.cta2Link}
                                            onChange={(e) => set('cta2Link', e.target.value)}
                                            placeholder="/contacts"
                                        />
                                    </div>
                                </div>
                            </div>
                        )}

                        {/* ── TAB: Products ── */}
                        {tab === 'products' && (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                                <div style={{ fontSize: 12, color: 'var(--ink-3)', lineHeight: 1.5 }}>
                                    Товары отображаются в правой части слайда. Рекомендуется 1–3 товара для оптимального вида.
                                </div>

                                <button
                                    onClick={() => setPickerOpen(true)}
                                    style={{
                                        height: 36, padding: '0 16px', border: '1.5px dashed var(--brand-red)',
                                        borderRadius: 'var(--r-3)', background: 'var(--red-tint)',
                                        color: 'var(--brand-red)', fontWeight: 600, fontSize: 13,
                                        cursor: 'pointer', fontFamily: 'var(--font-body)',
                                        display: 'inline-flex', alignItems: 'center', gap: 6,
                                    }}
                                >
                                    <PlusIcon /> Добавить товары из каталога
                                </button>

                                {slide.products.length > 0 && (
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                                        {slide.products.map((p) => (
                                            <div key={p.id} style={{
                                                display: 'flex', alignItems: 'center', gap: 10,
                                                padding: '8px 10px', borderRadius: 'var(--r-3)',
                                                background: 'var(--surface-2)', border: '1px solid var(--line-1)',
                                            }}>
                                                <div style={{
                                                    width: 36, height: 36, borderRadius: 'var(--r-2)',
                                                    background: 'var(--surface)', flexShrink: 0,
                                                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                                                    overflow: 'hidden',
                                                }}>
                                                    {p.imageUrl
                                                        ? <img src={p.imageUrl} alt={p.name} style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} />
                                                        : <ImgPlaceholder />
                                                    }
                                                </div>
                                                <div style={{ flex: 1, minWidth: 0 }}>
                                                    <div style={{ fontSize: 12, fontWeight: 500, color: 'var(--ink-1)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                                        {p.name}
                                                    </div>
                                                    <div style={{ fontSize: 11, color: 'var(--ink-3)', display: 'flex', gap: 8, marginTop: 1 }}>
                                                        {p.sku && <span style={{ fontFamily: 'var(--font-mono)' }}>{p.sku}</span>}
                                                        <span style={{ fontWeight: 600 }}>{formatPrice(p.price)}</span>
                                                    </div>
                                                </div>
                                                <button
                                                    onClick={() => removeProduct(p.id)}
                                                    style={{
                                                        width: 28, height: 28, border: 0, borderRadius: 'var(--r-2)',
                                                        background: 'transparent', cursor: 'pointer', color: 'var(--ink-3)',
                                                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                                                        flexShrink: 0,
                                                    }}
                                                >
                                                    <TrashIcon />
                                                </button>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>
                        )}
                    </div>

                    {/* Footer */}
                    <div style={{
                        padding: '12px 20px', borderTop: '1px solid var(--line-1)',
                        display: 'flex', alignItems: 'center', gap: 10,
                    }}>
                        <div style={{ flex: 1, display: 'flex', alignItems: 'center', gap: 8 }}>
                            <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>Видимость:</span>
                            <button
                                onClick={() => set('enabled', !slide.enabled)}
                                style={{
                                    height: 24, padding: '0 10px', border: '1px solid var(--line-1)',
                                    borderRadius: 'var(--r-full)', fontSize: 11, fontWeight: 600,
                                    cursor: 'pointer', fontFamily: 'var(--font-body)',
                                    background: slide.enabled ? 'var(--brand-green-soft)' : 'var(--surface-2)',
                                    color: slide.enabled ? 'var(--brand-green)' : 'var(--ink-3)',
                                }}
                            >
                                {slide.enabled ? 'Виден' : 'Скрыт'}
                            </button>
                        </div>
                        <button onClick={onClose} style={{
                            height: 36, padding: '0 16px', border: '1px solid var(--line-2)',
                            borderRadius: 'var(--r-3)', background: 'transparent', cursor: 'pointer',
                            fontSize: 13, color: 'var(--ink-2)', fontFamily: 'var(--font-body)',
                        }}>
                            Отмена
                        </button>
                        <button onClick={handleSave} style={{
                            height: 36, padding: '0 20px', border: 0,
                            borderRadius: 'var(--r-3)', background: 'var(--brand-red)',
                            color: '#fff', fontWeight: 600, fontSize: 13,
                            cursor: 'pointer', fontFamily: 'var(--font-body)',
                        }}>
                            Сохранить
                        </button>
                    </div>
                </div>
            </div>

            <ProductPickerModal
                open={pickerOpen}
                alreadySelected={slide.products.map((p) => p.id)}
                onConfirm={handleProductsConfirm}
                onClose={() => setPickerOpen(false)}
            />
        </>
    );
};

export default SlideEditorModal;
