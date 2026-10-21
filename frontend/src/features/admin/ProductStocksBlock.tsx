import { useState } from 'react';
import { App, Button, InputNumber } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { getAdminWarehouses } from '@/api/warehouses';
import { getOwnStocks, setProductStock } from '@/api/adminProducts';

/** Блок «Остатки по складам» карточки товара: правятся только собственные остатки записи. */
const ProductStocksBlock = ({ productId }: { productId: number }) => {
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();
    const [edits, setEdits] = useState<Record<number, number>>({});

    const { data: warehouses = [] } = useQuery({ queryKey: ['adminWarehouses'], queryFn: getAdminWarehouses });
    const { data: stocks = [] } = useQuery({
        queryKey: ['adminOwnStocks', productId],
        queryFn: () => getOwnStocks(productId),
    });

    const mutation = useMutation({
        mutationFn: ({ warehouseId, quantity }: { warehouseId: number; quantity: number }) =>
            setProductStock(productId, warehouseId, quantity),
        onSuccess: (_, { warehouseId }) => {
            messageApi.success('Остаток сохранён');
            setEdits((prev) => Object.fromEntries(Object.entries(prev).filter(([k]) => Number(k) !== warehouseId)));
            queryClient.invalidateQueries({ queryKey: ['adminOwnStocks', productId] });
            queryClient.invalidateQueries({ queryKey: ['adminProduct', productId] });
            queryClient.invalidateQueries({ queryKey: ['adminCatalogProducts'] });
        },
        onError: (err) => messageApi.error(
            (isAxiosError(err) && err.response?.data?.message) || 'Не удалось сохранить остаток'),
    });

    return (
        <div className="rf-card" style={{ overflow: 'hidden', marginTop: 16 }}>
            <div className="rf-card-header"><h3>Остатки по складам</h3></div>
            <div className="rf-card-body" style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>Остаток перезаписывается при следующем импорте.</div>
                {warehouses.map((w) => {
                    const own = stocks.find((s) => s.warehouseId === w.id)?.quantity ?? 0;
                    const edited = edits[w.id];
                    return (
                        <div key={w.id} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <span style={{ flex: 1, fontSize: 13 }}>{w.city}{w.active ? '' : ' (выключен)'}</span>
                            <InputNumber
                                min={0}
                                precision={0}
                                value={edited ?? own}
                                onChange={(v) => setEdits((prev) => ({ ...prev, [w.id]: v ?? 0 }))}
                                style={{ width: 100 }}
                            />
                            <Button
                                size="small"
                                type="primary"
                                disabled={edited === undefined || edited === own}
                                loading={mutation.isPending && mutation.variables?.warehouseId === w.id}
                                onClick={() => mutation.mutate({ warehouseId: w.id, quantity: edited ?? own })}
                            >
                                Сохранить
                            </Button>
                        </div>
                    );
                })}
            </div>
        </div>
    );
};

export default ProductStocksBlock;
