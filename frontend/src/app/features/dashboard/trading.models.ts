export interface UserProfileResponse {
  username: string;
  roles: string[];
  accountId: number | null;
}

export interface AccountResponse {
  id: number;
  accountNumber: string;
  holderName: string;
  cashBalance: number;
  status: string;
  lastUpdated: string;
}

export interface InstrumentResponse {
  symbol: string;
  name: string;
  assetClass: string;
  currency: string;
  tradable: boolean;
}

export type OrderSide = 'BUY' | 'SELL';

export interface PlaceOrderRequest {
  accountId: number;
  symbol: string;
  side: OrderSide;
  quantity: number;
  price?: number;
  idempotencyKey: string;
}

export interface OrderAcceptedResponse {
  orderId: string;
  status: string;
  price?: number;
  message?: string;
}

export interface OrderResponse {
  id: string;
  accountId: number;
  symbol: string;
  side: OrderSide;
  quantity: number;
  price: number;
  status: 'NEW' | 'FILLED' | 'REJECTED' | 'CANCELLED';
  createdOn: string;
  statusReason?: string;
}

export interface ArchivedOrderResponse {
  orderId: string;
  accountId: number;
  symbol: string;
  side: OrderSide;
  quantity: number;
  price: number;
  status: 'CANCELLED';
  orderCreatedOn: string;
  deletedOn: string;
}

export interface OrderHistoryRow {
  id: string;
  dateTime: string;
  symbol: string;
  side: OrderSide;
  quantity: number;
  price: number;
  status: 'FILLED' | 'REJECTED' | 'CANCELLED' | 'NEW';
}

export interface PlatformErrorResponse {
  errorCode: string;
  message: string;
  timestamp: string;
}
