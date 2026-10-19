/// <reference types="jasmine" />

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { BacktestResult, StrategyResponse } from '../../api/strategy';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/notifications/toast.service';
import { StrategyLabService } from '../../core/strategy/strategy-lab.service';
import { AccountStore } from '../../core/trading/account.store';
import { tradingServiceStub } from '../../core/trading/testing/trading-fixtures';
import { TradingService } from '../../core/trading/trading.service';
import { StrategyLabComponent } from './strategy-lab.component';

const STRATEGY: StrategyResponse = {
  id: 4,
  accountId: 11,
  name: 'MA 20/50',
  symbol: 'AAPL',
  ruleType: 'MA_CROSSOVER',
  rule: 'SMA 20 / SMA 50 crossover',
  quantity: 3,
  status: 'PAUSED',
  position: 'FLAT',
  barsNeeded: 51,
  market: [],
};

const RESULT: BacktestResult = {
  symbol: 'AAPL',
  rule: 'SMA 20 / SMA 50 crossover',
  from: '2021-10-19',
  to: '2026-10-16',
  initialCash: 10000,
  finalValue: 15800,
  totalReturnPct: 58,
  buyAndHoldReturnPct: 48,
  maxDrawdownPct: 9.1,
  roundTrips: 5,
  winningRoundTrips: 3,
  trades: [],
  equityCurve: [
    { date: '2021-10-19', strategy: 10000, buyAndHold: 10000 },
    { date: '2026-10-16', strategy: 15800, buyAndHold: 14800 },
  ],
};

describe('StrategyLabComponent', () => {
  let fixture: ComponentFixture<StrategyLabComponent>;
  let strategyLab: jasmine.SpyObj<StrategyLabService>;
  let toasts: jasmine.SpyObj<ToastService>;

  const create = (isAdmin = false): HTMLElement => {
    TestBed.configureTestingModule({
      imports: [StrategyLabComponent],
      providers: [
        AccountStore,
        { provide: TradingService, useValue: tradingServiceStub() },
        { provide: AuthService, useValue: { isAdmin: () => isAdmin } },
        { provide: StrategyLabService, useValue: strategyLab },
        { provide: ToastService, useValue: toasts },
      ],
    });
    fixture = TestBed.createComponent(StrategyLabComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  };

  const button = (element: HTMLElement, label: string): HTMLButtonElement =>
    Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.trim() === label) as HTMLButtonElement;

  beforeEach(() => {
    strategyLab = jasmine.createSpyObj<StrategyLabService>('StrategyLabService', [
      'list',
      'create',
      'setStatus',
      'remove',
      'backtest',
      'get',
      'signals',
      'currentReplay',
      'startReplay',
      'stopReplay',
    ]);
    strategyLab.list.and.returnValue(of([STRATEGY]));
    strategyLab.get.and.returnValue(of(STRATEGY));
    strategyLab.signals.and.returnValue(of([]));
    strategyLab.currentReplay.and.returnValue(of(null));
    toasts = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error', 'info']);
  });

  it('backtests the rule and shows the returns against buying and holding', () => {
    strategyLab.backtest.and.returnValue(of(RESULT));
    const element = create();
    fixture.componentInstance.form.patchValue({ symbol: 'AAPL', shortWindow: 10, longWindow: 30 });

    button(element, 'Run backtest').click();
    fixture.detectChanges();

    expect(strategyLab.backtest).toHaveBeenCalledOnceWith({
      symbol: 'AAPL',
      ruleType: 'MA_CROSSOVER',
      params: { shortWindow: 10, longWindow: 30 },
    });
    const stats = element.querySelector('.stats')!.textContent!;
    expect(stats).toContain('+58.00%');
    expect(stats).toContain('+48.00%');
    expect(stats).toContain('3 of 5');
    expect(element.querySelector('app-equity-curve-chart')).not.toBeNull();
  });

  it('sends the Bollinger settings for a Bollinger backtest', () => {
    strategyLab.backtest.and.returnValue(of(RESULT));
    create();
    fixture.componentInstance.form.patchValue({ ruleType: 'BOLLINGER', window: 30, width: 1.5 });

    fixture.componentInstance.runBacktest();

    expect(strategyLab.backtest.calls.mostRecent().args[0].params).toEqual({ window: 30, width: 1.5 });
  });

  it('refuses a short average that is not shorter than the long one', () => {
    const element = create();
    fixture.componentInstance.form.patchValue({ shortWindow: 50, longWindow: 20 });

    button(element, 'Run backtest').click();
    fixture.detectChanges();

    expect(strategyLab.backtest).not.toHaveBeenCalled();
    expect(element.querySelector('[role=alert]')?.textContent).toContain('shorter than the long one');
  });

  it("shows the service's reason when there is no price history", () => {
    strategyLab.backtest.and.returnValue(
      throwError(() => ({ error: { errorCode: 'PRC-404', message: 'No price history yet' } }))
    );
    const element = create();

    fixture.componentInstance.runBacktest();
    fixture.detectChanges();

    expect(element.querySelector('.note.error')?.textContent).toContain('No price history yet');
  });

  it('saves a strategy and selects it', () => {
    const created = { ...STRATEGY, id: 9, name: 'Fast MA' };
    strategyLab.create.and.returnValue(of(created));
    create();
    fixture.componentInstance.form.patchValue({ name: ' Fast MA ', symbol: 'AAPL', quantity: 3 });

    fixture.componentInstance.save();

    expect(strategyLab.create).toHaveBeenCalledOnceWith({
      name: 'Fast MA',
      symbol: 'AAPL',
      ruleType: 'MA_CROSSOVER',
      params: { shortWindow: 20, longWindow: 50 },
      quantity: 3,
    });
    expect(fixture.componentInstance.selectedId()).toBe(9);
    expect(fixture.componentInstance.strategies().length).toBe(2);
  });

  it('refuses a name with characters used by scripts', () => {
    create();
    fixture.componentInstance.form.patchValue({ name: '<script>' });

    fixture.componentInstance.save();

    expect(strategyLab.create).not.toHaveBeenCalled();
  });

  it('switches a strategy on, and shows why another cannot be', () => {
    strategyLab.setStatus.and.returnValues(
      of({ ...STRATEGY, status: 'ACTIVE' }),
      throwError(() => ({ error: { errorCode: 'STR-409', message: 'Only one strategy can be switched on at a time' } }))
    );
    const element = create();

    button(element, 'Switch on').click();
    fixture.detectChanges();

    expect(strategyLab.setStatus).toHaveBeenCalledWith(4, 'ACTIVE');
    expect(element.querySelector('tbody .badge')?.textContent).toContain('On');
    fixture.componentInstance.toggle({ ...STRATEGY, id: 5 });
    expect(toasts.error).toHaveBeenCalledWith('Only one strategy can be switched on at a time');
  });

  it('deletes a strategy', () => {
    strategyLab.remove.and.returnValue(of(undefined));
    const element = create();

    button(element, 'Delete').click();
    fixture.detectChanges();

    expect(strategyLab.remove).toHaveBeenCalledWith(4);
    expect(fixture.componentInstance.strategies()).toEqual([]);
    expect(fixture.componentInstance.selectedId()).toBeNull();
  });

  it('lets admins backtest and view strategies but not save or change them', () => {
    const element = create(true);

    expect(button(element, 'Save strategy')).toBeUndefined();
    expect(button(element, 'Switch on')).toBeUndefined();
    expect(element.textContent).toContain('All accounts');
    fixture.componentInstance.toggle(STRATEGY);
    fixture.componentInstance.save();
    expect(strategyLab.setStatus).not.toHaveBeenCalled();
    expect(strategyLab.create).not.toHaveBeenCalled();
  });

  it('says when strategies cannot be loaded', () => {
    strategyLab.list.and.returnValue(throwError(() => ({ status: 502 })));

    const element = create();

    expect(element.textContent).toContain('Strategies could not be loaded.');
  });
});
