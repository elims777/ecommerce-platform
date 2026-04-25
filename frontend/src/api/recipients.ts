import apiClient from './client';

export interface RecipientDto {
    id: number;
    name: string;
    phone: string;
    isDefault: boolean;
    createdAt: string;
}

export interface RecipientAddressDto {
    id: number;
    recipientId: number;
    label: string;
    city: string;
    street: string;
    building: string;
    apartment?: string;
    postalCode?: string;
    isDefault: boolean;
    createdAt: string;
}

export interface RecipientRequest {
    name: string;
    phone: string;
}

export interface RecipientAddressRequest {
    label: string;
    city: string;
    street: string;
    building: string;
    apartment?: string;
    postalCode?: string;
}

export const getRecipients = async (): Promise<RecipientDto[]> => {
    const { data } = await apiClient.get<RecipientDto[]>('/v1/recipients');
    return data;
};

export const createRecipient = async (request: RecipientRequest): Promise<RecipientDto> => {
    const { data } = await apiClient.post<RecipientDto>('/v1/recipients', request);
    return data;
};

export const getRecipientAddresses = async (recipientId: number): Promise<RecipientAddressDto[]> => {
    const { data } = await apiClient.get<RecipientAddressDto[]>(`/v1/recipients/${recipientId}/addresses`);
    return data;
};

export const createRecipientAddress = async (
    recipientId: number,
    request: RecipientAddressRequest,
): Promise<RecipientAddressDto> => {
    const { data } = await apiClient.post<RecipientAddressDto>(
        `/v1/recipients/${recipientId}/addresses`,
        request,
    );
    return data;
};