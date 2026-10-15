/// <reference types="jasmine" />

import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { AccountStore } from './account.store';
import { ACCOUNT, tradingServiceStub } from './testing/trading-fixtures';
import { TradingService } from './trading.service';

describe('AccountStore', () => {
  let tradingService: jasmine.SpyObj<TradingService>;
  let isAdmin: boolean;

  const createStore = (): AccountStore => {
    TestBed.configureTestingModule({
      providers: [
        AccountStore,
        { provide: TradingService, useValue: tradingService },
        { provide: AuthService, useValue: { isAdmin: () => isAdmin } },
      ],
    });
    return TestBed.inject(AccountStore);
  };

  beforeEach(() => {
    tradingService = tradingServiceStub();
    isAdmin = false;
  });

  it("loads the trader's account, open positions and instruments", () => {
    tradingService.getAccountPositions.and.returnValue(
      of([
        { accountId: 11, symbol: 'AAPL', quantity: 3, averageCost: 100, marketValue: null },
        { accountId: 11, symbol: 'MSFT', quantity: 0, averageCost: 200, marketValue: null },
      ])
    );

    const store = createStore();

    expect(tradingService.getAccount).toHaveBeenCalledWith(ACCOUNT.id);
    expect(store.account()).toEqual(ACCOUNT);
    expect(store.accountLoading()).toBeFalse();
    // Closed positions are left out
    expect(store.heldSymbols()).toEqual(['AAPL']);
    expect(store.tradableInstruments().map((instrument) => instrument.symbol)).toEqual(['AAPL', 'MSFT']);
    expect(tradingService.getAccounts).not.toHaveBeenCalled();
  });

  it('says so when no account is linked to the user', () => {
    tradingService.getCurrentUserProfile.and.returnValue(of({ username: 'nobody', roles: ['USER'], accountId: null }));

    const store = createStore();

    expect(store.accountError()).toContain('No trading account is linked');
    expect(store.accountLoading()).toBeFalse();
  });

  it('reports why positions could not be loaded', () => {
    tradingService.getAccountPositions.and.returnValue(
      throwError(() => ({ error: { errorCode: 'SYS-500', message: 'Positions are down' } }))
    );

    const store = createStore();

    expect(store.positions()).toEqual([]);
    expect(store.positionsError()).toBe('Positions are down');
  });

  it('reloads cash and positions after a fill without showing the loading state', () => {
    const store = createStore();
    tradingService.getAccount.and.returnValue(of({ ...ACCOUNT, cashBalance: 4000 }));

    store.refresh();

    expect(store.account()?.cashBalance).toBe(4000);
    expect(store.accountLoading()).toBeFalse();
    expect(tradingService.getAccountPositions).toHaveBeenCalledTimes(2);
  });

  describe('for an admin', () => {
    const other = { ...ACCOUNT, id: 12, accountNumber: 'ACC-U000012', holderName: 'Trader Two' };

    beforeEach(() => {
      isAdmin = true;
      tradingService.getCurrentUserProfile.and.returnValue(of({ username: 'admin', roles: ['ADMIN'], accountId: null }));
    });

    it('lists every account by number and shows the first one', () => {
      tradingService.getAccounts.and.returnValue(of([other, ACCOUNT]));

      const store = createStore();

      expect(store.adminAccounts().map((account) => account.id)).toEqual([11, 12]);
      expect(store.account()?.id).toBe(ACCOUNT.id);
      expect(tradingService.getAccountPositions).toHaveBeenCalledWith(ACCOUNT.id);
    });

    it('switches to the chosen account', () => {
      tradingService.getAccounts.and.returnValue(of([ACCOUNT, other]));
      tradingService.getAccount.and.returnValue(of(other));
      const store = createStore();

      store.viewAccount(other.id);

      expect(store.account()?.holderName).toBe('Trader Two');
      expect(tradingService.getAccountPositions).toHaveBeenCalledWith(other.id);
    });

    it('says so when there are no accounts', () => {
      tradingService.getAccounts.and.returnValue(of([]));

      const store = createStore();

      expect(store.accountError()).toBe('There are no trading accounts yet.');
    });
  });
});
