import { isAxiosError } from 'axios';
import type { NavigateFunction } from 'react-router-dom';
import type { App } from 'antd';

type ModalApi = ReturnType<typeof App.useApp>['modal'];

/**
 * Распознаёт 422-ответ бэкенда "профиль не заполнен" и показывает модалку
 * с переходом на /profile. Возвращает true, если ошибка обработана.
 */
export const handleProfileIncomplete = (
    err: unknown,
    modal: ModalApi,
    navigate: NavigateFunction,
): boolean => {
    if (!isAxiosError(err) || err.response?.status !== 422) return false;
    const missing = (err.response?.data as { missing?: unknown })?.missing;
    if (!Array.isArray(missing)) return false;

    modal.confirm({
        title: 'Заполните профиль для работы на портале',
        content: (
            <>
                <p>Чтобы добавлять товары в корзину и оформлять заказы, заполните в профиле:</p>
                <ul>
                    {missing.map((field) => (
                        <li key={String(field)}>{String(field)}</li>
                    ))}
                </ul>
            </>
        ),
        okText: 'Заполнить профиль',
        cancelText: 'Отмена',
        onOk: () => navigate('/profile'),
    });
    return true;
};
