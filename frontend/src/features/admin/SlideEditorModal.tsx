import React, { useState, useEffect, useRef, useCallback } from 'react';
import { App } from 'antd';
import type { Slide, SlideProduct, SlideType, GradientPreset, ImageFit, TextBlock, TextStyle, CtaButton } from '@/types/slider';
import { makeSlide, makeTextBlock, makeCtaButton, GRADIENT_PRESETS, GRADIENT_LABELS, IMAGE_FIT_LABELS } from '@/types/slider';
import ProductPickerModal from './ProductPickerModal';

// ── Icons ──────────────────────────────────────────────────────
const ArrRight = ({ width = 12, height = 12 }: { width?: number; height?: number }) => (
    <svg viewBox="0 0 24 24" width={width} height={height} fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M5 12h14M13 6l6 6-6 6"/>
    </svg>
);
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
const TextIcon = () => (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M4 6h16M4 12h10M4 18h12"/>
    </svg>
);

// ── Styles ─────────────────────────────────────────────────────
const Label = ({ children }: { children: React.ReactNode }) => (
    <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-3)', marginBottom: 5, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
        {children}
    </div>
);

const inputStyle: React.CSSProperties = {
    width: '100%', height: 34, padding: '0 10px',
    border: '1px solid var(--line-1)', borderRadius: 'var(--r-2)',
    background: 'var(--surface)', fontSize: 13, color: 'var(--ink-1)',
    fontFamily: 'var(--font-body)', outline: 'none', boxSizing: 'border-box',
};

const textareaStyle: React.CSSProperties = {
    width: '100%', padding: '8px 10px',
    border: '1px solid var(--line-1)', borderRadius: 'var(--r-2)',
    background: 'var(--surface)', fontSize: 13, color: 'var(--ink-1)',
    fontFamily: 'var(--font-body)', outline: 'none', resize: 'vertical',
    minHeight: 56, boxSizing: 'border-box', lineHeight: 1.5,
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

// ── Background style helper ────────────────────────────────────
const getSlideBgStyle = (slide: Slide): React.CSSProperties => {
    if (slide.type === 'image' && slide.imageUrl) {
        const fit = (slide.imageFit as string | undefined) ?? 'cover';
        return {
            backgroundImage: `url(${slide.imageUrl})`,
            backgroundSize: fit === 'contain' ? 'contain' : fit === 'fill' ? '100% 100%' : 'cover',
            backgroundPosition: 'center',
            backgroundRepeat: 'no-repeat',
        };
    }
    const grad = slide.gradientPreset === 'custom' ? slide.customGradient : GRADIENT_PRESETS[slide.gradientPreset];
    return { background: grad };
};

// ── Text style toggle button ───────────────────────────────────
const StyleBtn = ({ active, onClick, title, children }: { active: boolean; onClick: () => void; title: string; children: React.ReactNode }) => (
    <button title={title} onClick={onClick} style={{
        width: 28, height: 28, border: '1px solid',
        borderColor: active ? 'var(--brand-red)' : 'var(--line-1)',
        borderRadius: 'var(--r-2)',
        background: active ? 'var(--red-tint)' : 'var(--surface)',
        color: active ? 'var(--brand-red)' : 'var(--ink-2)',
        cursor: 'pointer', fontSize: 12, fontWeight: 700,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        flexShrink: 0,
    }}>
        {children}
    </button>
);

// ── Text style editor row ──────────────────────────────────────
const TextStyleRow = ({ style, onChange }: { style: TextStyle; onChange: (s: TextStyle) => void }) => {
    const toggle = (key: keyof TextStyle) => onChange({ ...style, [key]: !style[key] });
    return (
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, flexWrap: 'wrap' }}>
            <StyleBtn active={style.bold} onClick={() => toggle('bold')} title="Жирный"><b>B</b></StyleBtn>
            <StyleBtn active={style.italic} onClick={() => toggle('italic')} title="Курсив"><i>I</i></StyleBtn>
            <StyleBtn active={style.underline} onClick={() => toggle('underline')} title="Подчёркнутый"><u>U</u></StyleBtn>
            <StyleBtn active={style.strikethrough} onClick={() => toggle('strikethrough')} title="Зачёркнутый"><s>S</s></StyleBtn>
            <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginLeft: 4 }}>
                <input
                    type="number" min={8} max={120}
                    value={style.size}
                    onChange={(e) => onChange({ ...style, size: Math.max(8, Math.min(120, Number(e.target.value))) })}
                    style={{ ...inputStyle, width: 54, textAlign: 'center', height: 28, padding: '0 6px' }}
                />
                <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>px</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginLeft: 4 }}>
                <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>Цвет</span>
                <input
                    type="color"
                    value={style.color.startsWith('rgba') ? '#ffffff' : style.color}
                    onChange={(e) => onChange({ ...style, color: e.target.value })}
                    style={{ width: 28, height: 28, border: '1px solid var(--line-1)', borderRadius: 'var(--r-2)', padding: 2, cursor: 'pointer', background: 'var(--surface)' }}
                />
            </div>
        </div>
    );
};

// ── Preview ────────────────────────────────────────────────────
// Реальный слайд рендерится с фиксированной шириной REAL_W × REAL_H
// и масштабируется через transform:scale до ширины превью-контейнера.
// Так превью всегда 1:1 совпадает с главной страницей.
const REAL_W = 1200;
const REAL_H = 300;

type DrawMode = 'idle' | 'drawing' | 'moving';

interface SlidePreviewProps {
    slide: Slide;
    activeBlockId: string | null;
    onBlocksChange: (blocks: TextBlock[]) => void;
    onActiveBlockChange: (id: string | null) => void;
    addingText: boolean;
    onAddingTextDone: () => void;
    onSlideChange: React.Dispatch<React.SetStateAction<Slide>>;
}

// Рендерит содержимое слайда с теми же стилями что на главной.
// Используется и в превью (внутри scale-контейнера), и экспортируется для HomePage.
const SlideContent = ({
    slide,
    activeBlockId,
    addingText,
    onBlockMouseDown,
    onProductsMouseDown,
    drawRect,
}: {
    slide: Slide;
    activeBlockId?: string | null;
    addingText?: boolean;
    onBlockMouseDown?: (e: React.MouseEvent, block: TextBlock) => void;
    onProductsMouseDown?: (e: React.MouseEvent) => void;
    drawRect?: { x: number; y: number; w: number; h: number } | null;
}) => {
    const bgStyle = getSlideBgStyle(slide);
    return (
        <>
            {/* Background applied by parent */}
            {/* Watermark */}
            <img src="/logo-light.png" alt="" style={{
                position: 'absolute', right: -24, bottom: -28,
                height: REAL_H, width: 'auto', opacity: .12,
                pointerEvents: 'none',
            }} />

            {/* Grid hint */}
            {addingText && (
                <div style={{
                    position: 'absolute', inset: 0,
                    backgroundImage: 'repeating-linear-gradient(0deg,transparent,transparent 27px,rgba(255,255,255,.06) 28px),repeating-linear-gradient(90deg,transparent,transparent 27px,rgba(255,255,255,.06) 28px)',
                    pointerEvents: 'none',
                }} />
            )}

            {/* Draw rect */}
            {drawRect && drawRect.w > 2 && drawRect.h > 2 && (
                <div style={{
                    position: 'absolute',
                    left: drawRect.x, top: drawRect.y,
                    width: drawRect.w, height: drawRect.h,
                    border: '2px dashed rgba(255,255,255,.8)',
                    background: 'rgba(255,255,255,.08)',
                    pointerEvents: 'none', borderRadius: 2,
                }} />
            )}

            {/* Text blocks — идентично HomePage */}
            {slide.textBlocks.map((block) => {
                const isActive = activeBlockId != null && block.id === activeBlockId;
                return (
                    <div
                        key={block.id}
                        onMouseDown={onBlockMouseDown ? (e) => onBlockMouseDown(e, block) : undefined}
                        style={{
                            position: 'absolute',
                            left: `${block.x}%`,
                            top: `${block.y}%`,
                            width: `${block.width}%`,
                            height: `${block.height}%`,
                            background: block.background || 'transparent',
                            borderRadius: block.borderRadius,
                            padding: '8px 10px',
                            boxSizing: 'border-box',
                            zIndex: 2,
                            display: 'flex',
                            flexDirection: 'column',
                            justifyContent: 'flex-start',
                            cursor: onBlockMouseDown ? (addingText ? 'crosshair' : 'grab') : undefined,
                            outline: isActive ? '2px solid rgba(255,255,255,.9)' : 'none',
                            outlineOffset: 1,
                            overflow: 'hidden',
                        }}
                    >
                        {block.heading && (
                            <div style={{
                                fontFamily: 'var(--font-head)',
                                fontSize: block.headingStyle.size,
                                fontWeight: block.headingStyle.bold ? 700 : 400,
                                fontStyle: block.headingStyle.italic ? 'italic' : 'normal',
                                textDecoration: [
                                    block.headingStyle.underline ? 'underline' : '',
                                    block.headingStyle.strikethrough ? 'line-through' : '',
                                ].filter(Boolean).join(' ') || 'none',
                                color: block.headingStyle.color,
                                lineHeight: 1.15,
                                letterSpacing: '-0.02em',
                                margin: '0 0 8px',
                            }}>
                                {block.heading}
                            </div>
                        )}
                        {block.body && (
                            <div style={{
                                fontSize: block.bodyStyle.size,
                                fontWeight: block.bodyStyle.bold ? 700 : 400,
                                fontStyle: block.bodyStyle.italic ? 'italic' : 'normal',
                                textDecoration: [
                                    block.bodyStyle.underline ? 'underline' : '',
                                    block.bodyStyle.strikethrough ? 'line-through' : '',
                                ].filter(Boolean).join(' ') || 'none',
                                color: block.bodyStyle.color,
                                lineHeight: 1.5,
                                margin: 0,
                            }}>
                                {block.body}
                            </div>
                        )}
                        {!block.heading && !block.body && onBlockMouseDown && (
                            <div style={{ fontSize: 12, color: 'rgba(255,255,255,.45)', fontStyle: 'italic' }}>
                                Введите текст ниже
                            </div>
                        )}
                    </div>
                );
            })}

            {/* CTA кнопки — идентично HomePage */}
            {slide.cta?.length > 0 && (
                <div style={{
                    position: 'absolute',
                    left: `${slide.textBlocks?.[0]?.x ?? 5}%`,
                    bottom: 52,
                    display: 'flex', gap: 10, zIndex: 3,
                }}>
                    {slide.cta.map((btn) => btn.label ? (
                        <button key={btn.id} style={{
                            display: 'inline-flex', alignItems: 'center', gap: 6,
                            background: btn.variant === 'primary' ? 'var(--surface)' : 'var(--overlay-white-14)',
                            color: btn.variant === 'primary' ? 'var(--ink-1)' : '#fff',
                            fontWeight: 600,
                            padding: '0 18px', height: 'var(--btn-h-lg)', border: 'none', borderRadius: 'var(--r-3)',
                            fontSize: 'var(--text-md)', cursor: 'default', fontFamily: 'var(--font-body)',
                        }}>
                            {btn.label} {btn.variant === 'primary' && <ArrRight />}
                        </button>
                    ) : null)}
                </div>
            )}

            {/* Products block — идентично HomePage */}
            {slide.products.length > 0 && (
                <div
                    onMouseDown={onProductsMouseDown}
                    style={{
                        position: 'absolute',
                        right: `${slide.productsRight}%`,
                        top: `${slide.productsTop}%`,
                        width: `${slide.productsWidth}%`,
                        display: 'flex', flexDirection: 'column', gap: 8,
                        zIndex: 2,
                        // outline поверх, не влияет на layout
                        outline: onProductsMouseDown ? '1.5px dashed rgba(255,255,255,.5)' : 'none',
                        cursor: onProductsMouseDown ? (addingText ? 'crosshair' : 'grab') : undefined,
                    }}
                >
                    {slide.products.slice(0, 3).map((p) => (
                        <div key={p.id} style={{
                            display: 'flex', alignItems: 'center', gap: 10,
                            background: 'rgba(255,255,255,.12)', backdropFilter: 'blur(6px)',
                            border: '1px solid rgba(255,255,255,.2)',
                            borderRadius: 'var(--r-3)', padding: '8px 10px',
                            cursor: onProductsMouseDown ? undefined : 'pointer',
                            transition: 'background .12s',
                            height: slide.productsItemHeight,
                            boxSizing: 'border-box', overflow: 'hidden',
                        }}>
                            {p.imageUrl && (
                                <div style={{
                                    width: 40, height: 40, borderRadius: 'var(--r-2)',
                                    background: 'rgba(255,255,255,.9)', flexShrink: 0,
                                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                                    overflow: 'hidden',
                                }}>
                                    <img src={p.imageUrl} alt={p.name} style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} />
                                </div>
                            )}
                            <div style={{ flex: 1, minWidth: 0 }}>
                                {p.fields?.showName !== false && (
                                    <div style={{ fontSize: 'var(--text-sm)', fontWeight: 500, color: '#fff', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                        {p.name}
                                    </div>
                                )}
                                {p.fields?.showShortDescription && p.shortDescription && (
                                    <div style={{ fontSize: 'var(--text-xs)', color: 'rgba(255,255,255,.7)', lineHeight: 1.3, marginTop: 2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                        {p.shortDescription}
                                    </div>
                                )}
                                {p.fields?.showDescription && p.description && !p.fields?.showShortDescription && (
                                    <div style={{ fontSize: 'var(--text-xs)', color: 'rgba(255,255,255,.7)', lineHeight: 1.3, marginTop: 2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                        {p.description}
                                    </div>
                                )}
                                {p.fields?.showPrice !== false && (
                                    <div style={{ fontSize: 'var(--text-sm)', fontWeight: 700, color: 'rgba(255,255,255,.9)', fontVariantNumeric: 'tabular-nums' }}>
                                        {new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', minimumFractionDigits: 0 }).format(p.price)}
                                    </div>
                                )}
                            </div>
                            <ArrRight width={12} height={12} />
                        </div>
                    ))}
                    {onProductsMouseDown && (
                        <div style={{ fontSize: 9, color: 'rgba(255,255,255,.5)', textAlign: 'center' }}>↕ перетащить</div>
                    )}
                </div>
            )}
        </>
    );
};

const SlidePreview = ({ slide, activeBlockId, onBlocksChange, onActiveBlockChange, addingText, onAddingTextDone, onSlideChange }: SlidePreviewProps) => {
    const wrapperRef = useRef<HTMLDivElement>(null);
    const [scale, setScale] = useState(1);
    const drawMode = useRef<DrawMode>('idle');
    const drawStart = useRef({ x: 0, y: 0 });
    const dragBlockId = useRef<string | null>(null);
    const dragOffset = useRef({ dx: 0, dy: 0 });
    const draggingProducts = useRef(false);
    const productsDragOffset = useRef({ dx: 0, dy: 0 });
    const [drawRect, setDrawRect] = useState<{ x: number; y: number; w: number; h: number } | null>(null);

    // Вычисляем scale под ширину wrapper
    useEffect(() => {
        if (!wrapperRef.current) return;
        const obs = new ResizeObserver(([entry]) => {
            setScale(entry.contentRect.width / REAL_W * 0.85);
        });
        obs.observe(wrapperRef.current);
        setScale(wrapperRef.current.getBoundingClientRect().width / REAL_W * 0.85);
        return () => obs.disconnect();
    }, []);

    const clamp = (v: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, v));

    // Координаты мыши → координаты в реальном слайде (1200×300)
    const toReal = (clientX: number, clientY: number) => {
        if (!wrapperRef.current) return { x: 0, y: 0 };
        const r = wrapperRef.current.getBoundingClientRect();
        return {
            x: (clientX - r.left) / scale,
            y: (clientY - r.top) / scale,
        };
    };

    // ── Drawing a new block ──────────────────────────────────
    const onContainerMouseDown = useCallback((e: React.MouseEvent) => {
        if (!addingText) return;
        e.preventDefault();
        const { x: sx, y: sy } = toReal(e.clientX, e.clientY);
        drawStart.current = { x: sx * scale, y: sy * scale };
        drawMode.current = 'drawing';
        setDrawRect({ x: sx * scale, y: sy * scale, w: 0, h: 0 });

        const onMove = (ev: MouseEvent) => {
            if (drawMode.current !== 'drawing') return;
            const { x: cx, y: cy } = toReal(ev.clientX, ev.clientY);
            setDrawRect({
                x: Math.min(cx * scale, drawStart.current.x),
                y: Math.min(cy * scale, drawStart.current.y),
                w: Math.abs(cx * scale - drawStart.current.x),
                h: Math.abs(cy * scale - drawStart.current.y),
            });
        };

        const onUp = (ev: MouseEvent) => {
            if (drawMode.current !== 'drawing') return;
            drawMode.current = 'idle';
            const { x: cx, y: cy } = toReal(ev.clientX, ev.clientY);
            const x1r = Math.min(cx, drawStart.current.x / scale);
            const y1r = Math.min(cy, drawStart.current.y / scale);
            const wr = Math.abs(cx - drawStart.current.x / scale);
            const hr = Math.abs(cy - drawStart.current.y / scale);
            setDrawRect(null);

            if (wr > 3 && hr > 3) {
                const block = makeTextBlock({
                    x: clamp((x1r / REAL_W) * 100, 0, 95),
                    y: clamp((y1r / REAL_H) * 100, 0, 95),
                    width: clamp((wr / REAL_W) * 100, 5, 100),
                    height: clamp((hr / REAL_H) * 100, 5, 100),
                });
                onBlocksChange([...slide.textBlocks, block]);
                onActiveBlockChange(block.id);
            }
            onAddingTextDone();
            window.removeEventListener('mousemove', onMove);
            window.removeEventListener('mouseup', onUp);
        };

        window.addEventListener('mousemove', onMove);
        window.addEventListener('mouseup', onUp);
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [addingText, slide.textBlocks, scale]);

    // ── Dragging a text block ────────────────────────────────
    const onBlockMouseDown = useCallback((e: React.MouseEvent, block: TextBlock) => {
        if (addingText) return;
        e.preventDefault();
        e.stopPropagation();
        onActiveBlockChange(block.id);
        dragBlockId.current = block.id;
        const { x, y } = toReal(e.clientX, e.clientY);
        const blockLeftReal = (block.x / 100) * REAL_W;
        const blockTopReal  = (block.y / 100) * REAL_H;
        dragOffset.current = { dx: x - blockLeftReal, dy: y - blockTopReal };
        drawMode.current = 'moving';

        const onMove = (ev: MouseEvent) => {
            if (drawMode.current !== 'moving' || !dragBlockId.current) return;
            const { x: rx, y: ry } = toReal(ev.clientX, ev.clientY);
            const newX = clamp(((rx - dragOffset.current.dx) / REAL_W) * 100, 0, 95);
            const newY = clamp(((ry - dragOffset.current.dy) / REAL_H) * 100, 0, 95);
            onBlocksChange(slide.textBlocks.map((b) => b.id === dragBlockId.current ? { ...b, x: newX, y: newY } : b));
        };

        const onUp = () => {
            drawMode.current = 'idle';
            dragBlockId.current = null;
            window.removeEventListener('mousemove', onMove);
            window.removeEventListener('mouseup', onUp);
        };

        window.addEventListener('mousemove', onMove);
        window.addEventListener('mouseup', onUp);
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [addingText, slide.textBlocks, scale]);

    // ── Dragging products block ──────────────────────────────
    const onProductsMouseDown = useCallback((e: React.MouseEvent) => {
        if (addingText || slide.products.length === 0) return;
        e.preventDefault();
        e.stopPropagation();
        draggingProducts.current = true;
        const widthReal = (slide.productsWidth / 100) * REAL_W;
        const { x, y } = toReal(e.clientX, e.clientY);
        const blockLeft = REAL_W - (slide.productsRight / 100) * REAL_W - widthReal;
        const blockTop  = (slide.productsTop / 100) * REAL_H;
        productsDragOffset.current = { dx: x - blockLeft, dy: y - blockTop };

        const onMove = (ev: MouseEvent) => {
            if (!draggingProducts.current) return;
            const { x: rx, y: ry } = toReal(ev.clientX, ev.clientY);
            const newLeft = rx - productsDragOffset.current.dx;
            const newRight = clamp(((REAL_W - newLeft - widthReal) / REAL_W) * 100, 0, 80);
            const newTop   = clamp(((ry - productsDragOffset.current.dy) / REAL_H) * 100, 0, 80);
            onSlideChange((prev: Slide) => ({ ...prev, productsRight: newRight, productsTop: newTop }));
        };

        const onUp = () => {
            draggingProducts.current = false;
            window.removeEventListener('mousemove', onMove);
            window.removeEventListener('mouseup', onUp);
        };

        window.addEventListener('mousemove', onMove);
        window.addEventListener('mouseup', onUp);
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [addingText, slide.products.length, slide.productsRight, slide.productsTop, slide.productsWidth, scale]);

    const bgStyle = getSlideBgStyle(slide);
    const previewH = Math.round(REAL_H * scale);

    return (
        // Внешний wrapper задаёт реальную высоту превью и clips overflow
        <div
            ref={wrapperRef}
            style={{
                position: 'relative',
                width: '100%',
                height: previewH,
                borderRadius: 'var(--r-4)',
                overflow: 'hidden',
                userSelect: 'none',
                cursor: addingText ? 'crosshair' : 'default',
            }}
            onMouseDown={onContainerMouseDown}
        >
            {/* Масштабированный реальный слайд 1200×300 */}
            <div style={{
                position: 'absolute',
                top: 0, left: 0,
                width: REAL_W,
                height: REAL_H,
                transformOrigin: 'top left',
                transform: `scale(${scale})`,
                ...bgStyle,
            }}>
                <SlideContent
                    slide={slide}
                    activeBlockId={activeBlockId}
                    addingText={addingText}
                    onBlockMouseDown={onBlockMouseDown}
                    onProductsMouseDown={onProductsMouseDown}
                    drawRect={drawRect ? {
                        x: drawRect.x / scale,
                        y: drawRect.y / scale,
                        w: drawRect.w / scale,
                        h: drawRect.h / scale,
                    } : null}
                />
            </div>
        </div>
    );
};

// ── Text block editor panel ────────────────────────────────────
const BlockEditor = ({
    block,
    onChange,
    onDelete,
}: {
    block: TextBlock;
    onChange: (b: TextBlock) => void;
    onDelete: () => void;
}) => {
    const set = <K extends keyof TextBlock>(key: K, value: TextBlock[K]) => onChange({ ...block, [key]: value });

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {/* Заголовок */}
            <div style={{ background: 'var(--surface-2)', borderRadius: 'var(--r-3)', padding: '10px 12px', display: 'flex', flexDirection: 'column', gap: 8 }}>
                <Label>Заголовок</Label>
                <textarea
                    style={{ ...textareaStyle, minHeight: 44 }}
                    value={block.heading}
                    onChange={(e) => set('heading', e.target.value)}
                    placeholder="Главный заголовок блока"
                    rows={2}
                />
                <TextStyleRow style={block.headingStyle} onChange={(s) => set('headingStyle', s)} />
            </div>

            {/* Текст */}
            <div style={{ background: 'var(--surface-2)', borderRadius: 'var(--r-3)', padding: '10px 12px', display: 'flex', flexDirection: 'column', gap: 8 }}>
                <Label>Текст</Label>
                <textarea
                    style={{ ...textareaStyle, minHeight: 56 }}
                    value={block.body}
                    onChange={(e) => set('body', e.target.value)}
                    placeholder="Описание или дополнительный текст"
                    rows={3}
                />
                <TextStyleRow style={block.bodyStyle} onChange={(s) => set('bodyStyle', s)} />
            </div>

            {/* Оформление блока */}
            <div style={{ background: 'var(--surface-2)', borderRadius: 'var(--r-3)', padding: '10px 12px', display: 'flex', flexDirection: 'column', gap: 8 }}>
                <Label>Оформление блока</Label>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 8 }}>
                    <div>
                        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 4 }}>Фон</div>
                        <input
                            type="color"
                            value={(() => {
                                const m = block.background.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
                                if (m) return `#${parseInt(m[1]).toString(16).padStart(2,'0')}${parseInt(m[2]).toString(16).padStart(2,'0')}${parseInt(m[3]).toString(16).padStart(2,'0')}`;
                                return block.background.startsWith('#') ? block.background : '#000000';
                            })()}
                            onChange={(e) => {
                                const hex = e.target.value;
                                const r = parseInt(hex.slice(1,3),16);
                                const g = parseInt(hex.slice(3,5),16);
                                const b = parseInt(hex.slice(5,7),16);
                                const m = block.background.match(/rgba?\([^,]+,[^,]+,[^,]+,([^)]+)\)/);
                                const alpha = m ? parseFloat(m[1]) : 1;
                                set('background', `rgba(${r},${g},${b},${alpha})`);
                            }}
                            style={{ width: '100%', height: 34, border: '1px solid var(--line-1)', borderRadius: 'var(--r-2)', padding: 2, cursor: 'pointer', background: 'var(--surface)' }}
                        />
                    </div>
                    <div>
                        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 4 }}>Прозрачность фона</div>
                        <input
                            type="range" min={0} max={100}
                            value={(() => {
                                const m = block.background.match(/rgba\([^,]+,[^,]+,[^,]+,([^)]+)\)/);
                                return m ? Math.round(parseFloat(m[1]) * 100) : (block.background === 'transparent' || block.background === 'rgba(0,0,0,0)' ? 0 : 100);
                            })()}
                            onChange={(e) => {
                                const alpha = Number(e.target.value) / 100;
                                const m = block.background.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
                                const [r, g, b] = m ? [m[1], m[2], m[3]] : ['0', '0', '0'];
                                set('background', `rgba(${r},${g},${b},${alpha})`);
                            }}
                            style={{ width: '100%', marginTop: 6 }}
                        />
                    </div>
                    <div>
                        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 4 }}>Скругление (px)</div>
                        <input
                            type="number" min={0} max={48}
                            value={block.borderRadius}
                            onChange={(e) => set('borderRadius', Math.max(0, Math.min(48, Number(e.target.value))))}
                            style={{ ...inputStyle, height: 34, textAlign: 'center' }}
                        />
                    </div>
                </div>
            </div>

            {/* Ссылка */}
            <div style={{ background: 'var(--surface-2)', borderRadius: 'var(--r-3)', padding: '10px 12px', display: 'flex', flexDirection: 'column', gap: 8 }}>
                <Label>Ссылка блока</Label>
                <input
                    style={inputStyle}
                    value={block.link}
                    onChange={(e) => set('link', e.target.value)}
                    placeholder="/catalog или https://example.com"
                />
                <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>Весь блок станет кликабельным</div>
            </div>

            {/* Удалить */}
            <button
                onClick={onDelete}
                style={{
                    height: 32, border: '1px solid var(--line-1)', borderRadius: 'var(--r-2)',
                    background: 'transparent', color: 'var(--brand-red)', cursor: 'pointer',
                    fontSize: 12, fontFamily: 'var(--font-body)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
                }}
            >
                <TrashIcon /> Удалить блок
            </button>
        </div>
    );
};

// ── Products tab ───────────────────────────────────────────────
interface ProductsTabProps {
    products: SlideProduct[];
    onChange: (products: SlideProduct[]) => void;
    onOpenPicker: () => void;
    productsRight: number;
    productsTop: number;
    productsWidth: number;
    productsItemHeight: number;
    onLayoutChange: (right: number, top: number, width: number, itemHeight: number) => void;
}

const formatPrice = (p: number) =>
    new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', minimumFractionDigits: 0 }).format(p);

const ProductsTab = ({ products, onChange, onOpenPicker, productsRight, productsTop, productsWidth, productsItemHeight, onLayoutChange }: ProductsTabProps) => {
    const dragId = useRef<number | null>(null);
    const [dragOver, setDragOver] = useState<number | null>(null);

    const remove = (id: number) => onChange(products.filter((p) => p.id !== id));

    const handleDragStart = (id: number) => (e: React.DragEvent) => { dragId.current = id; e.dataTransfer.effectAllowed = 'move'; };
    const handleDragOver = (id: number) => (e: React.DragEvent) => { e.preventDefault(); setDragOver(id); };
    const handleDrop = (targetId: number) => (e: React.DragEvent) => {
        e.preventDefault();
        const fromId = dragId.current;
        if (fromId === null || fromId === targetId) { setDragOver(null); return; }
        const ids = products.map((p) => p.id);
        const reordered = [...products];
        const [moved] = reordered.splice(ids.indexOf(fromId), 1);
        reordered.splice(ids.indexOf(targetId), 0, moved);
        onChange(reordered);
        setDragOver(null);
    };

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <div style={{ fontSize: 12, color: 'var(--ink-3)', lineHeight: 1.5 }}>
                Товары показываются на слайде. Позицию можно задать вручную или перетащить блок на превью.
            </div>

            {/* Layout controls */}
            <div style={{ background: 'var(--surface-2)', borderRadius: 'var(--r-3)', padding: '10px 12px', display: 'flex', flexDirection: 'column', gap: 8 }}>
                <Label>Позиция и размер блока товаров</Label>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                    <div>
                        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 4 }}>Отступ справа (%)</div>
                        <input
                            type="number" min={0} max={80}
                            value={Math.round(productsRight)}
                            onChange={(e) => onLayoutChange(Math.max(0, Math.min(80, Number(e.target.value))), productsTop, productsWidth, productsItemHeight)}
                            style={{ ...inputStyle, height: 32, textAlign: 'center' }}
                        />
                    </div>
                    <div>
                        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 4 }}>Отступ сверху (%)</div>
                        <input
                            type="number" min={0} max={80}
                            value={Math.round(productsTop)}
                            onChange={(e) => onLayoutChange(productsRight, Math.max(0, Math.min(80, Number(e.target.value))), productsWidth, productsItemHeight)}
                            style={{ ...inputStyle, height: 32, textAlign: 'center' }}
                        />
                    </div>
                    <div>
                        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 4 }}>Ширина (%)</div>
                        <input
                            type="number" min={10} max={60}
                            value={Math.round(productsWidth)}
                            onChange={(e) => onLayoutChange(productsRight, productsTop, Math.max(10, Math.min(60, Number(e.target.value))), productsItemHeight)}
                            style={{ ...inputStyle, height: 32, textAlign: 'center' }}
                        />
                    </div>
                    <div>
                        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 4 }}>Высота карточки (px)</div>
                        <input
                            type="number" min={32} max={120}
                            value={productsItemHeight}
                            onChange={(e) => onLayoutChange(productsRight, productsTop, productsWidth, Math.max(32, Math.min(120, Number(e.target.value))))}
                            style={{ ...inputStyle, height: 32, textAlign: 'center' }}
                        />
                    </div>
                </div>
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
                        <div key={p.id} draggable
                            onDragStart={handleDragStart(p.id)}
                            onDragOver={handleDragOver(p.id)}
                            onDrop={handleDrop(p.id)}
                            onDragEnd={() => { dragId.current = null; setDragOver(null); }}
                            style={{
                                display: 'flex', alignItems: 'center', gap: 8,
                                padding: '7px 10px', borderRadius: 'var(--r-3)',
                                background: dragOver === p.id ? 'var(--red-tint)' : 'var(--surface-2)',
                                border: `1px solid ${dragOver === p.id ? 'var(--brand-red)' : 'var(--line-1)'}`,
                                cursor: 'grab',
                            }}
                        >
                            <div style={{ color: 'var(--ink-4)', flexShrink: 0 }}><DragIcon /></div>
                            <div style={{ width: 34, height: 34, borderRadius: 'var(--r-2)', background: 'var(--surface)', flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden' }}>
                                {p.imageUrl ? <img src={p.imageUrl} alt={p.name} style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} /> : <ImgPlaceholder />}
                            </div>
                            <div style={{ flex: 1, minWidth: 0 }}>
                                <div style={{ fontSize: 12, fontWeight: 500, color: 'var(--ink-1)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{p.name}</div>
                                <div style={{ fontSize: 11, color: 'var(--ink-3)', display: 'flex', gap: 6, marginTop: 1 }}>
                                    {p.sku && <span style={{ fontFamily: 'var(--font-mono)' }}>{p.sku}</span>}
                                    {p.fields?.showPrice !== false && <span style={{ fontWeight: 600 }}>{formatPrice(p.price)}</span>}
                                </div>
                            </div>
                            <button onClick={() => remove(p.id)} style={{ width: 26, height: 26, border: 0, borderRadius: 'var(--r-2)', background: 'transparent', cursor: 'pointer', color: 'var(--ink-3)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                                <TrashIcon />
                            </button>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

// ── Main component ─────────────────────────────────────────────
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
    const [activeBlockId, setActiveBlockId] = useState<string | null>(null);
    const [addingText, setAddingText] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        if (!open) return;
        const s = initial ? { ...initial } : makeSlide();
        setSlide(s);
        setTab('background');
        setActiveBlockId(s.textBlocks.length > 0 ? s.textBlocks[0].id : null);
        setAddingText(false);
    }, [open, initial?.id]);

    const set = <K extends keyof Slide>(key: K, value: Slide[K]) =>
        setSlide((prev) => ({ ...prev, [key]: value }));

    const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;
        if (file.size > 5 * 1024 * 1024) { messageApi.warning('Файл слишком большой, максимум 5 МБ'); return; }
        const reader = new FileReader();
        reader.onload = (ev) => {
            setSlide((prev) => ({ ...prev, imageUrl: ev.target?.result as string, type: 'image' }));
        };
        reader.readAsDataURL(file);
        e.target.value = '';
    };

    const handleProductsConfirm = (products: SlideProduct[]) => {
        if (products.length > 0) {
            const existingMap = new Map(slide.products.map((p) => [p.id, p]));
            set('products', [...slide.products, ...products.filter((p) => !existingMap.has(p.id))]);
        }
        setPickerOpen(false);
    };

    const handleBlocksChange = (blocks: TextBlock[]) => {
        setSlide((prev) => ({ ...prev, textBlocks: blocks }));
    };

    const handleBlockChange = (updated: TextBlock) => {
        setSlide((prev) => ({
            ...prev,
            textBlocks: prev.textBlocks.map((b) => b.id === updated.id ? updated : b),
        }));
    };

    const handleDeleteBlock = (id: string) => {
        const remaining = slide.textBlocks.filter((b) => b.id !== id);
        setSlide((prev) => ({ ...prev, textBlocks: remaining }));
        setActiveBlockId(remaining.length > 0 ? remaining[0].id : null);
    };

    const handleSave = () => {
        onSave(slide);
    };

    const activeBlock = slide.textBlocks.find((b) => b.id === activeBlockId) ?? null;

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
                    width: '90vw', height: '94vh',
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
                        <button onClick={onClose} style={{
                            width: 32, height: 32, border: 0, borderRadius: 'var(--r-3)',
                            background: 'transparent', cursor: 'pointer', color: 'var(--ink-3)',
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                        }}>
                            <CloseIcon />
                        </button>
                    </div>

                    {/* Body: одна колонка — превью сверху (фиксировано), вкладки + форма снизу (скролл) */}
                    <div style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>

                        {/* Превью — не скроллируется */}
                        <div style={{ flexShrink: 0, padding: '14px 20px 0', display: 'flex', flexDirection: 'column', gap: 8 }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                                <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.05em', flex: 1 }}>
                                    Превью{tab === 'text' ? ' — выделите область для текста' : ''}
                                </div>
                                {tab === 'text' && (
                                    <button
                                        onClick={() => setAddingText(true)}
                                        style={{
                                            height: 28, padding: '0 12px',
                                            border: addingText ? '1.5px solid var(--brand-red)' : '1px solid var(--line-1)',
                                            borderRadius: 'var(--r-2)',
                                            background: addingText ? 'var(--red-tint)' : 'var(--surface)',
                                            color: addingText ? 'var(--brand-red)' : 'var(--ink-2)',
                                            fontWeight: addingText ? 600 : 400,
                                            fontSize: 12, cursor: 'pointer', fontFamily: 'var(--font-body)',
                                            display: 'inline-flex', alignItems: 'center', gap: 5,
                                        }}
                                    >
                                        <TextIcon />
                                        {addingText ? 'Выделите область…' : 'Добавить текст'}
                                    </button>
                                )}
                            </div>

                            <SlidePreview
                                slide={slide}
                                activeBlockId={activeBlockId}
                                onBlocksChange={handleBlocksChange}
                                onActiveBlockChange={setActiveBlockId}
                                addingText={addingText}
                                onAddingTextDone={() => { setAddingText(false); setTab('text'); }}
                                onSlideChange={setSlide}
                            />

                            {/* Список блоков под превью */}
                            {tab === 'text' && slide.textBlocks.length > 0 && (
                                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                                    {slide.textBlocks.map((b, i) => (
                                        <button
                                            key={b.id}
                                            onClick={() => setActiveBlockId(b.id)}
                                            style={{
                                                height: 28, padding: '0 12px', border: '1px solid',
                                                borderColor: b.id === activeBlockId ? 'var(--brand-red)' : 'var(--line-1)',
                                                borderRadius: 'var(--r-2)',
                                                background: b.id === activeBlockId ? 'var(--red-tint)' : 'var(--surface-2)',
                                                color: b.id === activeBlockId ? 'var(--brand-red)' : 'var(--ink-2)',
                                                cursor: 'pointer', fontSize: 12, fontFamily: 'var(--font-body)',
                                                overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: 220,
                                            }}
                                        >
                                            Блок {i + 1}{b.heading ? ` — ${b.heading.slice(0, 24)}${b.heading.length > 24 ? '…' : ''}` : ''}
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>{/* end preview block */}

                        {/* Вкладки — прилипают под превью, тоже не скроллируются */}
                        <div style={{
                            padding: '8px 20px 0', borderBottom: '1px solid var(--line-1)',
                            display: 'flex', gap: 4, flexShrink: 0,
                        }}>
                            <TabBtn active={tab === 'background'} onClick={() => setTab('background')}>Фон</TabBtn>
                            <TabBtn active={tab === 'text'} onClick={() => setTab('text')}>
                                Текст{slide.textBlocks.length > 0 ? ` (${slide.textBlocks.length})` : ''}
                            </TabBtn>
                            <TabBtn active={tab === 'products'} onClick={() => setTab('products')}>
                                Товары{slide.products.length > 0 ? ` (${slide.products.length})` : ''}
                            </TabBtn>
                        </div>

                        {/* Форма — скроллируется */}
                        <div style={{ flex: 1, overflowY: 'auto', padding: '14px 20px' }}>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>

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
                                                        <input style={inputStyle} value={slide.customGradient}
                                                            onChange={(e) => set('customGradient', e.target.value)}
                                                            placeholder="linear-gradient(135deg, #1E3A5F 0%, #0d2240 100%)" />
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
                                                        <img src={slide.imageUrl} alt="" style={{ width: '100%', height: 120, objectFit: 'cover', borderRadius: 'var(--r-3)', display: 'block' }} />
                                                        <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                                                            <button onClick={() => fileInputRef.current?.click()} style={{ height: 30, padding: '0 12px', border: '1px solid var(--line-2)', borderRadius: 'var(--r-2)', background: 'var(--surface)', fontSize: 12, cursor: 'pointer', fontFamily: 'var(--font-body)', color: 'var(--ink-2)' }}>Заменить</button>
                                                            <button onClick={() => set('imageUrl', '')} style={{ height: 30, padding: '0 12px', border: '1px solid var(--line-2)', borderRadius: 'var(--r-2)', background: 'var(--surface)', fontSize: 12, cursor: 'pointer', fontFamily: 'var(--font-body)', color: 'var(--brand-red)' }}>Удалить</button>
                                                        </div>
                                                    </div>
                                                ) : (
                                                    <button onClick={() => fileInputRef.current?.click()} style={{ width: '100%', height: 90, border: '2px dashed var(--line-2)', borderRadius: 'var(--r-3)', background: 'var(--surface-2)', cursor: 'pointer', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 6, color: 'var(--ink-3)', fontSize: 12, fontFamily: 'var(--font-body)' }}>
                                                        <ImgPlaceholder />
                                                        Загрузить изображение (до 5 МБ)
                                                    </button>
                                                )}
                                                <div>
                                                    <Label>URL изображения</Label>
                                                    <input style={inputStyle} value={slide.imageUrl.startsWith('data:') ? '' : slide.imageUrl}
                                                        onChange={(e) => { set('imageUrl', e.target.value); if (e.target.value) set('type', 'image'); }}
                                                        placeholder="https://example.com/banner.jpg" />
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
                                                <div style={{ fontSize: 11, color: 'var(--ink-4)' }}>
                                                    Рекомендуемый размер: 1920×600 px. <b>cover</b> — обрезает края, <b>contain</b> — вписывает целиком, <b>fill</b> — растягивает.
                                                </div>
                                            </div>
                                        )}

                                        {/* CTA кнопки */}
                                        <div style={{ background: 'var(--surface-2)', borderRadius: 'var(--r-3)', padding: '10px 12px', display: 'flex', flexDirection: 'column', gap: 8 }}>
                                            <Label>Кнопки (CTA)</Label>
                                            {slide.cta.map((btn, i) => (
                                                <div key={btn.id} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr auto auto', gap: 6, alignItems: 'end' }}>
                                                    <div>
                                                        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 3 }}>Текст</div>
                                                        <input
                                                            style={inputStyle}
                                                            value={btn.label}
                                                            onChange={(e) => set('cta', slide.cta.map((b) => b.id === btn.id ? { ...b, label: e.target.value } : b))}
                                                            placeholder="Открыть каталог"
                                                        />
                                                    </div>
                                                    <div>
                                                        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 3 }}>Ссылка</div>
                                                        <input
                                                            style={inputStyle}
                                                            value={btn.link}
                                                            onChange={(e) => set('cta', slide.cta.map((b) => b.id === btn.id ? { ...b, link: e.target.value } : b))}
                                                            placeholder="/catalog"
                                                        />
                                                    </div>
                                                    {/* Вариант */}
                                                    <button
                                                        title={btn.variant === 'primary' ? 'Основная (светлая)' : 'Вторичная (прозрачная)'}
                                                        onClick={() => set('cta', slide.cta.map((b) => b.id === btn.id ? { ...b, variant: b.variant === 'primary' ? 'secondary' : 'primary' } : b))}
                                                        style={{
                                                            height: 34, width: 34, border: '1px solid var(--line-1)', borderRadius: 'var(--r-2)',
                                                            background: btn.variant === 'primary' ? 'var(--surface)' : 'var(--brand-navy)',
                                                            cursor: 'pointer', flexShrink: 0, fontSize: 10, color: btn.variant === 'primary' ? 'var(--ink-1)' : '#fff',
                                                            fontFamily: 'var(--font-body)', fontWeight: 600,
                                                        }}
                                                    >
                                                        {btn.variant === 'primary' ? 'P' : 'S'}
                                                    </button>
                                                    {/* Удалить */}
                                                    <button
                                                        onClick={() => set('cta', slide.cta.filter((b) => b.id !== btn.id))}
                                                        style={{
                                                            height: 34, width: 34, border: '1px solid var(--line-1)', borderRadius: 'var(--r-2)',
                                                            background: 'transparent', cursor: 'pointer', flexShrink: 0,
                                                            color: 'var(--brand-red)', display: 'flex', alignItems: 'center', justifyContent: 'center',
                                                        }}
                                                    >
                                                        <TrashIcon />
                                                    </button>
                                                </div>
                                            ))}
                                            <button
                                                onClick={() => set('cta', [...slide.cta, makeCtaButton({ variant: slide.cta.length === 0 ? 'primary' : 'secondary' })])}
                                                style={{
                                                    height: 32, border: '1.5px dashed var(--brand-red)', borderRadius: 'var(--r-2)',
                                                    background: 'var(--red-tint)', color: 'var(--brand-red)',
                                                    fontSize: 12, fontWeight: 600, cursor: 'pointer', fontFamily: 'var(--font-body)',
                                                    display: 'inline-flex', alignItems: 'center', gap: 5, padding: '0 12px', alignSelf: 'flex-start',
                                                }}
                                            >
                                                <PlusIcon /> Добавить кнопку
                                            </button>
                                            <div style={{ fontSize: 11, color: 'var(--ink-4)' }}>P — основная (светлая), S — вторичная (прозрачная). Пустой текст — кнопка не отображается.</div>
                                        </div>
                                    </div>
                                )}

                                {/* ── TAB: Text — active block editor ── */}
                                {tab === 'text' && (
                                    activeBlock
                                        ? <BlockEditor block={activeBlock} onChange={handleBlockChange} onDelete={() => handleDeleteBlock(activeBlock.id)} />
                                        : (
                                            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', gap: 12, color: 'var(--ink-3)', textAlign: 'center' }}>
                                                <TextIcon />
                                                <div style={{ fontSize: 13 }}>
                                                    Нажмите «Добавить текст» и<br />выделите область на превью
                                                </div>
                                            </div>
                                        )
                                )}

                                {/* ── TAB: Products ── */}
                                {tab === 'products' && (
                                    <ProductsTab
                                        products={slide.products}
                                        onChange={(p) => set('products', p)}
                                        onOpenPicker={() => setPickerOpen(true)}
                                        productsRight={slide.productsRight}
                                        productsTop={slide.productsTop}
                                        productsWidth={slide.productsWidth}
                                        productsItemHeight={slide.productsItemHeight}
                                        onLayoutChange={(r, t, w, h) => setSlide((prev) => ({ ...prev, productsRight: r, productsTop: t, productsWidth: w, productsItemHeight: h }))}
                                    />
                                )}
                            </div>
                        </div>{/* end overflowY scroll */}
                    </div>{/* end body */}

                    {/* Footer */}
                    <div style={{
                        padding: '10px 20px', borderTop: '1px solid var(--line-1)',
                        display: 'flex', alignItems: 'center', gap: 10, flexShrink: 0,
                    }}>
                        <div style={{ flex: 1, display: 'flex', alignItems: 'center', gap: 8 }}>
                            <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>Видимость:</span>
                            <button onClick={() => set('enabled', !slide.enabled)} style={{
                                height: 22, padding: '0 10px', border: '1px solid var(--line-1)',
                                borderRadius: 'var(--r-full)', fontSize: 11, fontWeight: 600,
                                cursor: 'pointer', fontFamily: 'var(--font-body)',
                                background: slide.enabled ? 'var(--brand-green-soft)' : 'var(--surface-2)',
                                color: slide.enabled ? 'var(--brand-green)' : 'var(--ink-3)',
                            }}>
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
