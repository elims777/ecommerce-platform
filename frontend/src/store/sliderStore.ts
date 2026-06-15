import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { Slide, SliderConfig } from '@/types/slider';
import { makeSlide, makeTextBlock, makeCtaButton, migrateSlide } from '@/types/slider';

const DEFAULT_CONFIG: SliderConfig = {
    slides: [
        makeSlide({
            id: 'default-1',
            gradientPreset: 'navy',
            textBlocks: [
                makeTextBlock({
                    x: 5, y: 12, width: 48, height: 60,
                    heading: 'Комплексное снабжение предприятий — в одном кабинете',
                    headingStyle: { bold: true, italic: false, underline: false, strikethrough: false, size: 42, color: '#ffffff' },
                    body: '12 000+ позиций со счёт-фактурой, ЭДО, отсрочкой по договору и закреплённым менеджером.',
                    bodyStyle: { bold: false, italic: false, underline: false, strikethrough: false, size: 15, color: 'rgba(255,255,255,0.8)' },
                    background: 'rgba(0,0,0,0)',
                    borderRadius: 0,
                    link: '',
                }),
            ],
            cta: [
                makeCtaButton({ label: 'Открыть каталог', link: '/catalog', variant: 'primary' }),
                makeCtaButton({ label: 'Запрос на снабжение', link: '/contacts', variant: 'secondary' }),
            ],
            displayOrder: 0,
        }),
        makeSlide({
            id: 'default-2',
            gradientPreset: 'red',
            textBlocks: [
                makeTextBlock({
                    x: 5, y: 12, width: 48, height: 60,
                    heading: 'Оснащение объекта по 123-ФЗ под ключ — за 5 рабочих дней',
                    headingStyle: { bold: true, italic: false, underline: false, strikethrough: false, size: 42, color: '#ffffff' },
                    body: 'Огнетушители, шкафы, извещатели, знаки. Полный пакет сертификатов и паспортов в каждой накладной.',
                    bodyStyle: { bold: false, italic: false, underline: false, strikethrough: false, size: 15, color: 'rgba(255,255,255,0.8)' },
                    background: 'rgba(0,0,0,0)',
                    borderRadius: 0,
                    link: '',
                }),
            ],
            cta: [
                makeCtaButton({ label: 'Подобрать комплект', link: '/catalog', variant: 'primary' }),
                makeCtaButton({ label: 'Скачать спецификацию', link: '/contacts', variant: 'secondary' }),
            ],
            displayOrder: 1,
        }),
        makeSlide({
            id: 'default-3',
            gradientPreset: 'green',
            textBlocks: [
                makeTextBlock({
                    x: 5, y: 12, width: 48, height: 60,
                    heading: 'Экипировка персонала по типовым нормам выдачи',
                    headingStyle: { bold: true, italic: false, underline: false, strikethrough: false, size: 42, color: '#ffffff' },
                    body: 'Поможем подобрать ассортимент по профессиям и роли, отгрузим со склада в Москве и Казани.',
                    bodyStyle: { bold: false, italic: false, underline: false, strikethrough: false, size: 15, color: 'rgba(255,255,255,0.8)' },
                    background: 'rgba(0,0,0,0)',
                    borderRadius: 0,
                    link: '',
                }),
            ],
            cta: [
                makeCtaButton({ label: 'Подобрать по нормам', link: '/catalog', variant: 'primary' }),
                makeCtaButton({ label: 'Прислать образцы', link: '/contacts', variant: 'secondary' }),
            ],
            displayOrder: 2,
        }),
    ],
};

interface SliderState extends SliderConfig {
    upsertSlide: (slide: Slide) => void;
    deleteSlide: (id: string) => void;
    reorderSlides: (ids: string[]) => void;
    toggleSlide: (id: string) => void;
}

export const useSliderStore = create<SliderState>()(
    persist(
        (set) => ({
            ...DEFAULT_CONFIG,

            upsertSlide: (slide) =>
                set((state) => {
                    const exists = state.slides.find((s) => s.id === slide.id);
                    if (exists) {
                        return { slides: state.slides.map((s) => (s.id === slide.id ? slide : s)) };
                    }
                    return {
                        slides: [
                            ...state.slides,
                            { ...slide, displayOrder: state.slides.length },
                        ],
                    };
                }),

            deleteSlide: (id) =>
                set((state) => ({
                    slides: state.slides
                        .filter((s) => s.id !== id)
                        .map((s, i) => ({ ...s, displayOrder: i })),
                })),

            reorderSlides: (ids) =>
                set((state) => {
                    const map = new Map(state.slides.map((s) => [s.id, s]));
                    return {
                        slides: ids
                            .map((id, i) => {
                                const s = map.get(id);
                                return s ? { ...s, displayOrder: i } : null;
                            })
                            .filter(Boolean) as Slide[],
                    };
                }),

            toggleSlide: (id) =>
                set((state) => ({
                    slides: state.slides.map((s) =>
                        s.id === id ? { ...s, enabled: !s.enabled } : s,
                    ),
                })),
        }),
        {
            name: 'rfsnab-slider',
            // Миграция старых слайдов из localStorage при загрузке
            merge: (persisted, current) => {
                const p = persisted as Partial<SliderConfig>;
                if (!p?.slides) return current;
                return {
                    ...current,
                    slides: p.slides.map((s) => migrateSlide(s as unknown as Record<string, unknown>)),
                };
            },
        },
    ),
);
