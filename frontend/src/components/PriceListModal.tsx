import { useMemo, useState } from 'react';
import { Modal, Tree, App } from 'antd';
import type { TreeDataNode } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { getCategoryTree } from '@/api/categories';
import { createPriceList } from '@/api/priceLists';
import type { CategoryTree } from '@/types/product';

const mapToTreeData = (categories: CategoryTree[]): TreeDataNode[] =>
    categories
        .filter((c) => c.isActive)
        .map((c) => ({
            key: c.id,
            title: c.name,
            children: c.children.length > 0 ? mapToTreeData(c.children) : undefined,
        }));

/** id → parentId по всему дереву — нужно, чтобы при отправке оставить только "верхние" выбранные узлы */
const buildParentMap = (categories: CategoryTree[], map: Map<number, number | null> = new Map()): Map<number, number | null> => {
    for (const c of categories) {
        map.set(c.id, c.parentId);
        if (c.children.length > 0) buildParentMap(c.children, map);
    }
    return map;
};

/** Оставляет только id, чей родитель не входит в тот же набор — т.е. корни выбранных поддеревьев */
const topLevelIds = (ids: number[], parentMap: Map<number, number | null>): number[] => {
    const idSet = new Set(ids);
    return ids.filter((id) => {
        const parentId = parentMap.get(id);
        return parentId == null || !idSet.has(parentId);
    });
};

interface PriceListModalProps {
    open: boolean;
    onClose: () => void;
}

const PriceListModal = ({ open, onClose }: PriceListModalProps) => {
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();
    const [checkedKeys, setCheckedKeys] = useState<React.Key[]>([]);

    const { data: categoryTree = [], isLoading } = useQuery({
        queryKey: ['categories', 'tree'],
        queryFn: getCategoryTree,
        staleTime: 5 * 60 * 1000,
        enabled: open,
    });

    const treeData = useMemo(() => mapToTreeData(categoryTree), [categoryTree]);
    const parentMap = useMemo(() => buildParentMap(categoryTree), [categoryTree]);

    const mutation = useMutation({
        mutationFn: (categoryIds: number[]) => createPriceList(categoryIds),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['priceLists'] });
            setCheckedKeys([]);
            onClose();
            messageApi.success('Прайс формируется, файл появится в разделе «Прайс-листы» в личном кабинете. Это займёт некоторое время.');
        },
        onError: (err) => {
            if (isAxiosError(err) && err.response?.status === 422) {
                messageApi.warning('У вас уже формируется прайс — дождитесь готовности.');
            } else {
                messageApi.error('Не удалось заказать прайс-лист. Попробуйте позже.');
            }
        },
    });

    const handleSubmit = () => {
        const ids = topLevelIds(checkedKeys.map((k) => Number(k)), parentMap);
        mutation.mutate(ids);
    };

    return (
        <Modal
            title="Заказать прайс-лист"
            open={open}
            onCancel={onClose}
            okText="Сформировать"
            onOk={handleSubmit}
            okButtonProps={{ disabled: checkedKeys.length === 0, loading: mutation.isPending }}
            confirmLoading={mutation.isPending}
        >
            {isLoading ? (
                <div style={{ textAlign: 'center', padding: 24 }}>Загрузка категорий...</div>
            ) : (
                <Tree
                    checkable
                    checkedKeys={checkedKeys}
                    onCheck={(keys) => setCheckedKeys(keys as React.Key[])}
                    treeData={treeData}
                    defaultExpandAll
                    style={{ maxHeight: 400, overflow: 'auto' }}
                />
            )}
        </Modal>
    );
};

export default PriceListModal;
