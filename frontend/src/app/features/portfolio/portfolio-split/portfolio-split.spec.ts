/// <reference types="jasmine" />

import { MarketQuote, PositionResponse } from '../../../api/trading';
import { calculatePortfolioSplit } from './portfolio-split';

describe('calculatePortfolioSplit', () => {
  const position = (symbol: string, quantity: number): PositionResponse => ({
    accountId: 11,
    symbol,
    quantity,
    reservedQuantity: 0,
    availableQuantity: quantity,
    averageCost: 1,
    marketValue: null,
  });

  const quotes = (prices: Record<string, number | null>): ReadonlyMap<string, MarketQuote> =>
    new Map(
      Object.entries(prices).map(([symbol, price]) => [
        symbol,
        { symbol, price, previousClose: null, change: null, changePercent: null, tradedAt: null },
      ])
    );

  it('splits holdings and cash by value, listing holdings by symbol with cash last', () => {
    const split = calculatePortfolioSplit(
      [position('MSFT', 2), position('AAPL', 1)],
      quotes({ AAPL: 100, MSFT: 150 }),
      500
    );

    expect(split.total).toBe(900);
    expect(split.segments.map(({ key, value, color }) => ({ key, value, color }))).toEqual([
      { key: 'AAPL', value: 100, color: 'series-1' },
      { key: 'MSFT', value: 300, color: 'series-2' },
      { key: 'cash', value: 500, color: 'series-cash' },
    ]);
    expect(split.segments.map((segment) => segment.percent.toFixed(2))).toEqual(['11.11', '33.33', '55.56']);
  });

  it('keeps the four largest holdings and folds the rest into Other', () => {
    const split = calculatePortfolioSplit(
      ['A', 'B', 'C', 'D', 'E', 'F'].map((symbol) => position(symbol, 1)),
      quotes({ A: 10, B: 60, C: 50, D: 40, E: 30, F: 20 }),
      0
    );

    expect(split.segments.map((segment) => segment.key)).toEqual(['B', 'C', 'D', 'E', 'other']);
    expect(split.segments[4]).toEqual(
      jasmine.objectContaining({ label: 'Other (2)', value: 30, color: 'series-other' })
    );
  });

  it('leaves out unpriced holdings and cash that is not positive', () => {
    const split = calculatePortfolioSplit(
      [position('AAPL', 1), position('MSFT', 1)],
      quotes({ AAPL: 100, MSFT: null }),
      -50
    );

    expect(split.segments.map((segment) => segment.key)).toEqual(['AAPL']);
    expect(split.segments[0].percent).toBe(100);
    expect(split.unpricedCount).toBe(1);
  });

  it('is empty when there is nothing to split', () => {
    const split = calculatePortfolioSplit([], new Map(), 0);

    expect(split.segments).toEqual([]);
    expect(split.total).toBe(0);
  });
});
