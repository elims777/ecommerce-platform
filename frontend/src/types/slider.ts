export type SlideType = 'gradient' | 'image' | 'products';

export type GradientPreset =
    | 'navy'
    | 'red'
    | 'green'
    | 'custom';

export interface SlideProduct {
    id: number;
    name: string;
    sku: string | null;
    price: number;
    imageUrl: string | null;
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

    // Текст
    eyebrow: string;
    title: string;
    text: string;
    cta1Label: string;
    cta1Link: string;
    cta2Label: string;
    cta2Link: string;

    // Товары
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

export const makeSlide = (overrides: Partial<Slide> = {}): Slide => ({
    id: crypto.randomUUID(),
    type: 'gradient',
    enabled: true,
    displayOrder: 0,
    gradientPreset: 'navy',
    customGradient: 'linear-gradient(135deg, #1E3A5F 0%, #0d2240 100%)',
    imageUrl: '',
    eyebrow: '',
    title: '',
    text: '',
    cta1Label: 'Открыть каталог',
    cta1Link: '/catalog',
    cta2Label: '',
    cta2Link: '',
    products: [],
    ...overrides,
});
