import { useEffect, useMemo, useState } from 'react';
import { AutoComplete, Alert, Form, Input, Radio, Spin, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '@/store/authStore';
import { getCities, getTerminals } from '@/api/delivery';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import PointsPicker from './PointsPicker';
import type { CarrierCode, CarrierTerminalDto, PointType } from '@/types/delivery';

const { Text } = Typography;

const CITY_DEBOUNCE_MS = 300;
const CITY_MIN_LENGTH = 2;

interface TerminalPickerProps {
    /** Уже выбранный терминал заказа (редактирование) — показывается, пока список города не загружен */
    current?: { id: number; name: string; address: string };
}

/**
 * Выбор терминала ТК: город (автодополнение из справочника), для СДЭК — переключатель ПВЗ/постамат,
 * карта + список точек, карточка выбранной точки. Пишет в поле формы carrierTerminalId;
 * ТК берёт из поля carrierCode (его выставляет CarrierPicker). Также содержит обязательные поля
 * получателя pickupRecipientName / pickupRecipientPhone (по умолчанию из профиля).
 */
const TerminalPicker = ({ current }: TerminalPickerProps) => {
    const form = Form.useFormInstance();
    const carrier = Form.useWatch('carrierCode', form) as CarrierCode | undefined;
    const terminalId = Form.useWatch('carrierTerminalId', form) as number | undefined;

    const [cdekType, setCdekType] = useState<PointType>('PVZ');
    const [cityInput, setCityInput] = useState('');
    const [city, setCity] = useState('');
    // Смена ТК сбрасывает город (терминал сбрасывает CarrierPicker); паттерн «подстройка состояния при рендере»
    const [prevCarrier, setPrevCarrier] = useState(carrier);
    if (carrier !== prevCarrier) {
        setPrevCarrier(carrier);
        setCity('');
        setCityInput('');
    }
    const pointType: PointType = carrier === 'CDEK' ? cdekType : 'TERMINAL';
    const debouncedInput = useDebouncedValue(cityInput.trim(), CITY_DEBOUNCE_MS);

    const { data: cities = [] } = useQuery({
        queryKey: ['deliveryCities', carrier, pointType, debouncedInput],
        queryFn: () => getCities(carrier!, pointType, debouncedInput),
        enabled: !!carrier && debouncedInput.length >= CITY_MIN_LENGTH,
        staleTime: 5 * 60 * 1000,
    });

    const { data: terminals = [], isLoading } = useQuery({
        queryKey: ['deliveryTerminals', carrier, city, pointType],
        queryFn: () => getTerminals(carrier!, city, pointType),
        enabled: !!carrier && !!city,
        staleTime: 5 * 60 * 1000,
    });

    const points = useMemo(
        () => terminals.map((t) => ({ id: t.id, title: t.name, address: t.address, latitude: t.latitude, longitude: t.longitude })),
        [terminals],
    );

    // Получатель на терминале: подставляем из профиля только в пустые поля (в редактировании там уже значения заказа)
    const user = useAuthStore((s) => s.user);
    useEffect(() => {
        if (!user) return;
        if (!form.getFieldValue('pickupRecipientName')) {
            form.setFieldValue('pickupRecipientName', [user.firstname, user.lastname].filter(Boolean).join(' '));
        }
        if (!form.getFieldValue('pickupRecipientPhone') && user.phone) {
            form.setFieldValue('pickupRecipientPhone', user.phone);
        }
    }, [user, form]);

    const resetTerminal = () => form.setFieldValue('carrierTerminalId', undefined);

    const commitCity = (value: string) => {
        setCity(value);
        resetTerminal();
    };

    const selected: CarrierTerminalDto | undefined = terminals.find((t) => t.id === terminalId);

    if (!carrier) return null;

    return (
        <>
            {carrier === 'CDEK' && (
                <Radio.Group
                    value={cdekType}
                    onChange={(e) => { setCdekType(e.target.value); setCity(''); setCityInput(''); resetTerminal(); }}
                    optionType="button"
                    style={{ marginBottom: 12 }}
                    options={[{ value: 'PVZ', label: 'Пункт выдачи' }, { value: 'POSTAMAT', label: 'Постамат' }]}
                />
            )}
            <Form.Item label="Город" required>
                <AutoComplete
                    value={cityInput}
                    placeholder="Начните вводить город"
                    options={cities.map((c) => ({ value: c }))}
                    onChange={(v) => setCityInput(v)}
                    onSelect={(v) => commitCity(v)}
                    onBlur={() => {
                        const match = cities.find((c) => c.toLowerCase() === cityInput.trim().toLowerCase());
                        if (match) {
                            if (match !== city) { setCityInput(match); commitCity(match); }
                        } else if (city && cityInput.trim().toLowerCase() !== city.toLowerCase()) {
                            commitCity('');
                        }
                    }}
                />
            </Form.Item>

            {isLoading && <div style={{ textAlign: 'center', padding: 16 }}><Spin /></div>}
            {city && !isLoading && terminals.length === 0 && (
                <Alert type="info" showIcon style={{ marginBottom: 8 }} message="В этом городе нет доступных точек выбранной ТК" />
            )}

            <Form.Item name="carrierTerminalId" rules={[{ required: true, message: 'Выберите терминал' }]}>
                <PointsPicker points={points} emptyHint={city ? undefined : 'Сначала выберите город'} />
            </Form.Item>

            {(selected || (current && current.id === terminalId)) && (
                <div style={{ padding: 12, border: '1px solid var(--line-2)', borderRadius: 6, marginBottom: 16 }}>
                    <Text strong>{selected?.name ?? current?.name}</Text>
                    <br />
                    <Text>{selected?.address ?? current?.address}</Text>
                    {selected?.workingHours && <><br /><Text type="secondary">Часы работы: {selected.workingHours}</Text></>}
                    {selected?.phone && <><br /><Text type="secondary">Телефон: {selected.phone}</Text></>}
                    {selected?.pointType === 'POSTAMAT' && (
                        <><br /><Text type="warning">Постамат: есть ограничения по габаритам и весу посылки</Text></>
                    )}
                </div>
            )}

            <Form.Item name="pickupRecipientName" label="ФИО получателя" rules={[{ required: true, message: 'Укажите получателя' }]}>
                <Input placeholder="Иванов Иван Иванович" />
            </Form.Item>
            <Form.Item
                name="pickupRecipientPhone"
                label="Телефон получателя"
                rules={[
                    { required: true, message: 'Укажите телефон' },
                    { pattern: /^\+?[0-9]{11}$/, message: 'Введите 11 цифр' },
                ]}
            >
                <Input placeholder="+79001234567" />
            </Form.Item>
        </>
    );
};

export default TerminalPicker;
