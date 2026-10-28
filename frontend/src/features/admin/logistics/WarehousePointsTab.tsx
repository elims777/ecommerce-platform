import { useState } from 'react';
import { App, Button, Form, Input, InputNumber, Modal, Switch, Table, Tag } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { isAxiosError } from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import PointsMap from '@/components/maps/PointsMap';
import { geocodeAddress } from '@/api/yandexGeo';
import { YANDEX_GEOCODER_KEY, YANDEX_MAPS_KEY } from '@/lib/ymaps';
import { createWarehousePoint, getAdminWarehousePoints, updateWarehousePoint } from '@/api/adminLogistics';
import type { WarehousePointDto } from '@/types/order';
import type { WarehousePointRequest } from '@/types/delivery';

/** Точки самовывоза: список, создание, редактирование, выключение (удаления нет — на точки ссылаются заказы). */
const WarehousePointsTab = () => {
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();
    const [form] = Form.useForm<WarehousePointRequest>();
    const [editing, setEditing] = useState<WarehousePointDto | null>(null);
    const [open, setOpen] = useState(false);
    const [geocoding, setGeocoding] = useState(false);

    const { data: points = [], isLoading } = useQuery({
        queryKey: ['adminWarehousePoints'],
        queryFn: getAdminWarehousePoints,
    });

    const mutation = useMutation({
        mutationFn: (values: WarehousePointRequest) =>
            editing ? updateWarehousePoint(editing.id, values) : createWarehousePoint(values),
        onSuccess: () => {
            messageApi.success('Точка сохранена');
            void queryClient.invalidateQueries({ queryKey: ['adminWarehousePoints'] });
            void queryClient.invalidateQueries({ queryKey: ['warehousePoints'] });
            setOpen(false);
        },
        onError: (err) => messageApi.error(
            (isAxiosError(err) && err.response?.data?.message) || 'Не удалось сохранить точку'),
    });

    const findOnMap = async () => {
        const { city, street, building } = form.getFieldsValue(['city', 'street', 'building']);
        setGeocoding(true);
        try {
            const geo = await geocodeAddress([city, street, building].filter(Boolean).join(', '));
            if (geo) form.setFieldsValue({ latitude: geo.latitude, longitude: geo.longitude });
            else messageApi.warning('Адрес не найден на карте — уточните адрес или введите координаты вручную');
        } finally {
            setGeocoding(false);
        }
    };

    const openForm = (point: WarehousePointDto | null) => {
        setEditing(point);
        form.resetFields();
        form.setFieldsValue(point
            ? { ...point, workingHours: point.workingHours ?? undefined, description: point.description ?? undefined }
            : { active: true });
        setOpen(true);
    };

    return (
        <>
            <Button type="primary" icon={<PlusOutlined />} style={{ marginBottom: 16 }} onClick={() => openForm(null)}>
                Добавить точку
            </Button>
            <Table<WarehousePointDto>
                rowKey="id"
                loading={isLoading}
                dataSource={points}
                pagination={false}
                scroll={{ x: true }}
                columns={[
                    { title: 'Название', dataIndex: 'name' },
                    {
                        title: 'Адрес',
                        render: (_, p) => [p.postalCode, p.city, p.street, `д. ${p.building}`].filter(Boolean).join(', '),
                    },
                    { title: 'Телефон', dataIndex: 'phone' },
                    { title: 'Часы работы', dataIndex: 'workingHours' },
                    {
                        title: 'Статус',
                        render: (_, p) => p.active ? <Tag color="green">Активна</Tag> : <Tag>Выключена</Tag>,
                    },
                    { title: '', render: (_, p) => <Button type="link" onClick={() => openForm(p)}>Изменить</Button> },
                ]}
            />
            <Modal
                open={open}
                title={editing ? 'Изменить точку самовывоза' : 'Новая точка самовывоза'}
                onCancel={() => setOpen(false)}
                onOk={() => form.submit()}
                okText="Сохранить"
                confirmLoading={mutation.isPending}
                destroyOnHidden
            >
                <Form<WarehousePointRequest> form={form} layout="vertical" onFinish={(v) => mutation.mutate(v)}>
                    <Form.Item name="name" label="Название" rules={[{ required: true, message: 'Укажите название' }, { max: 100 }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item name="city" label="Город" rules={[{ required: true, message: 'Укажите город' }, { max: 100 }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item name="street" label="Улица" rules={[{ required: true, message: 'Укажите улицу' }, { max: 150 }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item name="building" label="Дом" rules={[{ required: true, message: 'Укажите дом' }, { max: 20 }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item name="postalCode" label="Индекс" rules={[{ max: 10 }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item name="phone" label="Телефон" rules={[{ required: true, message: 'Укажите телефон' }, { max: 20 }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item name="workingHours" label="Часы работы" rules={[{ max: 200 }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item name="description" label="Описание" rules={[{ max: 500 }]}>
                        <Input.TextArea rows={2} />
                    </Form.Item>
                    <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                        <Form.Item name="latitude" label="Широта">
                            <InputNumber min={-90} max={90} step={0.000001} style={{ width: 160 }} />
                        </Form.Item>
                        <Form.Item name="longitude" label="Долгота">
                            <InputNumber min={-180} max={180} step={0.000001} style={{ width: 160 }} />
                        </Form.Item>
                    </div>
                    {YANDEX_MAPS_KEY && (
                        <>
                            {YANDEX_GEOCODER_KEY && (
                                <Button onClick={findOnMap} loading={geocoding} style={{ marginBottom: 12 }}>Найти на карте</Button>
                            )}
                            <Form.Item noStyle shouldUpdate={(a, b) => a.latitude !== b.latitude || a.longitude !== b.longitude}>
                                {() => {
                                    const latitude = form.getFieldValue('latitude');
                                    const longitude = form.getFieldValue('longitude');
                                    return latitude != null && longitude != null ? (
                                        <div style={{ marginBottom: 12 }}>
                                            <PointsMap
                                                points={[{ id: 'point', latitude, longitude }]}
                                                onDragEnd={(lat, lng) => form.setFieldsValue({ latitude: lat, longitude: lng })}
                                            />
                                        </div>
                                    ) : null;
                                }}
                            </Form.Item>
                        </>
                    )}
                    <Form.Item name="active" label="Активна" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                </Form>
            </Modal>
        </>
    );
};

export default WarehousePointsTab;
