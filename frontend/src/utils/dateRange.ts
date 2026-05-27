export type Period = 'day' | 'week' | 'month' | 'year';

export interface DateRange {
  dateFrom: string;
  dateTo: string;
  label: string;
}

export function getDateRange(period: Period): DateRange {
  const now = new Date();
  const to = new Date(now);
  to.setHours(23, 59, 59, 999);

  const from = new Date(now);
  from.setHours(0, 0, 0, 0);

  if (period === 'week') {
    from.setDate(from.getDate() - 6);
  } else if (period === 'month') {
    from.setDate(1);
  } else if (period === 'year') {
    from.setMonth(0, 1);
  }

  return {
    dateFrom: from.toISOString(),
    dateTo: to.toISOString(),
    label: { day: 'Сегодня', week: 'Неделя', month: 'Месяц', year: 'Год' }[period],
  };
}

export function groupRevenueByDay(
  orders: { createdAt: string; totalAmount: number; status: string }[],
  revenueStatuses: string[],
): { date: string; revenue: number }[] {
  const map = new Map<string, number>();
  for (const order of orders) {
    const statusCode = typeof order.status === 'object'
      ? (order.status as { code: string }).code
      : order.status;
    if (!revenueStatuses.includes(statusCode)) continue;
    const day = order.createdAt.slice(0, 10);
    map.set(day, (map.get(day) ?? 0) + order.totalAmount);
  }
  return Array.from(map.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, revenue]) => ({ date, revenue }));
}
