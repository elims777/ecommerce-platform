const generateId = (): string =>
    typeof crypto !== 'undefined' && crypto.randomUUID
        ? generateId()
        : Math.random().toString(36).slice(2) + Date.now().toString(36);

export type SlideType = 'gradient' | 'image' | 'products';
export type GradientPreset = 'navy' | 'red' | 'green' | 'custom';
export type ImageFit = 'cover' | 'contain' | 'fill';

export interface SlideProductFields {
    showName: boolean;
    showPrice: boolean;
    showShortDescription: boolean;
    showDescription: boolean;
    selectedImageIndex: number;
}

export const defaultProductFields = (): SlideProductFields => ({
    showName: true,
    showPrice: true,
    showShortDescription: false,
    showDescription: false,
    selectedImageIndex: 0,
});

export interface SlideProduct {
    id: number;
    name: string;
    sku: string | null;
    price: number;
    shortDescription: string | null;
    description: string | null;
    images: Array<{ fileUrl: string; isPrimary: boolean; displayOrder: number }>;
    imageUrl: string | null;
    fields: SlideProductFields;
}

export interface TextStyle {
    bold: boolean;
    italic: boolean;
    underline: boolean;
    strikethrough: boolean;
    size: number;
    color: string;
}

export const defaultTextStyle = (): TextStyle => ({
    bold: false,
    italic: false,
    underline: false,
    strikethrough: false,
    size: 16,
    color: '#ffffff',
});

export interface CtaButton {
    id: string;
    label: string;
    link: string;
    variant: 'primary' | 'secondary';
}

export const makeCtaButton = (overrides: Partial<CtaButton> = {}): CtaButton => ({
    id: generateId(),
    label: '',
    link: '',
    variant: 'primary',
    ...overrides,
});

/** Текстовый блок, размещаемый на слайде */
export interface TextBlock {
    id: string;
    /** Позиция и размер в % от превью */
    x: number;
    y: number;
    width: number;
    height: number;
    heading: string;
    headingStyle: TextStyle;
    body: string;
    bodyStyle: TextStyle;
    background: string;   // CSS color, e.g. 'rgba(0,0,0,0.4)'
    borderRadius: number; // px
    link: string;
}

export const makeTextBlock = (overrides: Partial<TextBlock> = {}): TextBlock => ({
    id: generateId(),
    x: 5, y: 15, width: 45, height: 50,
    heading: '',
    headingStyle: { bold: true, italic: false, underline: false, strikethrough: false, size: 40, color: '#ffffff' },
    body: '',
    bodyStyle: defaultTextStyle(),
    background: 'rgba(0,0,0,0)',
    borderRadius: 0,
    link: '',
    ...overrides,
});

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

    // Текстовые блоки
    textBlocks: TextBlock[];

    // CTA-кнопки (общие на слайд)
    cta: CtaButton[];

    // Товары (упорядочены по индексу массива)
    products: SlideProduct[];

    // Позиция и размер блока товаров (% от размеров слайда)
    productsRight: number;      // отступ от правого края, %
    productsTop: number;        // отступ сверху, %
    productsWidth: number;      // ширина блока товаров, %
    productsItemHeight: number; // высота одной карточки товара, px
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
    id: generateId(),
    type: 'gradient',
    enabled: true,
    displayOrder: 0,
    gradientPreset: 'navy',
    customGradient: 'linear-gradient(135deg, #1E3A5F 0%, #0d2240 100%)',
    imageUrl: '',
    imageFit: 'cover',
    textBlocks: [],
    cta: [makeCtaButton({ label: 'Открыть каталог', link: '/catalog', variant: 'primary' })],
    products: [],
    productsRight: 4,
    productsTop: 20,
    productsWidth: 22,
    productsItemHeight: 56,
    ...overrides,
});

/** Миграция слайдов из старого формата (eyebrow/title/text) в textBlocks[] */
export const migrateSlide = (raw: Record<string, unknown>): Slide => {
    const base = makeSlide(raw as Partial<Slide>);

    // Миграция cta1/cta2 → cta[]
    if (!Array.isArray(raw.cta)) {
        const buttons: CtaButton[] = [];
        const l1 = raw.cta1Label as string | undefined;
        const r1 = raw.cta1Link  as string | undefined;
        const l2 = raw.cta2Label as string | undefined;
        const r2 = raw.cta2Link  as string | undefined;
        if (l1) buttons.push(makeCtaButton({ label: l1, link: r1 ?? '', variant: 'primary' }));
        if (l2) buttons.push(makeCtaButton({ label: l2, link: r2 ?? '', variant: 'secondary' }));
        base.cta = buttons.length > 0 ? buttons : base.cta;
    } else {
        base.cta = raw.cta as CtaButton[];
    }

    // Если textBlocks уже есть — слайд новый, ничего не делаем
    if (Array.isArray(raw.textBlocks) && (raw.textBlocks as unknown[]).length >= 0) {
        return { ...base, textBlocks: raw.textBlocks as TextBlock[] };
    }

    const blocks: TextBlock[] = [];
    const eyebrow = (raw.eyebrow as string | undefined) ?? '';
    const title   = (raw.title   as string | undefined) ?? '';
    const body    = (raw.text    as string | undefined) ?? '';
    const titleSize   = (raw.titleSize   as number | undefined) ?? 48;
    const eyebrowSize = (raw.eyebrowSize as number | undefined) ?? 14;
    const textSize    = (raw.textSize    as number | undefined) ?? 16;
    const tx = ((raw.textPosition as { x?: number } | undefined)?.x ?? 5);
    const ty = ((raw.textPosition as { y?: number } | undefined)?.y ?? 15);

    if (eyebrow || title || body) {
        blocks.push(makeTextBlock({
            x: tx, y: ty, width: 45, height: 55,
            heading: title,
            headingStyle: { bold: true, italic: false, underline: false, strikethrough: false, size: titleSize, color: '#ffffff' },
            body: [eyebrow, body].filter(Boolean).join('\n'),
            bodyStyle: { bold: false, italic: false, underline: false, strikethrough: false, size: Math.min(eyebrowSize, textSize), color: 'rgba(255,255,255,0.8)' },
            background: 'rgba(0,0,0,0)',
            borderRadius: 0,
            link: '',
        }));
    }

    return { ...base, textBlocks: blocks };
};
