import { MarketQuote, PositionResponse } from '../trading.models';

export type SplitColor = 'series-1' | 'series-2' | 'series-3' | 'series-4' | 'series-other' | 'series-cash';

export interface SplitSegment {
  key: string;
  label: string;
  value: number;
  /** Share of the priced portfolio, holdings plus cash. */
  percent: number;
  color: SplitColor;
}

export interface PortfolioSplit {
  segments: SplitSegment[];
  total: number;
  /** Positions without a live price, which are left out of the split. */
  unpricedCount: number;
}

// Past four holdings the rest fold into "Other", keeping the bar to six readable segments
export const MAX_NAMED_HOLDINGS = 4;

const SERIES: SplitColor[] = ['series-1', 'series-2', 'series-3', 'series-4'];

/**
 * Splits the portfolio into its largest holdings, the rest as "Other", and cash.
 * Holdings are listed by symbol and coloured in that order so a symbol keeps its
 * colour as prices move, and the colours sit next to each other in the order they
 * were checked for colour-blind separation.
 */
export function calculatePortfolioSplit(
  positions: PositionResponse[],
  quotes: ReadonlyMap<string, MarketQuote>,
  cashBalance: number
): PortfolioSplit {
  const holdings: { symbol: string; value: number }[] = [];
  let unpricedCount = 0;

  for (const position of positions) {
    const price = quotes.get(position.symbol)?.price;
    if (price == null) {
      unpricedCount++;
      continue;
    }
    const value = position.quantity * price;
    if (value > 0) {
      holdings.push({ symbol: position.symbol, value });
    }
  }

  holdings.sort((a, b) => b.value - a.value || a.symbol.localeCompare(b.symbol));
  const named = holdings.length > MAX_NAMED_HOLDINGS ? holdings.slice(0, MAX_NAMED_HOLDINGS) : holdings;
  const rest = holdings.slice(named.length);
  const cash = Math.max(cashBalance, 0);

  const parts: Omit<SplitSegment, 'percent'>[] = [...named]
    .sort((a, b) => a.symbol.localeCompare(b.symbol))
    .map((holding, index) => ({ key: holding.symbol, label: holding.symbol, value: holding.value, color: SERIES[index] }));
  if (rest.length > 0) {
    parts.push({
      key: 'other',
      label: `Other (${rest.length})`,
      value: rest.reduce((sum, holding) => sum + holding.value, 0),
      color: 'series-other',
    });
  }
  if (cash > 0) {
    parts.push({ key: 'cash', label: 'Cash', value: cash, color: 'series-cash' });
  }

  const total = parts.reduce((sum, part) => sum + part.value, 0);
  return {
    segments: parts.map((part) => ({ ...part, percent: total > 0 ? (part.value * 100) / total : 0 })),
    total,
    unpricedCount,
  };
}
