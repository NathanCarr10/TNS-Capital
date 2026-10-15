/// <reference types="jasmine" />

import { PositionResponse } from '../../../api/trading';
import { PriceBar } from '../../../core/trading/trading.models';
import { buildValueSeries } from './portfolio-value';

describe('buildValueSeries', () => {
  const position = (symbol: string, quantity: number): PositionResponse => ({
    accountId: 11,
    symbol,
    quantity,
    averageCost: 1,
    marketValue: null,
  });
  const bar = (day: number, close: number): PriceBar => ({
    time: `2026-10-0${day}T04:00:00Z`,
    open: close,
    high: close,
    low: close,
    close,
    volume: 1,
  });

  it('values today\'s quantities at each close, plus cash', () => {
    const series = buildValueSeries(
      [position('AAPL', 10), position('MSFT', 2)],
      new Map([
        ['AAPL', [bar(1, 100), bar(2, 110)]],
        ['MSFT', [bar(1, 50), bar(2, 40)]],
      ]),
      500
    );

    expect(series).toEqual([
      { time: '2026-10-01T04:00:00Z', value: 500 + 1000 + 100 },
      { time: '2026-10-02T04:00:00Z', value: 500 + 1100 + 80 },
    ]);
  });

  it('starts once every holding has a price and carries a missing bar forward', () => {
    const series = buildValueSeries(
      [position('AAPL', 1), position('NEW', 1)],
      new Map([
        ['AAPL', [bar(1, 10), bar(2, 11), bar(3, 12)]],
        // Listed on day 2 and no bar on day 3
        ['NEW', [bar(2, 5)]],
      ]),
      0
    );

    expect(series.map((point) => point.value)).toEqual([16, 17]);
  });

  it('leaves out holdings without any bars', () => {
    const series = buildValueSeries([position('AAPL', 1), position('GONE', 3)], new Map([['AAPL', [bar(1, 10)]]]), 0);

    expect(series).toEqual([{ time: '2026-10-01T04:00:00Z', value: 10 }]);
  });

  it('is empty with no priced holdings', () => {
    expect(buildValueSeries([], new Map(), 1000)).toEqual([]);
  });
});
