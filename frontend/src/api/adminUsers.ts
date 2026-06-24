import apiClient from '@/api/client';

export interface AdminUserDto {
  id: number;
  email: string;
  firstname: string;
  lastname: string;
  surname: string | null;
  phone: string | null;
  active: boolean;
  emailVerified: boolean;
  roles: { id: number; name: string }[];
  createdAt: string;
  updatedAt: string;
}

export interface UpdateUserAdminRequest {
  firstname?: string;
  lastname?: string;
  phone?: string;
}

export interface LegalEntityDto {
  id: number;
  inn: string;
  ogrn: string | null;
  fullName: string;
  director: string | null;
  directorTitle: string | null;
  basisOfAuthority: string | null;
  office: string | null;
  phone: string | null;
  email: string | null;
  legalCity: string | null;
  legalStreet: string | null;
  legalBuilding: string | null;
  legalPostalCode: string | null;
  verificationStatus: string;
  verifiedAt: string | null;
  bankAccounts: { id: number; bankName: string; bik: string; correspondentAccount: string; settlementAccount: string; primary: boolean }[];
  addresses: { id: number; city: string; street: string; building: string; apartment: string | null; postalCode: string; primary: boolean }[];
  createdAt: string;
}

export const getAllUsers = async (): Promise<AdminUserDto[]> => {
  const { data } = await apiClient.get<AdminUserDto[]>('/v1/users/all');
  return data;
};

export const getAllLegalEntities = async (status?: string): Promise<LegalEntityDto[]> => {
  const { data } = await apiClient.get<LegalEntityDto[]>('/v1/admin/legal-entities', {
    params: status ? { status } : {},
  });
  return data;
};

export const getUserById = async (id: number): Promise<AdminUserDto> => {
  const { data } = await apiClient.get<AdminUserDto>(`/v1/users/${id}`);
  return data;
};

export const changeUserRole = async (id: number, role: string): Promise<AdminUserDto> => {
  const { data } = await apiClient.patch<AdminUserDto>(`/v1/users/${id}/role`, { role });
  return data;
};

export const changeUserStatus = async (id: number, active: boolean): Promise<AdminUserDto> => {
  const { data } = await apiClient.patch<AdminUserDto>(`/v1/users/${id}/status`, { active });
  return data;
};

export const updateUserAdmin = async (id: number, body: UpdateUserAdminRequest): Promise<AdminUserDto> => {
  const { data } = await apiClient.patch<AdminUserDto>(`/v1/users/${id}/admin`, body);
  return data;
};

export const getLegalEntityById = async (id: number): Promise<LegalEntityDto> => {
  const { data } = await apiClient.get<LegalEntityDto>(`/v1/admin/legal-entities/${id}`);
  return data;
};

// GET /api/v1/admin/legal-entities/users/{userId} → user-service LegalEntityAdminController
export const getUserLegalEntities = async (userId: number): Promise<LegalEntityDto[]> => {
  const { data } = await apiClient.get<LegalEntityDto[]>(`/v1/admin/legal-entities/users/${userId}`);
  return data;
};

// POST /api/v1/admin/legal-entities/{id}/verify?managerEmail=...
export const verifyLegalEntity = async (legalEntityId: number, managerEmail: string): Promise<void> => {
  await apiClient.post(`/v1/admin/legal-entities/${legalEntityId}/verify`, null, {
    params: { managerEmail },
  });
};

// POST /api/v1/admin/legal-entities/{id}/reject?managerEmail=...
export const rejectLegalEntity = async (legalEntityId: number, managerEmail: string, reason: string): Promise<void> => {
  await apiClient.post(`/v1/admin/legal-entities/${legalEntityId}/reject`, { reason }, {
    params: { managerEmail },
  });
};

// DELETE /api/v1/admin/legal-entities/{id}/users/{userId}
export const detachLegalEntity = async (legalEntityId: number, userId: number): Promise<void> => {
  await apiClient.delete(`/v1/admin/legal-entities/${legalEntityId}/users/${userId}`);
};

// DELETE /api/v1/admin/users/{id}
export const deleteUser = async (id: number): Promise<void> => {
  await apiClient.delete(`/v1/admin/users/${id}`);
};

// DELETE /api/v1/admin/legal-entities/{id}
export const deleteLegalEntity = async (id: number): Promise<void> => {
  await apiClient.delete(`/v1/admin/legal-entities/${id}`);
};
