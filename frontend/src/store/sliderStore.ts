import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { Slide, SliderConfig } from '@/types/slider';
import { makeSlide } from '@/types/slider';

const DEFAULT_CONFIG: SliderConfig = {
    slides: [
        makeSlide({
            id: 'default-1',
            gradientPreset: 'navy',
            eyebrow: 'B2B-снабжение',
            title: 'Комплексное снабжение предприятий — в одном кабинете',
            text: '12 000+ позиций со счёт-фактурой, ЭДО, отсрочкой по договору и закреплённым менеджером.',
            cta1Label: 'Открыть каталог',
            cta1Link: '/catalog',
            cta2Label: 'Запрос на снабжение',
            cta2Link: '/contacts',
            displayOrder: 0,
        }),
        makeSlide({
            id: 'default-2',
            gradientPreset: 'red',
            eyebrow: 'Сезон противопожарной безопасности',
            title: 'Оснащение объекта по 123-ФЗ под ключ — за 5 рабочих дней',
            text: 'Огнетушители, шкафы, извещатели, знаки. Полный пакет сертификатов и паспортов в каждой накладной.',
            cta1Label: 'Подобрать комплект',
            cta1Link: '/catalog',
            cta2Label: 'Скачать спецификацию',
            cta2Link: '/contacts',
            displayOrder: 1,
        }),
        makeSlide({
            id: 'default-3',
            gradientPreset: 'green',
            eyebrow: 'Спецодежда и СИЗ',
            title: 'Экипировка персонала по типовым нормам выдачи',
            text: 'Поможем подобрать ассортимент по профессиям и роли, отгрузим со склада в Москве и Казани.',
            cta1Label: 'Подобрать по нормам',
            cta1Link: '/catalog',
            cta2Label: 'Прислать образцы',
            cta2Link: '/contacts',
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
        { name: 'rfsnab-slider' },
    ),
);
