import { useCallback, useEffect, useRef } from 'react';
import type { PointerEvent as ReactPointerEvent, RefObject } from 'react';

/** Перетаскивание нативного <dialog> за заголовок. Вешать возвращённый onPointerDown на заголовок. */
export function useDraggableDialog(dialogRef: RefObject<HTMLDialogElement | null>) {
    const offset = useRef({ x: 0, y: 0 });

    // Сброс позиции при каждом открытии (атрибут open ставится при showModal)
    useEffect(() => {
        const dialog = dialogRef.current;
        if (!dialog) return;
        const observer = new MutationObserver(() => {
            if (!dialog.open) {
                offset.current = { x: 0, y: 0 };
                dialog.style.transform = '';
            }
        });
        observer.observe(dialog, { attributes: true, attributeFilter: ['open'] });
        return () => observer.disconnect();
    }, [dialogRef]);

    return useCallback((e: ReactPointerEvent<HTMLElement>) => {
        const dialog = dialogRef.current;
        if (!dialog || (e.target as HTMLElement).closest('button, input, select, textarea, a')) return;
        // capture: pointerup/pointercancel придут на заголовок, даже если курсор ушёл за окно браузера
        const handle = e.currentTarget;
        handle.setPointerCapture(e.pointerId);
        const startX = e.clientX - offset.current.x;
        const startY = e.clientY - offset.current.y;
        const onMove = (ev: PointerEvent) => {
            if (!dialog.open) return; // закрыли по Esc посреди перетаскивания
            offset.current = { x: ev.clientX - startX, y: ev.clientY - startY };
            dialog.style.transform = `translate(${offset.current.x}px, ${offset.current.y}px)`;
        };
        const onUp = () => {
            handle.removeEventListener('pointermove', onMove);
            handle.removeEventListener('pointerup', onUp);
            handle.removeEventListener('pointercancel', onUp);
        };
        handle.addEventListener('pointermove', onMove);
        handle.addEventListener('pointerup', onUp);
        handle.addEventListener('pointercancel', onUp);
        e.preventDefault(); // не выделять текст при перетаскивании
    }, [dialogRef]);
}
