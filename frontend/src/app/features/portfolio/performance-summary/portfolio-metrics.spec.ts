/// <reference types="jasmine" />

import { MarketQuote, PositionResponse } from '../../../api/trading';
import { calculatePortfolioMetrics } from './portfolio-metrics';

describe('calculatePortfolioMetrics', () => {
  const position = (symbol: string, quantity: number, averageCost: number): PositionResponse => ({
    accountId: 11,
    symbol,
    quantity,
    reservedQuantity: 0,
    availableQuantity: quantity,
    averageCost,
    marketValue: null,
  });

  const quote = (symbol: string, price: number | null, previousClose: number | null): MarketQuote => ({
    symbol,
    price,
    previousClose,
    change: price != null && previousClose != null ? price - previousClose : null,
    changePercent: null,
    tradedAt: null,
  });

  const quotesOf = (...quotes: MarketQuote[]) => new Map(quotes.map((q) => [q.symbol, q]));

  it('values positions at the latest prices', () => {
    const metrics = calculatePortfolioMetrics(
      [position('AAPL', 10, 100), position('MSFT', 5, 200)],
      quotesOf(quote('AAPL', 120, 110), quote('MSFT', 190, 200)),
      1000
    );

    // Value 10*120 + 5*190 = 2150; cost 1000 + 1000 = 2000
    expect(metrics.holdingsValue).toBe(2150);
    expect(metrics.unrealisedPnl).toBe(150);
    expect(metrics.unrealisedPnlPercent).toBeCloseTo(7.5, 6);
    // Today 10*10 + 5*-10 = 50 on a previous close value of 1100 + 1000
    expect(metrics.todayPnl).toBe(50);
    expect(metrics.todayPnlPercent).toBeCloseTo(2.381, 3);
    expect(metrics.totalEquity).toBe(3150);
    expect(metrics.investedPercent).toBeCloseTo(68.254, 3);
    expect(metrics.unpricedCount).toBe(0);
  });

  it('leaves unpriced positions out instead of valuing them at zero', () => {
    const metrics = calculatePortfolioMetrics(
      [position('AAPL', 10, 100), position('MSFT', 5, 200)],
      quotesOf(quote('AAPL', 110, 100), quote('MSFT', null, null)),
      0
    );

    expect(metrics.unrealisedPnl).toBe(100);
    expect(metrics.unrealisedPnlPercent).toBeCloseTo(10, 6);
    expect(metrics.holdingsValue).toBe(1100);
    expect(metrics.unpricedCount).toBe(1);
    expect(metrics.positionCount).toBe(2);
  });

  it('reports no P&L when no position is priced', () => {
    const metrics = calculatePortfolioMetrics([position('AAPL', 10, 100)], new Map(), 500);

    expect(metrics.unrealisedPnl).toBeNull();
    expect(metrics.todayPnl).toBeNull();
    expect(metrics.totalEquity).toBe(500);
    expect(metrics.investedPercent).toBe(0);
    expect(metrics.unpricedCount).toBe(1);
  });

  it('reports no daily P&L when the previous close is unknown', () => {
    const metrics = calculatePortfolioMetrics([position('AAPL', 10, 100)], quotesOf(quote('AAPL', 110, null)), 0);

    expect(metrics.unrealisedPnl).toBe(100);
    expect(metrics.todayPnl).toBeNull();
    expect(metrics.todayPnlPercent).toBeNull();
  });

  it('is all cash with no positions', () => {
    const metrics = calculatePortfolioMetrics([], new Map(), 5000);

    expect(metrics.unrealisedPnl).toBeNull();
    expect(metrics.totalEquity).toBe(5000);
    expect(metrics.investedPercent).toBe(0);
    expect(metrics.positionCount).toBe(0);
  });
});
