import apiClient from './client';

export interface LegalEntityResponse {
    id: number;
    fullName: string;
    inn: string;
    email: string;
    verificationStatus: string;
}

export interface LinkStatusResponse {
    linked: boolean;
    confirmed: boolean;
    legalEntityId: number | null;
}

export interface RegisterLegalEntityRequest {
    fullName: string;
    inn: string;
    email: string;
    password: string;
}

export const getLinkStatus = async (userId: number): Promise<LinkStatusResponse> => {
    const { data } = await apiClient.get<LinkStatusResponse>(
        `/api/v1/legal-entities/link-status/${userId}`,
    );
    return data;
};

export const getLegalEntity = async (id: number): Promise<LegalEntityResponse> => {
    const { data } = await apiClient.get<LegalEntityResponse>(`/api/v1/legal-entities/${id}`);
    return data;
};

export const registerLegalEntity = async (
    request: RegisterLegalEntityRequest,
): Promise<void> => {
    await apiClient.post('/api/v1/legal-entities/register', request);
};
