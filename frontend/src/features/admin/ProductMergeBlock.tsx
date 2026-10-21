import { useState } from 'react';
import { App, Button, Input, Popconfirm } from 'antd';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { getMergeCandidates, getMergedProducts, mergeProduct, unmergeProduct } from '@/api/adminProducts';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import type { Product } from '@/types/product';

const MIN_QUERY_LENGTH = 2;
const SEARCH_DEBOUNCE_MS = 300;

/** Блок «Склейка с товаром 1С»: у товара Факела — список склеенных; у склеенного товара 1С — ссылка на основной. */
const ProductMergeBlock = ({ product }: { product: Product }) => {
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();
    const [search, setSearch] = useState('');
    const debounced = useDebouncedValue(search.trim(), SEARCH_DEBOUNCE_MS);
    // Родитель с вариантами не склеивается: склейка живёт на вариантах
    const canMerge = product.source === 'FTK' && product.children.length === 0;

    const { data: merged = [] } = useQuery({
        queryKey: ['adminMerged', product.id],
        queryFn: () => getMergedProducts(product.id),
        enabled: canMerge,
    });
    const { data: candidates = [] } = useQuery({
        queryKey: ['adminMergeCandidates', debounced],
        queryFn: () => getMergeCandidates(debounced),
        enabled: canMerge && debounced.length >= MIN_QUERY_LENGTH,
    });

    const onChanged = () => {
        queryClient.invalidateQueries({ queryKey: ['adminMerged', product.id] });
        queryClient.invalidateQueries({ queryKey: ['adminMergeCandidates'] });
        queryClient.invalidateQueries({ queryKey: ['adminProduct', product.id] });
        queryClient.invalidateQueries({ queryKey: ['adminCatalogProducts'] });
    };
    const onError = (fallback: string) => (err: unknown) =>
        messageApi.error((isAxiosError(err) && err.response?.data?.message) || fallback);

    const mergeMutation = useMutation({
        mutationFn: (oneCId: number) => mergeProduct(product.id, oneCId),
        onSuccess: () => { messageApi.success('Товар привязан'); setSearch(''); onChanged(); },
        onError: onError('Не удалось привязать товар'),
    });
    const unmergeMutation = useMutation({
        mutationFn: unmergeProduct,
        onSuccess: () => { messageApi.success('Товар отвязан'); onChanged(); },
        onError: onError('Не удалось отвязать товар'),
    });

    if (product.mergedIntoProductId) {
        return (
            <div className="rf-card" style={{ overflow: 'hidden', marginTop: 16 }}>
                <div className="rf-card-body" style={{ fontSize: 13 }}>
                    Склеен с → <Link to={`/admin/products/${product.mergedIntoProductId}/edit`}>товар #{product.mergedIntoProductId}</Link>
                </div>
            </div>
        );
    }
    if (!canMerge) return null;

    const stocksLabel = (p: Product) =>
        (p.stocks ?? []).map((s) => `${s.city}: ${s.quantity}`).join(' · ') || 'нет остатков';

    return (
        <div className="rf-card" style={{ overflow: 'visible', marginTop: 16 }}>
            <div className="rf-card-header"><h3>Склейка с товарами 1С</h3></div>
            <div className="rf-card-body" style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {merged.length === 0 && <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>Нет склеенных товаров</div>}
                {merged.map((m) => (
                    <div key={m.id} style={{ display: 'flex', alignItems: 'flex-start', gap: 8, fontSize: 13 }}>
                        <div style={{ flex: 1, minWidth: 0 }}>
                            <div style={{ fontWeight: 500 }}>{m.name}</div>
                            <div style={{ color: 'var(--ink-3)', fontSize: 12 }}>
                                Арт. {m.sku ?? '—'} · Ид 1С: {m.oneCExternalId ?? m.externalId ?? '—'}
                            </div>
                            <div style={{ color: 'var(--ink-3)', fontSize: 12 }}>{stocksLabel(m)}</div>
                        </div>
                        <Popconfirm title="Отвязать товар?" okText="Отвязать" cancelText="Отмена" onConfirm={() => unmergeMutation.mutate(m.id)}>
                            <Button size="small" danger loading={unmergeMutation.isPending && unmergeMutation.variables === m.id}>Отвязать</Button>
                        </Popconfirm>
                    </div>
                ))}
                <Input
                    placeholder="Найти товар 1С по названию или артикулу..."
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                />
                {candidates.map((c) => (
                    <div key={c.id} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
                        <div style={{ flex: 1, minWidth: 0 }}>
                            <div>{c.name}</div>
                            <div style={{ color: 'var(--ink-3)', fontSize: 12 }}>Арт. {c.sku ?? '—'}</div>
                        </div>
                        <Button
                            size="small"
                            type="primary"
                            loading={mergeMutation.isPending && mergeMutation.variables === c.id}
                            onClick={() => mergeMutation.mutate(c.id)}
                        >
                            Привязать
                        </Button>
                    </div>
                ))}
            </div>
        </div>
    );
};

export default ProductMergeBlock;
