import type { WarehousePointDto } from '@/types/order';
import type { PickerPoint } from './PointsPicker';

/** Пункт самовывоза → точка для выбора */
export const warehouseToPickerPoint = (w: WarehousePointDto): PickerPoint => ({
    id: w.id,
    title: w.name,
    address: `${w.city}, ${w.street}, д. ${w.building}${w.workingHours ? ` | ${w.workingHours}` : ''}`,
    latitude: w.latitude,
    longitude: w.longitude,
});
