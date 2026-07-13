const UNIT_SHORT_MAP: Record<string, string> = {
    'штука': 'шт', 'шт': 'шт', 'шт.': 'шт',
    'пара': 'пар',
    'упаковка': 'уп', 'уп': 'уп', 'уп.': 'уп',
    'комплект': 'компл', 'компл': 'компл', 'компл.': 'компл',
    'набор': 'набор',
    'метр': 'м', 'м': 'м',
    'сантиметр': 'см', 'см': 'см',
    'миллиметр': 'мм', 'мм': 'мм',
    'литр': 'л', 'л': 'л',
    'миллилитр': 'мл', 'мл': 'мл',
    'килограмм': 'кг', 'кг': 'кг',
    'грамм': 'г', 'г': 'г',
    'тонна': 'т', 'т': 'т',
    'рулон': 'рул',
    'лист': 'лист',
    'погонный метр': 'пог.м', 'пог.м': 'пог.м', 'п.м': 'пог.м',
    'квадратный метр': 'м²', 'м2': 'м²', 'м²': 'м²',
    'кубический метр': 'м³', 'м3': 'м³', 'м³': 'м³',
};

// Склонение по числу: [1, 2-4, 5+]. Только для единиц, где краткая форма склоняется.
const UNIT_PLURAL_MAP: Record<string, [string, string, string]> = {
    'пар': ['пара', 'пары', 'пар'],
    'набор': ['набор', 'набора', 'наборов'],
    'лист': ['лист', 'листа', 'листов'],
    'рул': ['рулон', 'рулона', 'рулонов'],
};

// Убирает хвост "(2 шт.)" у уже импортированных значений ("пара (2 шт.)" → "пара").
const stripPackSuffix = (unit: string): string =>
    unit.replace(/\s*\(\d+\s*шт\.?\)\s*$/i, '').trim();

export const unitShort = (unit: string | null | undefined): string => {
    if (!unit) return 'шт';
    const key = stripPackSuffix(unit).toLowerCase();
    return UNIT_SHORT_MAP[key] ?? key;
};

const pluralIndex = (n: number): 0 | 1 | 2 => {
    const abs = Math.abs(n) % 100;
    const d = abs % 10;
    if (abs > 10 && abs < 20) return 2;
    if (d > 1 && d < 5) return 1;
    if (d === 1) return 0;
    return 2;
};

// "69 пар", "1 пара", "2 пары". Несклоняемые единицы (шт, кг…) возвращаются как есть.
export const unitPlural = (count: number, unit: string | null | undefined): string => {
    const short = unitShort(unit);
    const forms = UNIT_PLURAL_MAP[short];
    return forms ? forms[pluralIndex(count)] : short;
};
