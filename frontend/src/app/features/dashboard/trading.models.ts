import { PlaceOrderRequest } from '../../api/trading';

// API models come from the generated clients in src/app/api; only view types live here

export type OrderSide = PlaceOrderRequest.SideEnum;

export interface OrderHistoryRow {
  id: string;
  dateTime: string;
  symbol: string;
  side: OrderSide;
  quantity: number;
  price: number;
  status: 'FILLED' | 'REJECTED' | 'CANCELLED' | 'NEW';
}
