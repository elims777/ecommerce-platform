import { useState } from 'react';
import { App, Button, Form, Input, InputNumber, Modal, Switch, Table } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { isAxiosError } from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createWarehouse, getAdminWarehouses, updateWarehouse } from '@/api/warehouses';
import type { WarehouseRequest } from '@/api/warehouses';
import type { Warehouse } from '@/types/product';

/** Склады (города отгрузки): список, создание, редактирование, включение/выключение. */
const WarehousesTab = () => {
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();
    const [form] = Form.useForm<WarehouseRequest>();
    const [editing, setEditing] = useState<Warehouse | null>(null);
    const [open, setOpen] = useState(false);

    const { data: warehouses = [], isLoading } = useQuery({
        queryKey: ['adminWarehouses'],
        queryFn: getAdminWarehouses,
    });

    const mutation = useMutation({
        mutationFn: ({ id, values }: { id?: number; values: WarehouseRequest }) =>
            id ? updateWarehouse(id, values) : createWarehouse(values),
        onSuccess: () => {
            messageApi.success('Склад сохранён');
            void queryClient.invalidateQueries({ queryKey: ['adminWarehouses'] });
            void queryClient.invalidateQueries({ queryKey: ['warehouses'] });
            setOpen(false);
        },
        onError: (err) => messageApi.error(
            (isAxiosError(err) && err.response?.data?.message) || 'Не удалось сохранить склад'),
    });

    const openForm = (warehouse: Warehouse | null) => {
        setEditing(warehouse);
        form.resetFields();
        form.setFieldsValue(warehouse ?? { active: true, pickupAvailable: false, sortOrder: 0 });
        setOpen(true);
    };

    const toRequest = (w: Warehouse, patch: Partial<WarehouseRequest>): WarehouseRequest => ({
        code: w.code, name: w.name, city: w.city, pickupAvailable: w.pickupAvailable,
        active: w.active, sortOrder: w.sortOrder, ...patch,
    });

    return (
        <>
            <Button type="primary" icon={<PlusOutlined />} style={{ marginBottom: 16 }} onClick={() => openForm(null)}>
                Добавить
            </Button>
            <Table<Warehouse>
                rowKey="id"
                loading={isLoading}
                dataSource={warehouses}
                pagination={false}
                scroll={{ x: true }}
                columns={[
                    { title: 'Код', dataIndex: 'code' },
                    { title: 'Название', dataIndex: 'name' },
                    { title: 'Город', dataIndex: 'city' },
                    { title: 'Самовывоз', dataIndex: 'pickupAvailable', render: (v: boolean) => (v ? 'да' : 'нет') },
                    {
                        title: 'Активен',
                        dataIndex: 'active',
                        render: (active: boolean, w) => (
                            <Switch
                                checked={active}
                                loading={mutation.isPending}
                                onChange={(checked) => mutation.mutate({ id: w.id, values: toRequest(w, { active: checked }) })}
                            />
                        ),
                    },
                    { title: 'Порядок', dataIndex: 'sortOrder' },
                    { title: '', render: (_, w) => <Button type="link" onClick={() => openForm(w)}>Изменить</Button> },
                ]}
            />
            <Modal
                open={open}
                title={editing ? 'Изменить склад' : 'Новый склад'}
                onCancel={() => setOpen(false)}
                onOk={() => form.submit()}
                okText="Сохранить"
                confirmLoading={mutation.isPending}
                destroyOnHidden
            >
                <Form<WarehouseRequest>
                    form={form}
                    layout="vertical"
                    onFinish={(values) => mutation.mutate({ id: editing?.id, values })}
                >
                    <Form.Item
                        name="code"
                        label="Код"
                        rules={[
                            { required: true, message: 'Укажите код' },
                            { pattern: /^[A-Z0-9_]+$/, message: 'Только A-Z, 0-9 и _' },
                            { max: 30 },
                        ]}
                    >
                        <Input disabled={!!editing} />
                    </Form.Item>
                    <Form.Item name="name" label="Название" rules={[{ required: true, message: 'Укажите название' }, { max: 100 }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item name="city" label="Город" rules={[{ required: true, message: 'Укажите город' }, { max: 100 }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item name="sortOrder" label="Порядок" rules={[{ required: true, message: 'Укажите порядок' }]}>
                        <InputNumber precision={0} />
                    </Form.Item>
                    <Form.Item name="pickupAvailable" label="Самовывоз" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                    <Form.Item name="active" label="Активен" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                </Form>
            </Modal>
        </>
    );
};

export default WarehousesTab;
