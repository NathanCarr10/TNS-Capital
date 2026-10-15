/// <reference types="jasmine" />

import { of } from 'rxjs';

import { AccountResponse, InstrumentResponse } from '../../../api/trading';
import { TradingService } from '../trading.service';

// Shared by the specs only; nothing in the app imports this file

export const ACCOUNT: AccountResponse = {
  id: 11,
  accountNumber: 'ACC-U000011',
  holderName: 'Trader One',
  cashBalance: 5000,
  status: 'ACTIVE',
  lastUpdated: '2026-10-13T10:00:00Z',
};

export const INSTRUMENTS: InstrumentResponse[] = [
  { symbol: 'AAPL', name: 'Apple Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { symbol: 'MSFT', name: 'Microsoft Corp.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  { symbol: 'OLD', name: 'Delisted Co.', assetClass: 'EQUITY', currency: 'USD', tradable: false },
];

/** A TradingService spy answering as a trader with account 11, no positions and no orders. */
export function tradingServiceStub(): jasmine.SpyObj<TradingService> {
  const spy = jasmine.createSpyObj<TradingService>('TradingService', [
    'getCurrentUserProfile',
    'getAccounts',
    'getAccount',
    'getInstruments',
    'getAccountPositions',
    'getAccountOrders',
    'getAccountOrderHistory',
    'placeOrder',
    'getOrder',
    'cancelOrder',
    'deposit',
    'updateAccountStatus',
  ]);
  spy.getCurrentUserProfile.and.returnValue(of({ username: 'trader1', roles: ['USER'], accountId: ACCOUNT.id }));
  spy.getAccount.and.returnValue(of(ACCOUNT));
  spy.getInstruments.and.returnValue(of(INSTRUMENTS));
  spy.getAccountPositions.and.returnValue(of([]));
  spy.getAccountOrders.and.returnValue(of([]));
  spy.getAccountOrderHistory.and.returnValue(of([]));
  return spy;
}
