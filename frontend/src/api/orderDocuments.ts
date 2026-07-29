import apiClient from '@/api/client';

/** Документ, прикреплённый к заказу (счёт, УПД, КП, сертификат) */
export interface OrderDocument {
    id: string;
    type: string;
    typeName: string;
    fileName: string;
    contentType: string | null;
    sizeBytes: number;
    uploadedAt: string;
}

/** Скачанный файл — блоб + имя из Content-Disposition (либо fallback) */
export interface DownloadedFile {
    blob: Blob;
    fileName: string;
}

const parseFileNameFromContentDisposition = (contentDisposition?: string): string | null => {
    if (!contentDisposition) return null;
    const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
    if (utf8Match) return decodeURIComponent(utf8Match[1]);
    const plainMatch = contentDisposition.match(/filename="?([^";]+)"?/i);
    return plainMatch ? plainMatch[1] : null;
};

export const getAdminOrderDocuments = async (orderId: string): Promise<OrderDocument[]> => {
    const { data } = await apiClient.get<OrderDocument[]>(`/v1/admin/orders/${orderId}/documents`);
    return data;
};

export const uploadOrderDocument = async (orderId: string, type: string, file: File): Promise<OrderDocument> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('type', type);
    const { data } = await apiClient.post<OrderDocument>(`/v1/admin/orders/${orderId}/documents`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
};

export const deleteOrderDocument = async (orderId: string, docId: string): Promise<void> => {
    await apiClient.delete(`/v1/admin/orders/${orderId}/documents/${docId}`);
};

export const downloadAdminOrderDocument = async (orderId: string, doc: OrderDocument): Promise<DownloadedFile> => {
    const response = await apiClient.get<Blob>(`/v1/admin/orders/${orderId}/documents/${doc.id}/download`, {
        responseType: 'blob',
    });
    return {
        blob: response.data,
        fileName: parseFileNameFromContentDisposition(response.headers['content-disposition']) ?? doc.fileName,
    };
};

export const getMyOrderDocuments = async (orderId: string): Promise<OrderDocument[]> => {
    const { data } = await apiClient.get<OrderDocument[]>(`/v1/orders/${orderId}/documents`);
    return data;
};

export const downloadMyOrderDocument = async (orderId: string, doc: OrderDocument): Promise<DownloadedFile> => {
    const response = await apiClient.get<Blob>(`/v1/orders/${orderId}/documents/${doc.id}/download`, {
        responseType: 'blob',
    });
    return {
        blob: response.data,
        fileName: parseFileNameFromContentDisposition(response.headers['content-disposition']) ?? doc.fileName,
    };
};

/** Инициирует скачивание файла в браузере и освобождает временный URL */
export const triggerBrowserDownload = (file: DownloadedFile): void => {
    const url = URL.createObjectURL(file.blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = file.fileName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
};
