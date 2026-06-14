import { useState, useRef } from 'react';
import { App } from 'antd';
import { useSliderStore } from '@/store/sliderStore';
import type { Slide } from '@/types/slider';
import { makeSlide, GRADIENT_PRESETS } from '@/types/slider';
import SlideEditorModal from './SlideEditorModal';

// ── Icons ─────────────────────────────────────────────────────
const PlusIcon = () => (
    <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
        <path d="M12 5v14M5 12h14"/>
    </svg>
);
const EditIcon = () => (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
    </svg>
);
const TrashIcon = () => (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6M14 11v6M9 6V4h6v2"/>
    </svg>
);
const DragIcon = () => (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round">
        <path d="M9 5h.01M9 12h.01M9 19h.01M15 5h.01M15 12h.01M15 19h.01"/>
    </svg>
);
const EyeIcon = ({ open }: { open: boolean }) => open ? (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>
    </svg>
) : (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
        <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
        <line x1="1" y1="1" x2="23" y2="23"/>
    </svg>
);

// ── Slide card ────────────────────────────────────────────────
const getBg = (slide: Slide): string => {
    if (slide.type === 'image' && slide.imageUrl) return `url(${slide.imageUrl}) center/cover`;
    return slide.gradientPreset === 'custom' ? slide.customGradient : GRADIENT_PRESETS[slide.gradientPreset];
};

interface SlideCardProps {
    slide: Slide;
    index: number;
    onEdit: () => void;
    onDelete: () => void;
    onToggle: () => void;
    isDragging: boolean;
    onDragStart: (e: React.DragEvent) => void;
    onDragOver: (e: React.DragEvent) => void;
    onDrop: (e: React.DragEvent) => void;
    onDragEnd: () => void;
}

const SlideCard = ({ slide, index, onEdit, onDelete, onToggle, isDragging, onDragStart, onDragOver, onDrop, onDragEnd }: SlideCardProps) => {
    return (
        <div
            draggable
            onDragStart={onDragStart}
            onDragOver={onDragOver}
            onDrop={onDrop}
            onDragEnd={onDragEnd}
            style={{
                display: 'flex', gap: 14, alignItems: 'flex-start',
                padding: '12px 14px',
                background: isDragging ? 'var(--red-tint)' : 'var(--surface)',
                border: `1.5px solid ${isDragging ? 'var(--brand-red)' : 'var(--line-1)'}`,
                borderRadius: 'var(--r-4)',
                opacity: slide.enabled ? 1 : 0.55,
                transition: 'border-color .12s, background .12s, opacity .12s',
                cursor: 'grab',
            }}
        >
            {/* Drag handle + number */}
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4, paddingTop: 2, color: 'var(--ink-4)' }}>
                <DragIcon />
                <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--ink-4)', fontVariantNumeric: 'tabular-nums' }}>
                    {String(index + 1).padStart(2, '0')}
                </span>
            </div>

            {/* Preview thumbnail */}
            <div style={{
                width: 130, height: 72, borderRadius: 'var(--r-3)',
                background: getBg(slide), flexShrink: 0, overflow: 'hidden',
                position: 'relative',
            }}>
                <div style={{
                    position: 'absolute', inset: 0, padding: '6px 8px',
                    display: 'flex', flexDirection: 'column', justifyContent: 'flex-end',
                }}>
                    {slide.textBlocks[0]?.heading && (
                        <div style={{ fontSize: 9, fontWeight: 700, color: '#fff', lineHeight: 1.3,
                            overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {slide.textBlocks[0].heading}
                        </div>
                    )}
                </div>
            </div>

            {/* Info */}
            {(() => {
                const firstBlock = slide.textBlocks[0];
                const title = firstBlock?.heading || '';
                const sub = firstBlock?.body || '';
                return (
            <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-1)', marginBottom: 3,
                    overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {title || <span style={{ color: 'var(--ink-4)', fontStyle: 'italic' }}>Без заголовка</span>}
                </div>
                {sub && (
                    <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 4,
                        overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {sub.slice(0, 60)}{sub.length > 60 ? '…' : ''}
                    </div>
                )}
                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                    <span style={{
                        height: 18, padding: '0 7px', borderRadius: 'var(--r-full)',
                        fontSize: 10, fontWeight: 600, display: 'inline-flex', alignItems: 'center',
                        background: 'var(--surface-2)', color: 'var(--ink-3)',
                    }}>
                        {slide.type === 'gradient' ? 'Градиент' : slide.type === 'image' ? 'Изображение' : 'Товары'}
                    </span>
                    {slide.products.length > 0 && (
                        <span style={{
                            height: 18, padding: '0 7px', borderRadius: 'var(--r-full)',
                            fontSize: 10, fontWeight: 600, display: 'inline-flex', alignItems: 'center',
                            background: 'var(--navy-tint)', color: 'var(--brand-navy)',
                        }}>
                            {slide.products.length} товаров
                        </span>
                    )}
                    {slide.cta1Label && (
                        <span style={{
                            height: 18, padding: '0 7px', borderRadius: 'var(--r-full)',
                            fontSize: 10, fontWeight: 500, display: 'inline-flex', alignItems: 'center',
                            background: 'var(--surface-2)', color: 'var(--ink-3)',
                        }}>
                            {slide.cta1Label}
                        </span>
                    )}
                </div>
            </div>
                );
            })()}

            {/* Actions */}
            <div style={{ display: 'flex', gap: 4, flexShrink: 0 }}>
                <button
                    onClick={onToggle}
                    title={slide.enabled ? 'Скрыть слайд' : 'Показать слайд'}
                    style={{
                        width: 30, height: 30, border: '1px solid var(--line-1)',
                        borderRadius: 'var(--r-2)', background: 'var(--surface)',
                        cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center',
                        color: slide.enabled ? 'var(--brand-green)' : 'var(--ink-4)',
                    }}
                >
                    <EyeIcon open={slide.enabled} />
                </button>
                <button
                    onClick={onEdit}
                    title="Редактировать"
                    style={{
                        width: 30, height: 30, border: '1px solid var(--line-1)',
                        borderRadius: 'var(--r-2)', background: 'var(--surface)',
                        cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center',
                        color: 'var(--ink-2)',
                    }}
                >
                    <EditIcon />
                </button>
                <button
                    onClick={onDelete}
                    title="Удалить"
                    style={{
                        width: 30, height: 30, border: '1px solid var(--line-1)',
                        borderRadius: 'var(--r-2)', background: 'var(--surface)',
                        cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center',
                        color: 'var(--brand-red)',
                    }}
                >
                    <TrashIcon />
                </button>
            </div>
        </div>
    );
};

// ── Main ──────────────────────────────────────────────────────
const AdminSliderPage = () => {
    const { message: messageApi, modal } = App.useApp();
    const { slides, upsertSlide, deleteSlide, reorderSlides, toggleSlide } = useSliderStore();

    const [editorOpen, setEditorOpen] = useState(false);
    const [editingSlide, setEditingSlide] = useState<Slide | null>(null);

    const dragIdRef = useRef<string | null>(null);
    const [dragOverId, setDragOverId] = useState<string | null>(null);

    const sorted = [...slides].sort((a, b) => a.displayOrder - b.displayOrder);
    const enabledCount = slides.filter((s) => s.enabled).length;

    const openNew = () => {
        setEditingSlide(null);
        setEditorOpen(true);
    };
    const openEdit = (slide: Slide) => {
        setEditingSlide(slide);
        setEditorOpen(true);
    };

    const handleSave = (slide: Slide) => {
        upsertSlide(slide);
        setEditorOpen(false);
        messageApi.success(editingSlide ? 'Слайд обновлён' : 'Слайд добавлен');
    };

    const handleDelete = (slide: Slide) => {
        const heading = slide.textBlocks[0]?.heading || '';
        modal.confirm({
            title: 'Удалить слайд?',
            content: heading ? `«${heading}»` : 'Этот слайд будет удалён.',
            okText: 'Удалить',
            okButtonProps: { danger: true },
            cancelText: 'Отмена',
            onOk: () => {
                deleteSlide(slide.id);
                messageApi.success('Слайд удалён');
            },
        });
    };

    // ── Drag-and-drop ─────────────────────────────────────────
    const handleDragStart = (id: string) => (e: React.DragEvent) => {
        dragIdRef.current = id;
        e.dataTransfer.effectAllowed = 'move';
    };
    const handleDragOver = (id: string) => (e: React.DragEvent) => {
        e.preventDefault();
        setDragOverId(id);
    };
    const handleDrop = (targetId: string) => (e: React.DragEvent) => {
        e.preventDefault();
        const fromId = dragIdRef.current;
        if (!fromId || fromId === targetId) { setDragOverId(null); return; }
        const ids = sorted.map((s) => s.id);
        const fromIdx = ids.indexOf(fromId);
        const toIdx = ids.indexOf(targetId);
        const reordered = [...ids];
        reordered.splice(fromIdx, 1);
        reordered.splice(toIdx, 0, fromId);
        reorderSlides(reordered);
        setDragOverId(null);
    };
    const handleDragEnd = () => {
        dragIdRef.current = null;
        setDragOverId(null);
    };

    return (
        <div style={{ maxWidth: 860 }}>
            {/* Header bar */}
            <div style={{
                display: 'flex', alignItems: 'center', gap: 14, marginBottom: 20,
                padding: '14px 18px', background: 'var(--surface)', border: '1px solid var(--line-1)',
                borderRadius: 'var(--r-4)',
            }}>
                <div style={{ flex: 1 }}>
                    <div style={{ fontFamily: 'var(--font-head)', fontWeight: 700, fontSize: 16, color: 'var(--ink-1)' }}>
                        Слайды главной страницы
                    </div>
                    <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 2 }}>
                        {slides.length} слайдов · {enabledCount} показываются · перетащите для изменения порядка
                    </div>
                </div>
                <button
                    onClick={openNew}
                    style={{
                        display: 'inline-flex', alignItems: 'center', gap: 6,
                        height: 36, padding: '0 16px', border: 0,
                        borderRadius: 'var(--r-3)', background: 'var(--brand-red)',
                        color: '#fff', fontWeight: 600, fontSize: 13,
                        cursor: 'pointer', fontFamily: 'var(--font-body)',
                    }}
                >
                    <PlusIcon /> Добавить слайд
                </button>
            </div>

            {/* Empty state */}
            {sorted.length === 0 && (
                <div style={{
                    textAlign: 'center', padding: '60px 0',
                    border: '2px dashed var(--line-2)', borderRadius: 'var(--r-5)',
                    color: 'var(--ink-3)',
                }}>
                    <div style={{ fontSize: 15, fontWeight: 600, marginBottom: 6 }}>Нет слайдов</div>
                    <div style={{ fontSize: 13, marginBottom: 16 }}>Создайте первый слайд для главной страницы</div>
                    <button
                        onClick={openNew}
                        style={{
                            display: 'inline-flex', alignItems: 'center', gap: 6,
                            height: 36, padding: '0 18px', border: 0,
                            borderRadius: 'var(--r-3)', background: 'var(--brand-red)',
                            color: '#fff', fontWeight: 600, fontSize: 13,
                            cursor: 'pointer', fontFamily: 'var(--font-body)',
                        }}
                    >
                        <PlusIcon /> Создать слайд
                    </button>
                </div>
            )}

            {/* Slides list */}
            {sorted.length > 0 && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {sorted.map((slide, i) => (
                        <SlideCard
                            key={slide.id}
                            slide={slide}
                            index={i}
                            onEdit={() => openEdit(slide)}
                            onDelete={() => handleDelete(slide)}
                            onToggle={() => toggleSlide(slide.id)}
                            isDragging={dragOverId === slide.id}
                            onDragStart={handleDragStart(slide.id)}
                            onDragOver={handleDragOver(slide.id)}
                            onDrop={handleDrop(slide.id)}
                            onDragEnd={handleDragEnd}
                        />
                    ))}
                </div>
            )}

            {/* Hint */}
            {sorted.length > 1 && (
                <div style={{ marginTop: 12, fontSize: 12, color: 'var(--ink-4)', display: 'flex', alignItems: 'center', gap: 5 }}>
                    <DragIcon />
                    Перетащите карточку, чтобы изменить порядок слайдов
                </div>
            )}

            <SlideEditorModal
                open={editorOpen}
                initial={editingSlide}
                onSave={handleSave}
                onClose={() => setEditorOpen(false)}
            />
        </div>
    );
};

export default AdminSliderPage;
