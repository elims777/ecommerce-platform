export type SlideType = 'gradient' | 'image' | 'products';
export type GradientPreset = 'navy' | 'red' | 'green' | 'custom';
export type ImageFit = 'cover' | 'contain' | 'fill';

export interface SlideProduct {
    id: number;
    name: string;
    sku: string | null;
    price: number;
    imageUrl: string | null;
}

/** Позиция текстового блока в % от размеров превью */
export interface TextPosition {
    x: number;
    y: number;
}

export interface Slide {
    id: string;
    type: SlideType;
    enabled: boolean;
    displayOrder: number;

    // Фон
    gradientPreset: GradientPreset;
    customGradient: string;
    imageUrl: string;
    imageFit: ImageFit;

    // Текст
    eyebrow: string;
    title: string;
    text: string;
    cta1Label: string;
    cta1Link: string;
    cta2Label: string;
    cta2Link: string;
    textPosition: TextPosition;

    // Товары (упорядочены по индексу массива)
    products: SlideProduct[];
}

export interface SliderConfig {
    slides: Slide[];
}

export const GRADIENT_PRESETS: Record<GradientPreset, string> = {
    navy:   'var(--gradient-hero-navy)',
    red:    'var(--gradient-hero-red)',
    green:  'var(--gradient-hero-green)',
    custom: '',
};

export const GRADIENT_LABELS: Record<GradientPreset, string> = {
    navy:   'Тёмно-синий',
    red:    'Красный',
    green:  'Зелёный',
    custom: 'Свой CSS',
};

export const IMAGE_FIT_LABELS: Record<ImageFit, string> = {
    cover:   'Обрезать (cover)',
    contain: 'Вписать (contain)',
    fill:    'Растянуть (fill)',
};

export const makeSlide = (overrides: Partial<Slide> = {}): Slide => ({
    id: crypto.randomUUID(),
    type: 'gradient',
    enabled: true,
    displayOrder: 0,
    gradientPreset: 'navy',
    customGradient: 'linear-gradient(135deg, #1E3A5F 0%, #0d2240 100%)',
    imageUrl: '',
    imageFit: 'cover',
    eyebrow: '',
    title: '',
    text: '',
    cta1Label: 'Открыть каталог',
    cta1Link: '/catalog',
    cta2Label: '',
    cta2Link: '',
    textPosition: { x: 5, y: 20 },
    products: [],
    ...overrides,
});
