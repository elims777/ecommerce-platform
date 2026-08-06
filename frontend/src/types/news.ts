/** Новость для публичной части сайта */
export interface News {
    id: number;
    title: string;
    slug: string;
    coverImageUrl: string | null;
    /** Исходная ссылка — показываем, если площадка неизвестна и эмбед невозможен */
    videoUrl: string | null;
    /** URL плеера для iframe; null — встраивать нечего */
    videoEmbedUrl: string | null;
    contentHtml: string;
    publishedAt: string | null;
}

/** Новость в админке: с черновиками и служебными датами */
export interface NewsAdmin {
    id: number;
    title: string;
    slug: string;
    coverImageUrl: string | null;
    videoUrl: string | null;
    contentHtml: string;
    isPublished: boolean;
    publishedAt: string | null;
    createdAt: string;
    updatedAt: string;
}

/** Создание и редактирование новости */
export interface NewsRequest {
    title: string;
    coverImageUrl?: string | null;
    videoUrl?: string | null;
    contentHtml: string;
    isPublished: boolean;
}
