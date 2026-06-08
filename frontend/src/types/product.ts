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

/** Вариант товара (размер, рост, цвет и т.д.) */
export interface ProductVariant {
    id: number;
    sku: string | null;
    price: number | null;
    wholesalePrice: number | null;
    stockQuantity: number;
    attributes: Record<string, string> | null;
    isActive: boolean;
    externalId: string | null;
}

/** Товар — полный ответ от product-service */
export interface Product {
    id: number;
    name: string;
    slug: string;
    description: string | null;
    shortDescription: string | null;
    price: number;
    wholesalePrice: number | null;
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

    source: string | null;

    // Вложенные данные
    variants: ProductVariant[];
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
    createdAt: string;
    updatedAt: string;
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