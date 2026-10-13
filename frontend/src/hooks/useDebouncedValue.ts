import { useEffect, useState } from 'react';

/** Значение, обновляющееся через `delay` мс после последнего изменения `value`. */
export const useDebouncedValue = <T,>(value: T, delay: number): T => {
    const [debounced, setDebounced] = useState(value);
    useEffect(() => {
        const timer = setTimeout(() => setDebounced(value), delay);
        return () => clearTimeout(timer);
    }, [value, delay]);
    return debounced;
};
