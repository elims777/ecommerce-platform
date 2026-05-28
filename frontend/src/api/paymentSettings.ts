import api from './client';

export interface PaymentMethodSettings {
    sbpEnabled: boolean;
    cardEnabled: boolean;
}

export const getPaymentSettings = async (): Promise<PaymentMethodSettings> => {
    const { data } = await api.get<PaymentMethodSettings>('/v1/payment-settings');
    return data;
};

export const updatePaymentSettings = async (
    settings: PaymentMethodSettings
): Promise<PaymentMethodSettings> => {
    const { data } = await api.put<PaymentMethodSettings>('/v1/admin/payment-settings', settings);
    return data;
};
