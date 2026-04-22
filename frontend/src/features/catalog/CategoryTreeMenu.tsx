import { useState } from 'react';
import { Typography } from 'antd';
import { RightOutlined } from '@ant-design/icons';
import type { CategoryTree } from '@/types/product';

const { Text } = Typography;

interface CategoryNodeProps {
    category: CategoryTree;
    selectedId?: number;
    onSelect: (id: number) => void;
    depth?: number;
    defaultExpanded?: boolean;
}

const CategoryNode = ({
                          category,
                          selectedId,
                          onSelect,
                          depth = 0,
                          defaultExpanded = false,
                      }: CategoryNodeProps) => {
    const hasChildren = category.children.filter((c) => c.isActive).length > 0;
    const isSelected = selectedId === category.id;
    const [expanded, setExpanded] = useState(defaultExpanded);

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
                    gap: 6,
                    padding: `6px 12px 6px ${12 + depth * 16}px`,
                    cursor: 'pointer',
                    borderLeft: isSelected ? '3px solid #1677ff' : '3px solid transparent',
                    background: isSelected ? '#e6f4ff' : 'transparent',
                    borderRadius: '0 6px 6px 0',
                    transition: 'background 0.15s, border-color 0.15s',
                    userSelect: 'none',
                }}
                onMouseEnter={(e) => {
                    if (!isSelected) {
                        e.currentTarget.style.background = '#f5f5f5';
                    }
                }}
                onMouseLeave={(e) => {
                    if (!isSelected) {
                        e.currentTarget.style.background = 'transparent';
                    }
                }}
            >
                {/* Стрелка — занимает фиксированное место даже для листьев */}
                <span
                    style={{
                        width: 14,
                        height: 14,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0,
                        color: hasChildren ? '#8c8c8c' : 'transparent',
                        transform: expanded ? 'rotate(90deg)' : 'rotate(0deg)',
                        transition: 'transform 0.2s ease-out',
                        fontSize: 10,
                    }}
                >
                    <RightOutlined />
                </span>

                <Text
                    style={{
                        fontSize: depth === 0 ? 14 : 13,
                        fontWeight: depth === 0 ? 600 : 400,
                        color: isSelected ? '#1677ff' : '#262626',
                        lineHeight: '1.4',
                        flex: 1,
                    }}
                >
                    {category.name}
                </Text>
            </div>

            {/* Дочерние узлы — анимация через max-height */}
            <div
                style={{
                    maxHeight: expanded ? 2000 : 0,
                    overflow: 'hidden',
                    transition: 'max-height 0.25s ease-out',
                }}
            >
                {activeChildren.map((child) => (
                    <CategoryNode
                        key={child.id}
                        category={child}
                        selectedId={selectedId}
                        onSelect={onSelect}
                        depth={depth + 1}
                        defaultExpanded={false}
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
                    defaultExpanded={false}
                />
            ))}
        </div>
    );
};

export default CategoryTreeMenu;