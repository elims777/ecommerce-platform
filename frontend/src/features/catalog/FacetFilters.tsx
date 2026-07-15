import { useQuery } from '@tanstack/react-query';
import { Checkbox, Collapse } from 'antd';
import { getFacets } from '@/api/products';

interface FacetFiltersProps {
    categoryId: number;
    selected: Record<string, string[]>;
    onChange: (name: string, values: string[]) => void;
    onReset: () => void;
}

const FacetFilters = ({ categoryId, selected, onChange, onReset }: FacetFiltersProps) => {
    const { data: facets = [], isLoading } = useQuery({
        queryKey: ['facets', categoryId],
        queryFn: () => getFacets(categoryId),
        staleTime: 5 * 60 * 1000,
    });

    if (isLoading) {
        return (
            <div style={{ border: '1px solid var(--line-1)', borderRadius: 6, background: '#fff', overflow: 'hidden' }}>
                <div style={{ padding: '10px 14px 8px', fontSize: 12, fontWeight: 600, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                    Фильтры
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: '12px 14px' }}>
                    {Array.from({ length: 5 }).map((_, i) => (
                        <div key={i} style={{ height: 14, background: 'var(--surface-3)', borderRadius: 4, width: `${70 + (i % 3) * 10}%` }} />
                    ))}
                </div>
            </div>
        );
    }

    if (facets.length === 0) return null;

    const hasSelection = Object.values(selected).some((v) => v.length > 0);

    return (
        <div style={{ border: '1px solid var(--line-1)', borderRadius: 6, background: '#fff', overflow: 'hidden' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px 8px' }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                    Фильтры
                </span>
                {hasSelection && (
                    <span onClick={onReset} style={{ fontSize: 12, color: 'var(--brand-red)', fontWeight: 500, cursor: 'pointer' }}>
                        Сбросить
                    </span>
                )}
            </div>
            <Collapse
                ghost
                defaultActiveKey={facets.map((f) => f.name)}
                items={facets.map((facet) => ({
                    key: facet.name,
                    label: <span style={{ fontSize: 13, fontWeight: 500, color: 'var(--ink-1)', fontFamily: 'var(--font-body)' }}>{facet.name}</span>,
                    children: (
                        <Checkbox.Group
                            value={selected[facet.name] ?? []}
                            onChange={(vals) => onChange(facet.name, vals as string[])}
                            style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
                            options={facet.values.map((v) => ({ label: v, value: v }))}
                        />
                    ),
                }))}
            />
        </div>
    );
};

export default FacetFilters;
