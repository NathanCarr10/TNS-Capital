import { PositionResponse } from '../../../api/trading';
import { PriceBar } from '../../../core/trading/trading.models';

export interface ValuePoint {
  time: string;
  value: number;
}

/**
 * What the current holdings plus cash would have been worth at each bar's close.
 *
 * Quantities and cash are today's: past trades are not replayed, so this shows how
 * the current portfolio has moved, not the account's actual history. A point is only
 * given once every holding has a price, carrying a holding's last close forward
 * where it has no bar at that time.
 */
export function buildValueSeries(
  positions: PositionResponse[],
  barsBySymbol: ReadonlyMap<string, PriceBar[]>,
  cashBalance: number
): ValuePoint[] {
  const held = positions.filter((position) => (barsBySymbol.get(position.symbol)?.length ?? 0) > 0);
  if (held.length === 0) {
    return [];
  }

  const times = [...new Set(held.flatMap((position) => barsBySymbol.get(position.symbol)!.map((bar) => bar.time)))].sort(
    (a, b) => new Date(a).getTime() - new Date(b).getTime()
  );

  // Walks each holding's bars alongside the merged timeline
  const cursors = held.map(() => -1);
  const series: ValuePoint[] = [];
  for (const time of times) {
    const at = new Date(time).getTime();
    let value = cashBalance;
    let priced = true;
    held.forEach((position, index) => {
      const bars = barsBySymbol.get(position.symbol)!;
      while (cursors[index] + 1 < bars.length && new Date(bars[cursors[index] + 1].time).getTime() <= at) {
        cursors[index]++;
      }
      if (cursors[index] < 0) {
        priced = false;
      } else {
        value += position.quantity * bars[cursors[index]].close;
      }
    });
    if (priced) {
      series.push({ time, value });
    }
  }
  return series;
}
