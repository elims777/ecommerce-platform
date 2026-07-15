import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { CategoryTree } from '@/types/product';

const ChevRight = () => (
    <svg viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
        <path d="m9 18 6-6-6-6"/>
    </svg>
);

const isLeaf = (category: CategoryTree): boolean =>
    category.children.filter((c) => c.isActive).length === 0;

const activeSorted = (categories: CategoryTree[]): CategoryTree[] =>
    categories.filter((c) => c.isActive).sort((a, b) => a.displayOrder - b.displayOrder);

interface CategoryColumnProps {
    categories: CategoryTree[];
    onNavigate: (id: number) => void;
    depth: number;
}

/** Одна колонка каскада + рекурсивно колонка следующего уровня активной категории с детьми */
const CategoryColumn = ({ categories, onNavigate, depth }: CategoryColumnProps) => {
    const [hoveredId, setHoveredId] = useState<number | null>(null);
    const hovered = categories.find((c) => c.id === hoveredId);
    const nextLevel = hovered && !isLeaf(hovered) ? activeSorted(hovered.children) : null;

    return (
        <>
            <div style={{ width: 260, borderRight: nextLevel ? '1px solid var(--line-1)' : 'none', padding: '8px 0', flexShrink: 0 }}>
                {categories.map((category) => {
                    const leaf = isLeaf(category);
                    const isHovered = hoveredId === category.id;
                    const content = (
                        <>
                            <span style={{
                                fontSize: 13.5, color: isHovered ? 'var(--brand-red)' : 'var(--ink-1)',
                                fontWeight: isHovered ? 500 : 400, flex: 1, fontFamily: 'var(--font-body)',
                            }}>
                                {category.name}
                            </span>
                            {!leaf && (
                                <span style={{ display: 'flex', alignItems: 'center', color: 'var(--ink-3)', flexShrink: 0 }}>
                                    <ChevRight />
                                </span>
                            )}
                        </>
                    );
                    const commonStyle = {
                        display: 'flex', alignItems: 'center', gap: 8,
                        padding: '9px 16px', cursor: leaf ? 'pointer' : 'default',
                        background: isHovered ? 'var(--surface-3)' : 'transparent',
                        transition: 'background 0.12s',
                    };
                    return leaf ? (
                        <div
                            key={category.id}
                            onClick={() => onNavigate(category.id)}
                            onMouseEnter={() => setHoveredId(category.id)}
                            style={commonStyle}
                        >
                            {content}
                        </div>
                    ) : (
                        <div
                            key={category.id}
                            onMouseEnter={() => setHoveredId(category.id)}
                            style={commonStyle}
                        >
                            {content}
                        </div>
                    );
                })}
            </div>
            {nextLevel && (
                <CategoryColumn categories={nextLevel} onNavigate={onNavigate} depth={depth + 1} />
            )}
        </>
    );
};

interface CatalogMegaMenuProps {
    categories: CategoryTree[];
    onClose: () => void;
}

const CatalogMegaMenu = ({ categories, onClose }: CatalogMegaMenuProps) => {
    const navigate = useNavigate();

    const handleNavigate = (id: number) => {
        navigate(`/catalog?category=${id}`);
        onClose();
    };

    return (
        <div style={{
            position: 'absolute', top: '100%', left: 0,
            background: '#fff', border: '1px solid var(--line-1)', borderTop: 'none',
            borderRadius: '0 0 var(--r-3) var(--r-3)', boxShadow: '0 12px 24px -8px rgba(0,0,0,0.15)',
            display: 'flex', zIndex: 'var(--z-header)' as unknown as number,
        }}>
            <CategoryColumn categories={activeSorted(categories)} onNavigate={handleNavigate} depth={0} />
        </div>
    );
};

export default CatalogMegaMenu;
