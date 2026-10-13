import { MarketQuote, PositionResponse } from '../trading.models';

export interface PortfolioMetrics {
  /** Latest value minus cost of the priced positions; null when none is priced. */
  unrealisedPnl: number | null;
  unrealisedPnlPercent: number | null;
  /** Move since the previous close of the positions with a known close; null when none has one. */
  todayPnl: number | null;
  todayPnlPercent: number | null;
  holdingsValue: number;
  totalEquity: number;
  /** Share of total equity held in positions; null when equity is not positive. */
  investedPercent: number | null;
  positionCount: number;
  /** Positions without a live price, which are left out of every figure. */
  unpricedCount: number;
}

/**
 * Values open positions at the latest quotes. A position without a live price is
 * counted in unpricedCount rather than valued at zero.
 */
export function calculatePortfolioMetrics(
  positions: PositionResponse[],
  quotes: ReadonlyMap<string, MarketQuote>,
  cashBalance: number
): PortfolioMetrics {
  let holdingsValue = 0;
  let costBasis = 0;
  let todayPnl = 0;
  let previousCloseValue = 0;
  let unpricedCount = 0;

  for (const position of positions) {
    const quote = quotes.get(position.symbol);
    if (quote?.price == null) {
      unpricedCount++;
      continue;
    }
    holdingsValue += position.quantity * quote.price;
    costBasis += position.quantity * position.averageCost;
    if (quote.change != null && quote.previousClose != null) {
      todayPnl += position.quantity * quote.change;
      previousCloseValue += position.quantity * quote.previousClose;
    }
  }

  const pricedCount = positions.length - unpricedCount;
  const unrealisedPnl = pricedCount > 0 ? holdingsValue - costBasis : null;
  const totalEquity = cashBalance + holdingsValue;

  return {
    unrealisedPnl,
    unrealisedPnlPercent: unrealisedPnl != null ? percentOf(unrealisedPnl, costBasis) : null,
    todayPnl: previousCloseValue > 0 ? todayPnl : null,
    todayPnlPercent: percentOf(todayPnl, previousCloseValue),
    holdingsValue,
    totalEquity,
    investedPercent: percentOf(holdingsValue, totalEquity),
    positionCount: positions.length,
    unpricedCount,
  };
}

function percentOf(value: number, base: number): number | null {
  return base > 0 ? (value * 100) / base : null;
}
