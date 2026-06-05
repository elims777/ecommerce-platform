import { useState, useEffect, useRef, useCallback } from 'react';
import { App } from 'antd';
import type { Slide, SlideProduct, SlideType, GradientPreset, ImageFit, TextPosition } from '@/types/slider';
import { makeSlide, GRADIENT_PRESETS, GRADIENT_LABELS, IMAGE_FIT_LABELS } from '@/types/slider';
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
const DragIcon = () => (
    <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round">
        <path d="M9 5h.01M9 12h.01M9 19h.01M15 5h.01M15 12h.01M15 19h.01"/>
    </svg>
);
const ImgPlaceholder = () => (
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" style={{ opacity: 0.4 }}>
        <rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/>
        <polyline points="21 15 16 10 5 21"/>
    </svg>
);
const MoveIcon = () => (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M5 9l-3 3 3 3M9 5l3-3 3 3M15 19l-3 3-3-3M19 9l3 3-3 3M2 12h20M12 2v20"/>
    </svg>
);

// ── Field helpers ─────────────────────────────────────────────
const Label = ({ children }: { children: React.ReactNode }) => (
    <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-3)', marginBottom: 5, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
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
    minHeight: 64, boxSizing: 'border-box', lineHeight: 1.5,
};

const TabBtn = ({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) => (
    <button onClick={onClick} style={{
        height: 30, padding: '0 13px', border: 0, borderRadius: 'var(--r-2)',
        background: active ? 'var(--brand-red)' : 'transparent',
        color: active ? '#fff' : 'var(--ink-2)',
        fontWeight: active ? 600 : 400,
        fontSize: 13, cursor: 'pointer', fontFamily: 'var(--font-body)',
        transition: 'background .12s, color .12s', whiteSpace: 'nowrap',
    }}>
        {children}
    </button>
);

// ── Interactive preview with draggable text block ─────────────
const PREVIEW_W = 820;
const PREVIEW_H = 240;

interface DraggablePreviewProps {
    slide: Slide;
    onPositionChange: (pos: TextPosition) => void;
}

const getSlideBgStyle = (slide: Slide): React.CSSProperties => {
    if (slide.type === 'image' && slide.imageUrl) {
        const fit = (slide.imageFit as string | undefined) ?? 'cover';
        return {
            backgroundImage: `url(${slide.imageUrl})`,
            backgroundSize: fit === 'contain' ? 'contain'
                : fit === 'fill' ? '100% 100%'
                : 'cover',
            backgroundPosition: 'center',
            backgroundRepeat: 'no-repeat',
        };
    }
    const grad = slide.gradientPreset === 'custom' ? slide.customGradient : GRADIENT_PRESETS[slide.gradientPreset];
    return { background: grad };
};

const DraggablePreview = ({ slide, onPositionChange }: DraggablePreviewProps) => {
    const containerRef = useRef<HTMLDivElement>(null);
    const isDragging = useRef(false);
    const dragOffset = useRef({ dx: 0, dy: 0 });
    const [pos, setPos] = useState<TextPosition>(slide.textPosition ?? { x: 5, y: 20 });

    useEffect(() => { setPos(slide.textPosition ?? { x: 5, y: 20 }); }, [slide.textPosition]);

    const clamp = (v: number, min: number, max: number) => Math.max(min, Math.min(max, v));

    const onMouseDown = useCallback((e: React.MouseEvent) => {
        e.preventDefault();
        const el = (e.currentTarget as HTMLDivElement);
        const rect = el.getBoundingClientRect();
        dragOffset.current = {
            dx: e.clientX - rect.left,
            dy: e.clientY - rect.top,
        };
        isDragging.current = true;

        const onMove = (ev: MouseEvent) => {
            if (!isDragging.current || !containerRef.current) return;
            const cRect = containerRef.current.getBoundingClientRect();
            const newX = clamp(((ev.clientX - cRect.left - dragOffset.current.dx) / cRect.width) * 100, 0, 75);
            const newY = clamp(((ev.clientY - cRect.top - dragOffset.current.dy) / cRect.height) * 100, 0, 70);
            setPos({ x: newX, y: newY });
        };
        const onUp = (ev: MouseEvent) => {
            if (!isDragging.current || !containerRef.current) return;
            isDragging.current = false;
            const cRect = containerRef.current.getBoundingClientRect();
            const newX = clamp(((ev.clientX - cRect.left - dragOffset.current.dx) / cRect.width) * 100, 0, 75);
            const newY = clamp(((ev.clientY - cRect.top - dragOffset.current.dy) / cRect.height) * 100, 0, 70);
            const finalPos = { x: newX, y: newY };
            setPos(finalPos);
            onPositionChange(finalPos);
            window.removeEventListener('mousemove', onMove);
            window.removeEventListener('mouseup', onUp);
        };
        window.addEventListener('mousemove', onMove);
        window.addEventListener('mouseup', onUp);
    }, [onPositionChange]);

    const bgStyle = getSlideBgStyle(slide);

    return (
        <div
            ref={containerRef}
            style={{
                position: 'relative',
                width: '100%',
                height: PREVIEW_H,
                borderRadius: 'var(--r-4)',
                overflow: 'hidden',
                ...bgStyle,
                userSelect: 'none',
            }}
        >
            {/* Watermark logo */}
            <img src="/logo-light.png" alt="" style={{
                position: 'absolute', right: -12, bottom: -14,
                height: PREVIEW_H * 0.85, opacity: 0.1, pointerEvents: 'none',
            }} />

            {/* Grid overlay hint */}
            <div style={{
                position: 'absolute', inset: 0,
                backgroundImage: 'repeating-linear-gradient(0deg, transparent, transparent 23px, rgba(255,255,255,.05) 24px), repeating-linear-gradient(90deg, transparent, transparent 23px, rgba(255,255,255,.05) 24px)',
                pointerEvents: 'none',
            }} />

            {/* Draggable text block */}
            <div
                onMouseDown={onMouseDown}
                style={{
                    position: 'absolute',
                    left: `${pos.x}%`,
                    top: `${pos.y}%`,
                    cursor: 'grab',
                    maxWidth: '55%',
                    padding: '6px 8px',
                    borderRadius: 4,
                    border: '1.5px dashed rgba(255,255,255,.6)',
                    background: 'rgba(0,0,0,.18)',
                    backdropFilter: 'blur(2px)',
                    display: 'inline-flex', flexDirection: 'column', gap: 2,
                }}
                title="Перетащите, чтобы изменить положение текста"
            >
                <div style={{
                    position: 'absolute', top: -8, right: -8,
                    width: 16, height: 16, borderRadius: '50%',
                    background: 'rgba(255,255,255,.9)', display: 'flex',
                    alignItems: 'center', justifyContent: 'center', color: '#333',
                }}>
                    <MoveIcon />
                </div>

                {slide.eyebrow && (
                    <div style={{ fontSize: 8, fontWeight: 700, color: 'rgba(255,255,255,.85)', textTransform: 'uppercase', letterSpacing: '0.06em', lineHeight: 1.2 }}>
                        {slide.eyebrow}
                    </div>
                )}
                {slide.title && (
                    <div style={{ fontSize: 11, fontWeight: 700, color: '#fff', lineHeight: 1.25 }}>
                        {slide.title.length > 60 ? slide.title.slice(0, 60) + '…' : slide.title}
                    </div>
                )}
                {slide.text && (
                    <div style={{ fontSize: 8.5, color: 'rgba(255,255,255,.75)', lineHeight: 1.3, marginTop: 1 }}>
                        {slide.text.length > 80 ? slide.text.slice(0, 80) + '…' : slide.text}
                    </div>
                )}
                {(slide.cta1Label || slide.cta2Label) && (
                    <div style={{ display: 'flex', gap: 4, marginTop: 3 }}>
                        {slide.cta1Label && (
                            <div style={{ height: 14, padding: '0 6px', borderRadius: 2, background: 'rgba(255,255,255,.9)', fontSize: 7.5, fontWeight: 700, color: '#333', display: 'flex', alignItems: 'center' }}>
                                {slide.cta1Label}
                            </div>
                        )}
                        {slide.cta2Label && (
                            <div style={{ height: 14, padding: '0 6px', borderRadius: 2, background: 'rgba(255,255,255,.2)', fontSize: 7.5, fontWeight: 600, color: '#fff', display: 'flex', alignItems: 'center' }}>
                                {slide.cta2Label}
                            </div>
                        )}
                    </div>
                )}
                {slide.products.length > 0 && (
                    <div style={{ fontSize: 8, color: 'rgba(255,255,255,.6)', marginTop: 2 }}>
                        + {slide.products.length} товаров справа
                    </div>
                )}

                {!slide.eyebrow && !slide.title && !slide.text && (
                    <div style={{ fontSize: 9, color: 'rgba(255,255,255,.5)', fontStyle: 'italic' }}>
                        Заполните текст на вкладке «Текст»
                    </div>
                )}
            </div>

            {/* Position badge */}
            <div style={{
                position: 'absolute', bottom: 6, right: 8,
                fontSize: 9, color: 'rgba(255,255,255,.5)',
                fontFamily: 'var(--font-mono)',
            }}>
                x:{pos.x.toFixed(0)}% y:{pos.y.toFixed(0)}%
            </div>
        </div>
    );
};

// ── Products tab with drag-and-drop reorder ───────────────────
interface ProductsTabProps {
    products: SlideProduct[];
    onChange: (products: SlideProduct[]) => void;
    onOpenPicker: () => void;
}

const formatPrice = (p: number) =>
    new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', minimumFractionDigits: 0 }).format(p);

const ProductsTab = ({ products, onChange, onOpenPicker }: ProductsTabProps) => {
    const dragId = useRef<number | null>(null);
    const [dragOver, setDragOver] = useState<number | null>(null);

    const remove = (id: number) => onChange(products.filter((p) => p.id !== id));

    const handleDragStart = (id: number) => (e: React.DragEvent) => {
        dragId.current = id;
        e.dataTransfer.effectAllowed = 'move';
    };
    const handleDragOver = (id: number) => (e: React.DragEvent) => {
        e.preventDefault();
        setDragOver(id);
    };
    const handleDrop = (targetId: number) => (e: React.DragEvent) => {
        e.preventDefault();
        const fromId = dragId.current;
        if (fromId === null || fromId === targetId) { setDragOver(null); return; }
        const ids = products.map((p) => p.id);
        const fromIdx = ids.indexOf(fromId);
        const toIdx = ids.indexOf(targetId);
        const reordered = [...products];
        const [moved] = reordered.splice(fromIdx, 1);
        reordered.splice(toIdx, 0, moved);
        onChange(reordered);
        setDragOver(null);
    };

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <div style={{ fontSize: 12, color: 'var(--ink-3)', lineHeight: 1.5 }}>
                Товары показываются в правой части слайда. Рекомендуется 1–3 товара. Перетащите для изменения порядка.
            </div>

            <button onClick={onOpenPicker} style={{
                height: 34, padding: '0 14px', border: '1.5px dashed var(--brand-red)',
                borderRadius: 'var(--r-3)', background: 'var(--red-tint)',
                color: 'var(--brand-red)', fontWeight: 600, fontSize: 13,
                cursor: 'pointer', fontFamily: 'var(--font-body)',
                display: 'inline-flex', alignItems: 'center', gap: 6, alignSelf: 'flex-start',
            }}>
                <PlusIcon /> Добавить товары из каталога
            </button>

            {products.length > 0 && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
                    {products.map((p) => (
                        <div
                            key={p.id}
                            draggable
                            onDragStart={handleDragStart(p.id)}
                            onDragOver={handleDragOver(p.id)}
                            onDrop={handleDrop(p.id)}
                            onDragEnd={() => { dragId.current = null; setDragOver(null); }}
                            style={{
                                display: 'flex', alignItems: 'center', gap: 8,
                                padding: '7px 10px', borderRadius: 'var(--r-3)',
                                background: dragOver === p.id ? 'var(--red-tint)' : 'var(--surface-2)',
                                border: `1px solid ${dragOver === p.id ? 'var(--brand-red)' : 'var(--line-1)'}`,
                                cursor: 'grab', transition: 'background .1s, border-color .1s',
                            }}
                        >
                            <div style={{ color: 'var(--ink-4)', flexShrink: 0 }}><DragIcon /></div>
                            <div style={{
                                width: 34, height: 34, borderRadius: 'var(--r-2)',
                                background: 'var(--surface)', flexShrink: 0,
                                display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden',
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
                                <div style={{ fontSize: 11, color: 'var(--ink-3)', display: 'flex', gap: 6, marginTop: 1, flexWrap: 'wrap' }}>
                                    {p.sku && <span style={{ fontFamily: 'var(--font-mono)' }}>{p.sku}</span>}
                                    {p.fields?.showPrice !== false && <span style={{ fontWeight: 600 }}>{formatPrice(p.price)}</span>}
                                    {p.fields?.showName === false && <span style={{ color: 'var(--ink-4)' }}>без названия</span>}
                                    {p.fields?.showShortDescription && <span>+ краткое</span>}
                                    {p.fields?.showDescription && <span>+ описание</span>}
                                </div>
                            </div>
                            <button onClick={() => remove(p.id)} style={{
                                width: 26, height: 26, border: 0, borderRadius: 'var(--r-2)',
                                background: 'transparent', cursor: 'pointer', color: 'var(--ink-3)',
                                display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
                            }}>
                                <TrashIcon />
                            </button>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

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
        if (!open) return;
        setSlide(initial ? { ...initial } : makeSlide());
        setTab('background');
    }, [open, initial?.id]);

    const set = <K extends keyof Slide>(key: K, value: Slide[K]) =>
        setSlide((prev) => ({ ...prev, [key]: value }));

    const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;
        if (file.size > 5 * 1024 * 1024) { messageApi.warning('Файл слишком большой, максимум 5 МБ'); return; }
        const reader = new FileReader();
        reader.onload = (ev) => {
            const img = new Image();
            img.onload = () => {
                setSlide((prev) => ({
                    ...prev,
                    imageUrl: ev.target?.result as string,
                    type: 'image',
                }));
            };
            img.src = ev.target?.result as string;
        };
        reader.readAsDataURL(file);
        e.target.value = '';
    };

    const handleProductsConfirm = (products: SlideProduct[]) => {
        if (products.length > 0) {
            const existingMap = new Map(slide.products.map((p) => [p.id, p]));
            set('products', [
                ...slide.products,
                ...products.filter((p) => !existingMap.has(p.id)),
            ]);
        }
        setPickerOpen(false);
    };

    const handleSave = () => {
        if (!slide.title.trim()) {
            messageApi.warning('Заполните заголовок слайда');
            return;
        }
        onSave(slide);
    };

    if (!open) return null;

    return (
        <>
            <div
                style={{
                    position: 'fixed', inset: 0, zIndex: 1050,
                    background: 'rgba(0,0,0,.5)', display: 'flex',
                    alignItems: 'center', justifyContent: 'center',
                    padding: '20px',
                }}
                onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
            >
                <div style={{
                    background: 'var(--surface)', borderRadius: 'var(--r-5)',
                    width: '100%', maxWidth: 920, maxHeight: 'calc(100vh - 40px)',
                    display: 'flex', flexDirection: 'column',
                    boxShadow: 'var(--shadow-4)',
                }}>
                    {/* Header */}
                    <div style={{
                        padding: '14px 20px', borderBottom: '1px solid var(--line-1)',
                        display: 'flex', alignItems: 'center', gap: 12, flexShrink: 0,
                    }}>
                        <div style={{ fontWeight: 700, fontSize: 15, color: 'var(--ink-1)', flex: 1 }}>
                            {initial ? 'Редактировать слайд' : 'Новый слайд'}
                        </div>
                        <div style={{ flex: 1, display: 'flex', alignItems: 'center', gap: 8 }}>
                            <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>Видимость:</span>
                            <button
                                onClick={() => set('enabled', !slide.enabled)}
                                style={{
                                    height: 22, padding: '0 10px', border: '1px solid var(--line-1)',
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
                            width: 32, height: 32, border: 0, borderRadius: 'var(--r-3)',
                            background: 'transparent', cursor: 'pointer', color: 'var(--ink-3)',
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                        }}>
                            <CloseIcon />
                        </button>
                    </div>

                    {/* Body: preview left, form right */}
                    <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>

                        {/* Left — interactive preview */}
                        <div style={{
                            width: 340, flexShrink: 0, padding: '16px 16px 16px 20px',
                            borderRight: '1px solid var(--line-1)',
                            display: 'flex', flexDirection: 'column', gap: 10,
                            overflowY: 'auto',
                        }}>
                            <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                                Превью · перетащите текстовый блок
                            </div>
                            <DraggablePreview
                                slide={slide}
                                onPositionChange={(pos) => set('textPosition', pos)}
                            />
                            <div style={{
                                fontSize: 11, color: 'var(--ink-4)', lineHeight: 1.4,
                                padding: '8px 10px', background: 'var(--surface-2)',
                                borderRadius: 'var(--r-2)', border: '1px solid var(--line-1)',
                            }}>
                                Тяните белую рамку с текстом для позиционирования. Позиция сохраняется вместе со слайдом.
                            </div>

                            {/* Image info when type = image */}
                            {slide.type === 'image' && slide.imageUrl && (
                                <div style={{
                                    fontSize: 11, color: 'var(--ink-3)', lineHeight: 1.5,
                                    padding: '8px 10px', background: 'var(--surface-2)',
                                    borderRadius: 'var(--r-2)', border: '1px solid var(--line-1)',
                                }}>
                                    <div style={{ fontWeight: 700, marginBottom: 3, color: 'var(--ink-2)' }}>Рекомендуемый размер</div>
                                    <div>1920 × 600 px (3.2:1)</div>
                                    <div style={{ marginTop: 4 }}>
                                        <span style={{ fontWeight: 600 }}>cover</span> — обрезает края<br/>
                                        <span style={{ fontWeight: 600 }}>contain</span> — вписывает целиком (с пустотами)<br/>
                                        <span style={{ fontWeight: 600 }}>fill</span> — растягивает до краёв
                                    </div>
                                </div>
                            )}
                        </div>

                        {/* Right — tabs + form */}
                        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                            {/* Tabs */}
                            <div style={{
                                padding: '10px 20px', borderBottom: '1px solid var(--line-1)',
                                display: 'flex', gap: 4, flexShrink: 0,
                            }}>
                                <TabBtn active={tab === 'background'} onClick={() => setTab('background')}>Фон</TabBtn>
                                <TabBtn active={tab === 'text'} onClick={() => setTab('text')}>Текст и кнопки</TabBtn>
                                <TabBtn active={tab === 'products'} onClick={() => setTab('products')}>
                                    Товары{slide.products.length > 0 ? ` (${slide.products.length})` : ''}
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
                                                    <button key={t} onClick={() => set('type', t)} style={{
                                                        height: 34, padding: '0 16px', border: '1.5px solid',
                                                        borderColor: slide.type === t ? 'var(--brand-red)' : 'var(--line-1)',
                                                        borderRadius: 'var(--r-2)',
                                                        background: slide.type === t ? 'var(--red-tint)' : 'var(--surface)',
                                                        color: slide.type === t ? 'var(--brand-red)' : 'var(--ink-2)',
                                                        fontWeight: slide.type === t ? 600 : 400,
                                                        fontSize: 13, cursor: 'pointer', fontFamily: 'var(--font-body)',
                                                    }}>
                                                        {t === 'gradient' ? 'Градиент' : 'Изображение'}
                                                    </button>
                                                ))}
                                            </div>
                                        </div>

                                        {slide.type === 'gradient' && (
                                            <>
                                                <div>
                                                    <Label>Пресет</Label>
                                                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
                                                        {(Object.keys(GRADIENT_PRESETS) as GradientPreset[]).map((preset) => (
                                                            <button key={preset} onClick={() => set('gradientPreset', preset)} style={{
                                                                height: 48, border: '2px solid',
                                                                borderColor: slide.gradientPreset === preset ? 'var(--brand-red)' : 'transparent',
                                                                borderRadius: 'var(--r-3)', cursor: 'pointer', overflow: 'hidden',
                                                                background: preset === 'custom' ? 'var(--surface-2)' : GRADIENT_PRESETS[preset],
                                                                fontSize: 11, fontWeight: 600,
                                                                color: preset === 'custom' ? 'var(--ink-2)' : '#fff',
                                                                fontFamily: 'var(--font-body)',
                                                            }}>
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
                                            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                                                <input ref={fileInputRef} type="file" accept="image/*" style={{ display: 'none' }} onChange={handleImageUpload} />

                                                {slide.imageUrl ? (
                                                    <div>
                                                        <img src={slide.imageUrl} alt="" style={{
                                                            width: '100%', height: 120, objectFit: 'cover',
                                                            borderRadius: 'var(--r-3)', display: 'block',
                                                        }} />
                                                        <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                                                            <button onClick={() => fileInputRef.current?.click()} style={{
                                                                height: 30, padding: '0 12px', border: '1px solid var(--line-2)',
                                                                borderRadius: 'var(--r-2)', background: 'var(--surface)',
                                                                fontSize: 12, cursor: 'pointer', fontFamily: 'var(--font-body)', color: 'var(--ink-2)',
                                                            }}>Заменить</button>
                                                            <button onClick={() => set('imageUrl', '')} style={{
                                                                height: 30, padding: '0 12px', border: '1px solid var(--line-2)',
                                                                borderRadius: 'var(--r-2)', background: 'var(--surface)',
                                                                fontSize: 12, cursor: 'pointer', fontFamily: 'var(--font-body)', color: 'var(--brand-red)',
                                                            }}>Удалить</button>
                                                        </div>
                                                    </div>
                                                ) : (
                                                    <button onClick={() => fileInputRef.current?.click()} style={{
                                                        width: '100%', height: 90, border: '2px dashed var(--line-2)',
                                                        borderRadius: 'var(--r-3)', background: 'var(--surface-2)',
                                                        cursor: 'pointer', display: 'flex', flexDirection: 'column',
                                                        alignItems: 'center', justifyContent: 'center', gap: 6,
                                                        color: 'var(--ink-3)', fontSize: 12, fontFamily: 'var(--font-body)',
                                                    }}>
                                                        <ImgPlaceholder />
                                                        Загрузить изображение (до 5 МБ)
                                                    </button>
                                                )}

                                                <div>
                                                    <Label>URL изображения</Label>
                                                    <input
                                                        style={inputStyle}
                                                        value={slide.imageUrl.startsWith('data:') ? '' : slide.imageUrl}
                                                        onChange={(e) => { set('imageUrl', e.target.value); if (e.target.value) set('type', 'image'); }}
                                                        placeholder="https://example.com/banner.jpg"
                                                    />
                                                </div>

                                                <div>
                                                    <Label>Режим отображения</Label>
                                                    <div style={{ display: 'flex', gap: 6 }}>
                                                        {(['cover', 'contain', 'fill'] as ImageFit[]).map((fit) => (
                                                            <button key={fit} onClick={() => set('imageFit', fit)} style={{
                                                                flex: 1, height: 32, border: '1.5px solid',
                                                                borderColor: slide.imageFit === fit ? 'var(--brand-red)' : 'var(--line-1)',
                                                                borderRadius: 'var(--r-2)',
                                                                background: slide.imageFit === fit ? 'var(--red-tint)' : 'var(--surface)',
                                                                color: slide.imageFit === fit ? 'var(--brand-red)' : 'var(--ink-2)',
                                                                fontWeight: slide.imageFit === fit ? 600 : 400,
                                                                fontSize: 12, cursor: 'pointer', fontFamily: 'var(--font-body)',
                                                            }}>
                                                                {IMAGE_FIT_LABELS[fit].split(' ')[0]}
                                                            </button>
                                                        ))}
                                                    </div>
                                                    <div style={{ marginTop: 5, fontSize: 11, color: 'var(--ink-3)' }}>
                                                        {IMAGE_FIT_LABELS[slide.imageFit]}
                                                    </div>
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                )}

                                {/* ── TAB: Text ── */}
                                {tab === 'text' && (
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: 13 }}>
                                        <div>
                                            <Label>Подпись (eyebrow)</Label>
                                            <input style={inputStyle} value={slide.eyebrow}
                                                onChange={(e) => set('eyebrow', e.target.value)}
                                                placeholder="Например: B2B-снабжение" />
                                        </div>
                                        <div>
                                            <Label>Заголовок *</Label>
                                            <textarea style={textareaStyle} value={slide.title}
                                                onChange={(e) => set('title', e.target.value)}
                                                placeholder="Главный заголовок слайда" rows={2} />
                                        </div>
                                        <div>
                                            <Label>Текст</Label>
                                            <textarea style={textareaStyle} value={slide.text}
                                                onChange={(e) => set('text', e.target.value)}
                                                placeholder="Описание или уточнение" rows={3} />
                                        </div>
                                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                                            <div>
                                                <Label>Кнопка 1 — текст</Label>
                                                <input style={inputStyle} value={slide.cta1Label}
                                                    onChange={(e) => set('cta1Label', e.target.value)}
                                                    placeholder="Открыть каталог" />
                                            </div>
                                            <div>
                                                <Label>Кнопка 1 — ссылка</Label>
                                                <input style={inputStyle} value={slide.cta1Link}
                                                    onChange={(e) => set('cta1Link', e.target.value)}
                                                    placeholder="/catalog" />
                                            </div>
                                            <div>
                                                <Label>Кнопка 2 — текст</Label>
                                                <input style={inputStyle} value={slide.cta2Label}
                                                    onChange={(e) => set('cta2Label', e.target.value)}
                                                    placeholder="Необязательная кнопка" />
                                            </div>
                                            <div>
                                                <Label>Кнопка 2 — ссылка</Label>
                                                <input style={inputStyle} value={slide.cta2Link}
                                                    onChange={(e) => set('cta2Link', e.target.value)}
                                                    placeholder="/contacts" />
                                            </div>
                                        </div>
                                    </div>
                                )}

                                {/* ── TAB: Products ── */}
                                {tab === 'products' && (
                                    <ProductsTab
                                        products={slide.products}
                                        onChange={(p) => set('products', p)}
                                        onOpenPicker={() => setPickerOpen(true)}
                                    />
                                )}
                            </div>

                            {/* Footer */}
                            <div style={{
                                padding: '10px 20px', borderTop: '1px solid var(--line-1)',
                                display: 'flex', alignItems: 'center', gap: 10, flexShrink: 0,
                            }}>
                                <div style={{ flex: 1 }} />
                                <button onClick={onClose} style={{
                                    height: 36, padding: '0 16px', border: '1px solid var(--line-2)',
                                    borderRadius: 'var(--r-3)', background: 'transparent', cursor: 'pointer',
                                    fontSize: 13, color: 'var(--ink-2)', fontFamily: 'var(--font-body)',
                                }}>
                                    Отмена
                                </button>
                                <button onClick={handleSave} style={{
                                    height: 36, padding: '0 22px', border: 0,
                                    borderRadius: 'var(--r-3)', background: 'var(--brand-red)',
                                    color: '#fff', fontWeight: 600, fontSize: 13,
                                    cursor: 'pointer', fontFamily: 'var(--font-body)',
                                }}>
                                    Сохранить слайд
                                </button>
                            </div>
                        </div>
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
