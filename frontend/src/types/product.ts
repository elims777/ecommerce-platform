/** Изображение товара */
export interface ProductImage {
    id: number;
    fileUrl: string;
    fileSize: number;
    contentType: string;
    width: number;
    height: number;
    isPrimary: boolean;
    displayOrder: number;
    altText: string | null;
}

/** Видео товара */
export interface ProductVideo {
    id: number;
    videoUrl: string;
    title: string | null;
}

/** Характеристика товара */
export interface ProductAttribute {
    id: number;
    attributeName: string;
    attributeValue: string;
}

/** Дочерний товар-вариант (размер, рост, цвет и т.д.) */
export interface ProductChild {
    id: number;
    name: string;
    sku: string | null;
    price: number;
    wholesalePrice: number | null;
    stockQuantity: number;
    attributes: ProductAttribute[];
    isActive: boolean;
    externalId: string | null;
    barcode: string | null;
    countryOfOrigin: string | null;
}

/** Товар — полный ответ от product-service */
export interface Product {
    id: number;
    name: string;
    slug: string;
    description: string | null;
    /** Описание из импорта (ФТК "Подробнее о товаре"), не зависит от ручного description */
    importDescription: string | null;
    shortDescription: string | null;
    /** Акционная цена, если товар в акции; иначе базовая */
    price: number;
    wholesalePrice: number | null;
    /** Базовая цена до акции — есть только когда акция изменила цену (для зачёркивания) */
    oldPrice: number | null;
    oldWholesalePrice: number | null;
    /** Товар участвует в акции: своя метка ИЛИ метка категории (для витрины) */
    isSale: boolean;
    /** Действующий процент акции: положительный — наценка, отрицательный — скидка */
    saleMarkupPercent: number | null;
    /** Собственная метка товара без учёта категории — только для формы админки */
    ownSale: boolean;
    ownSaleMarkupPercent: number | null;
    stockQuantity: number;
    categoryId: number | null;
    categoryName: string | null;
    isActive: boolean;
    isFeatured: boolean;

    // Интеграция с 1С
    externalId: string | null;
    sku: string | null;
    externalCode: string | null;
    unitOfMeasure: string | null;
    vatRate: number | null;

    material: string | null;
    source: string | null;
    isVariantChild: boolean;
    parentProductId: number | null;
    hasVariants?: boolean;
    displayOrder: number;

    // Вложенные данные
    children: ProductChild[];
    images: ProductImage[];
    videos: ProductVideo[];
    attributes: ProductAttribute[];

    createdAt: string;
    updatedAt: string;
}

/** Узел дерева категорий — рекурсивная структура */
export interface CategoryTree {
    id: number;
    name: string;
    slug: string;
    description: string | null;
    parentId: number | null;
    isActive: boolean;
    displayOrder: number;
    /** Акция включена именно на этой категории */
    isSale: boolean;
    /** Действующий процент: собственный или унаследованный от предка */
    saleMarkupPercent: number | null;
    /** Акция действует, но включена у предка */
    inheritedSale: boolean;
    children: CategoryTree[];
}

/** Категория — плоский ответ */
export interface Category {
    id: number;
    name: string;
    slug: string;
    description: string | null;
    parentId: number | null;
    parentName: string | null;
    isActive: boolean;
    displayOrder: number;
    externalId: string | null;
    /** Акционная категория: процент применяется ко всем товарам её поддерева */
    isSale: boolean;
    saleMarkupPercent: number | null;
    createdAt: string;
    updatedAt: string;
}

/** Фасет каталога: свойство и его различные значения (без счётчиков) */
export interface Facet {
    name: string;
    values: string[];
}

/**
 * Spring Data Page — типизированная обёртка для пагинации.
 * Маппится на org.springframework.data.domain.Page из бэкенда.
 */
export interface Page<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
    first: boolean;
    last: boolean;
    empty: boolean;
}