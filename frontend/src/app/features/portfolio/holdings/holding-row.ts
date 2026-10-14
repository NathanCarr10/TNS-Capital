import { PositionResponse } from '../../../api/trading';

export interface HoldingRow {
  symbol: string;
  quantity: number;
  averageCost: number;
  /** Quantity at the latest price; null when the symbol has no live price. */
  value: number | null;
  unrealisedReturn: number | null;
  unrealisedReturnPercent: number | null;
}

/** Values a position at the latest price, or leaves it unpriced when there is none. */
export function toHoldingRow(position: PositionResponse, price: number | null | undefined): HoldingRow {
  const cost = position.quantity * position.averageCost;
  const value = price != null ? position.quantity * price : null;
  const unrealisedReturn = value != null ? value - cost : null;
  return {
    symbol: position.symbol,
    quantity: position.quantity,
    averageCost: position.averageCost,
    value,
    unrealisedReturn,
    unrealisedReturnPercent: unrealisedReturn != null && cost > 0 ? (unrealisedReturn * 100) / cost : null,
  };
}
