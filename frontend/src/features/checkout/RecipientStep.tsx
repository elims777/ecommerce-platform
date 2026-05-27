import { Form, Row, Col, Input, Select, Button, Typography, Divider, Spin, Space } from 'antd';
import { EnvironmentOutlined, PlusOutlined, UserOutlined } from '@ant-design/icons';
import { DeliveryMethod } from '@/types/order';
import type { RecipientDto, RecipientAddressDto } from '@/api/recipients';

const { Text } = Typography;

interface RecipientStepProps {
    deliveryMethod: DeliveryMethod | undefined;
    recipients: RecipientDto[];
    recipientsLoading: boolean;
    addresses: RecipientAddressDto[];
    addressesLoading: boolean;
    selectedRecipient: RecipientDto | null;
    selectedAddress: RecipientAddressDto | null;
    manualInput: boolean;
    onRecipientChange: (id: number) => void;
    onAddressChange: (id: number) => void;
    onSwitchToManual: () => void;
    onSwitchToSaved: () => void;
}

const RecipientStep = ({
    deliveryMethod,
    recipients,
    recipientsLoading,
    addresses,
    addressesLoading,
    selectedRecipient,
    selectedAddress,
    manualInput,
    onRecipientChange,
    onAddressChange,
    onSwitchToManual,
    onSwitchToSaved,
}: RecipientStepProps) => {
    if (deliveryMethod !== DeliveryMethod.SUPPLIER_DELIVERY) return null;

    return (
        <>
            <Divider titlePlacement="left" plain>
                Получатель и адрес доставки
            </Divider>

            {recipientsLoading ? (
                <div style={{ textAlign: 'center', padding: 16 }}>
                    <Spin />
                </div>
            ) : !manualInput ? (
                <>
                    <Form.Item label="Получатель">
                        <Select
                            value={selectedRecipient?.id}
                            onChange={onRecipientChange}
                            placeholder="Выберите получателя"
                            suffixIcon={<UserOutlined />}
                            size="large"
                        >
                            {recipients.map((r) => (
                                <Select.Option key={r.id} value={r.id}>
                                    <Space>
                                        <Text strong>{r.name}</Text>
                                        <Text type="secondary">{r.phone}</Text>
                                        {r.isDefault && (
                                            <Text type="secondary" style={{ fontSize: 12 }}>
                                                (по умолчанию)
                                            </Text>
                                        )}
                                    </Space>
                                </Select.Option>
                            ))}
                        </Select>
                    </Form.Item>

                    {selectedRecipient && (
                        <Form.Item label="Адрес доставки">
                            {addressesLoading ? (
                                <Spin size="small" />
                            ) : addresses.length === 0 ? (
                                <Text type="secondary">
                                    У этого получателя нет сохранённых адресов.{' '}
                                    <Button
                                        type="link"
                                        style={{ padding: 0 }}
                                        onClick={onSwitchToManual}
                                    >
                                        Добавить адрес
                                    </Button>
                                </Text>
                            ) : (
                                <Select
                                    value={selectedAddress?.id}
                                    onChange={onAddressChange}
                                    placeholder="Выберите адрес"
                                    suffixIcon={<EnvironmentOutlined />}
                                    size="large"
                                >
                                    {addresses.map((a) => (
                                        <Select.Option key={a.id} value={a.id}>
                                            <Space>
                                                <Text strong>{a.label}</Text>
                                                <Text type="secondary">
                                                    {a.city}, {a.street}, д. {a.building}
                                                    {a.apartment ? `, кв. ${a.apartment}` : ''}
                                                </Text>
                                            </Space>
                                        </Select.Option>
                                    ))}
                                </Select>
                            )}
                        </Form.Item>
                    )}

                    <Button
                        type="dashed"
                        icon={<PlusOutlined />}
                        onClick={onSwitchToManual}
                        block
                    >
                        Новый получатель и адрес
                    </Button>
                </>
            ) : (
                <>
                    <Row gutter={12}>
                        <Col span={12}>
                            <Form.Item
                                name="newRecipientName"
                                label="ФИО получателя"
                                rules={[{ required: true, message: 'Укажите получателя' }]}
                            >
                                <Input placeholder="Иванов Иван Иванович" />
                            </Form.Item>
                        </Col>
                        <Col span={12}>
                            <Form.Item
                                name="newRecipientPhone"
                                label="Телефон"
                                rules={[
                                    { required: true, message: 'Укажите телефон' },
                                    {
                                        pattern: /^\+?[0-9]{11}$/,
                                        message: 'Введите 11 цифр',
                                    },
                                ]}
                            >
                                <Input placeholder="+79001234567" />
                            </Form.Item>
                        </Col>
                    </Row>

                    <Row gutter={12}>
                        <Col span={8}>
                            <Form.Item name="newAddressLabel" label="Название адреса">
                                <Input placeholder="Офис, склад, дом..." />
                            </Form.Item>
                        </Col>
                        <Col span={10}>
                            <Form.Item
                                name="newCity"
                                label="Город"
                                rules={[{ required: true, message: 'Укажите город' }]}
                            >
                                <Input placeholder="Москва" />
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                            <Form.Item name="newPostalCode" label="Индекс">
                                <Input placeholder="101000" />
                            </Form.Item>
                        </Col>
                    </Row>

                    <Row gutter={12}>
                        <Col span={12}>
                            <Form.Item
                                name="newStreet"
                                label="Улица"
                                rules={[{ required: true, message: 'Укажите улицу' }]}
                            >
                                <Input placeholder="ул. Примерная" />
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                            <Form.Item
                                name="newBuilding"
                                label="Дом"
                                rules={[{ required: true, message: 'Укажите дом' }]}
                            >
                                <Input placeholder="12" />
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                            <Form.Item name="newApartment" label="Квартира/офис">
                                <Input placeholder="45" />
                            </Form.Item>
                        </Col>
                    </Row>

                    {recipients.length > 0 && (
                        <Button
                            type="link"
                            style={{ padding: 0, marginBottom: 8 }}
                            onClick={onSwitchToSaved}
                        >
                            ← Выбрать из сохранённых
                        </Button>
                    )}
                </>
            )}
        </>
    );
};

export default RecipientStep;
