import apiClient from './client';

export interface LegalEntityResponse {
    id: number;
    inn: string;
    ogrn: string;
    fullName: string;
    director: string;
    directorTitle: string | null;
    basisOfAuthority: string | null;
    office: string | null;
    phone: string;
    email: string;
    legalCity: string;
    legalStreet: string;
    legalBuilding: string;
    legalPostalCode: string | null;
    verificationStatus: string;
    verifiedAt: string | null;
    createdAt: string;
}

export interface UpdateLegalEntityRequest {
    fullName: string;
    director: string;
    directorTitle?: string;
    basisOfAuthority?: string;
    office?: string;
    phone: string;
    legalCity: string;
    legalStreet: string;
    legalBuilding: string;
    legalPostalCode?: string;
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
        `/v1/legal-entities/link-status/${userId}`,
    );
    return data;
};

export const getLegalEntity = async (id: number): Promise<LegalEntityResponse> => {
    const { data } = await apiClient.get<LegalEntityResponse>(`/v1/legal-entities/${id}`);
    return data;
};

export const registerLegalEntity = async (
    request: RegisterLegalEntityRequest,
): Promise<void> => {
    await apiClient.post('/v1/legal-entities/register', request);
};

export const updateLegalEntity = async (
    id: number,
    request: UpdateLegalEntityRequest,
): Promise<LegalEntityResponse> => {
    const { data } = await apiClient.patch<LegalEntityResponse>(
        `/v1/legal-entities/${id}`,
        request,
        { headers: { 'Content-Type': 'application/json;charset=UTF-8' } },
    );
    return data;
};
