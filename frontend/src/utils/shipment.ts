import type { ShipmentDto, ShipmentPayerDto } from '@/types/delivery';
import { pluralIndex } from '@/utils/unitOfMeasure';

type PayerRole = 'SENDER' | 'RECEIVER' | 'THIRD';

// день по Москве — так же, как группирует бэкенд
export const formatDate = (iso: string) => new Date(iso).toLocaleDateString('ru-RU', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Europe/Moscow' });

export const formatNumber = (n: number, maxFractionDigits: number) => n.toLocaleString('ru-RU', { maximumFractionDigits: maxFractionDigits });

/** Русское склонение: 1 груз, 2 груза, 5 грузов */
export const plural = (n: number, one: string, few: string, many: string) => [one, few, many][pluralIndex(n)];

/** «Город, от терминала» / «Город, до адреса»; режим неизвестен — только город */
export const routeLine = (city: string | null, address: boolean | null, prep: 'от' | 'до') =>
    [city, address == null ? null : `${prep} ${address ? 'адреса' : 'терминала'}`].filter(Boolean).join(', ');

/** Сторона плательщика: по роли, затем по ИНН, затем по имени; иначе третье лицо */
export const payerRole = (s: ShipmentDto, p: ShipmentPayerDto): PayerRole => {
    if (p.role) return p.role;
    if (p.inn && p.inn === s.senderInn) return 'SENDER';
    if (p.inn && p.inn === s.receiverInn) return 'RECEIVER';
    const same = (a: string | null, b: string | null) => !!a && !!b && a.trim().toLowerCase() === b.trim().toLowerCase();
    if (same(p.name, s.sender)) return 'SENDER';
    if (same(p.name, s.receiver)) return 'RECEIVER';
    return 'THIRD';
};
export const payerSide = (s: ShipmentDto, role: PayerRole) => s.payers.some((p) => payerRole(s, p) === role);
