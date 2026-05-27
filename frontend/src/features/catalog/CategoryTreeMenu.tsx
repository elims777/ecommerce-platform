import { useState } from 'react';
import type { CategoryTree } from '@/types/product';

const ChevRight = () => (
    <svg viewBox="0 0 24 24" width="10" height="10" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
        <path d="m9 18 6-6-6-6"/>
    </svg>
);

interface CategoryNodeProps {
    category: CategoryTree;
    selectedId?: number;
    onSelect: (id: number) => void;
    depth?: number;
}

const CategoryNode = ({ category, selectedId, onSelect, depth = 0 }: CategoryNodeProps) => {
    const hasChildren = category.children.filter((c) => c.isActive).length > 0;
    const isSelected = selectedId === category.id;
    const [expanded, setExpanded] = useState(false);

    const activeChildren = category.children
        .filter((c) => c.isActive)
        .sort((a, b) => a.displayOrder - b.displayOrder);

    const handleClick = () => {
        if (hasChildren) setExpanded((prev) => !prev);
        onSelect(category.id);
    };

    return (
        <div>
            <div
                onClick={handleClick}
                style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    padding: `9px 14px 9px ${depth > 0 ? 40 : 11}px`,
                    cursor: 'pointer',
                    borderLeft: isSelected ? '3px solid var(--brand-red)' : '3px solid transparent',
                    background: isSelected ? 'var(--red-tint)' : 'transparent',
                    transition: 'background 0.12s, border-color 0.12s',
                    userSelect: 'none',
                }}
                onMouseEnter={(e) => {
                    if (!isSelected) e.currentTarget.style.background = 'var(--surface-3)';
                }}
                onMouseLeave={(e) => {
                    if (!isSelected) e.currentTarget.style.background = 'transparent';
                }}
            >
                <span
                    style={{
                        width: 14,
                        height: 14,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0,
                        color: hasChildren ? 'var(--ink-3)' : 'transparent',
                        transform: expanded ? 'rotate(90deg)' : 'rotate(0deg)',
                        transition: 'transform 0.2s ease-out',
                        fontSize: 10,
                    }}
                >
                    <ChevRight />
                </span>
                <span
                    style={{
                        fontSize: depth === 0 ? 13.5 : 13,
                        fontWeight: isSelected ? 600 : depth === 0 ? 500 : 400,
                        color: isSelected ? 'var(--brand-red)' : 'var(--ink-1)',
                        lineHeight: 1.4,
                        flex: 1,
                        fontFamily: 'var(--font-body)',
                    }}
                >
                    {category.name}
                </span>
            </div>

            <div
                style={{
                    maxHeight: expanded ? 2000 : 0,
                    overflow: 'hidden',
                    transition: 'max-height 0.25s ease-out',
                    background: expanded ? 'var(--surface-2)' : 'transparent',
                }}
            >
                {activeChildren.map((child) => (
                    <CategoryNode
                        key={child.id}
                        category={child}
                        selectedId={selectedId}
                        onSelect={onSelect}
                        depth={depth + 1}
                    />
                ))}
            </div>
        </div>
    );
};

interface CategoryTreeProps {
    categories: CategoryTree[];
    selectedId?: number;
    onSelect: (id: number) => void;
}

const CategoryTreeMenu = ({ categories, selectedId, onSelect }: CategoryTreeProps) => {
    const activeRoots = categories
        .filter((c) => c.isActive)
        .sort((a, b) => a.displayOrder - b.displayOrder);

    return (
        <div style={{ margin: '0 -12px' }}>
            {activeRoots.map((category) => (
                <CategoryNode
                    key={category.id}
                    category={category}
                    selectedId={selectedId}
                    onSelect={onSelect}
                    depth={0}
                />
            ))}
        </div>
    );
};

export default CategoryTreeMenu;
