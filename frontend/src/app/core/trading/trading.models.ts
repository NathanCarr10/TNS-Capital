import { PlaceOrderRequest, PriceBar as ApiPriceBar } from '../../api/trading';

// API models come from the generated clients in src/app/api; only view types live here

export type OrderSide = PlaceOrderRequest.SideEnum;

export type BarTimeframe = '15Min' | '1Hour' | '1Day';

/** One OHLC bar (candlestick); time is the start of the bar's period. */
export type PriceBar = Required<ApiPriceBar>;

export interface OrderHistoryRow {
  id: string;
  dateTime: string;
  symbol: string;
  side: OrderSide;
  quantity: number;
  price: number;
  status: 'FILLED' | 'REJECTED' | 'CANCELLED' | 'NEW';
}
