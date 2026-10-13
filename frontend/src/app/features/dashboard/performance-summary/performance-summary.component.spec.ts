/// <reference types="jasmine" />

import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MarketQuotesStore } from '../market-quotes.store';
import { MarketQuote, PositionResponse } from '../../../api/trading';
import { PerformanceSummaryComponent } from './performance-summary.component';

describe('PerformanceSummaryComponent', () => {
  let fixture: ComponentFixture<PerformanceSummaryComponent>;
  let store: Pick<MarketQuotesStore, 'quotes' | 'lastUpdated' | 'errorMessage'>;

  const positions: PositionResponse[] = [
    { accountId: 11, symbol: 'AAPL', quantity: 10, averageCost: 100, marketValue: null },
    { accountId: 11, symbol: 'MSFT', quantity: 5, averageCost: 200, marketValue: null },
  ];

  const quote = (symbol: string, price: number | null, previousClose: number | null): MarketQuote => ({
    symbol,
    price,
    previousClose,
    change: price != null && previousClose != null ? price - previousClose : null,
    changePercent: null,
    tradedAt: null,
  });

  const tile = (selector: string): HTMLElement => fixture.nativeElement.querySelector(selector) as HTMLElement;

  const render = (inputs: { positions: PositionResponse[] | null; cashBalance: number | null; error?: string }) => {
    fixture.componentRef.setInput('positions', inputs.positions);
    fixture.componentRef.setInput('cashBalance', inputs.cashBalance);
    fixture.componentRef.setInput('error', inputs.error ?? null);
    fixture.detectChanges();
  };

  const setQuotes = (...quotes: MarketQuote[]) => {
    store.quotes.set(new Map(quotes.map((q) => [q.symbol, q])));
    store.lastUpdated.set(new Date());
  };

  beforeEach(async () => {
    store = { quotes: signal(new Map()), lastUpdated: signal(null), errorMessage: signal(null) };

    await TestBed.configureTestingModule({
      imports: [PerformanceSummaryComponent],
      providers: [{ provide: MarketQuotesStore, useValue: store }],
    }).compileComponents();

    fixture = TestBed.createComponent(PerformanceSummaryComponent);
  });

  it('shows skeleton tiles until positions and the first quotes arrive', () => {
    render({ positions: null, cashBalance: 1000 });
    expect(fixture.nativeElement.querySelectorAll('.skeleton').length).toBeGreaterThan(0);

    render({ positions, cashBalance: 1000 });
    expect(fixture.nativeElement.querySelectorAll('.skeleton').length).toBeGreaterThan(0);

    setQuotes(quote('AAPL', 120, 110), quote('MSFT', 190, 200));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.skeleton')).toBeNull();
  });

  it('shows the KPIs, green for a gain and red for a loss', () => {
    render({ positions, cashBalance: 1000 });
    setQuotes(quote('AAPL', 120, 110), quote('MSFT', 170, 200));
    fixture.detectChanges();

    // Unrealised: 1200 + 850 - 2000 = +50 (+2.50%); today: 100 - 150 = -50
    expect(tile('.kpi-unrealised').classList).toContain('pnl-up');
    expect(tile('.kpi-unrealised').textContent).toContain('+$50.00');
    expect(tile('.kpi-unrealised').textContent).toContain('(+2.50%)');
    expect(tile('.kpi-today').classList).toContain('pnl-down');
    expect(tile('.kpi-today').textContent).toContain('-$50.00');
    expect(tile('.kpi-equity').textContent).toContain('$3,050.00');
    expect(tile('.kpi-invested').textContent).toContain('67%');
    expect(tile('.kpi-invested').textContent).toContain('$1,000 cash');
  });

  it('updates when new quotes arrive', () => {
    render({ positions, cashBalance: 1000 });
    setQuotes(quote('AAPL', 120, 110), quote('MSFT', 200, 200));
    fixture.detectChanges();
    expect(tile('.kpi-unrealised').textContent).toContain('+$200.00');

    setQuotes(quote('AAPL', 90, 110), quote('MSFT', 200, 200));
    fixture.detectChanges();
    expect(tile('.kpi-unrealised').textContent).toContain('-$100.00');
    expect(tile('.kpi-unrealised').classList).toContain('pnl-down');
  });

  it('notes positions without a live price', () => {
    render({ positions, cashBalance: 1000 });
    setQuotes(quote('AAPL', 120, 110), quote('MSFT', null, null));
    fixture.detectChanges();

    expect(tile('.note').textContent).toContain('1 of 2 positions unpriced');
  });

  it('says when there are no open positions', () => {
    render({ positions: [], cashBalance: 5000 });

    expect(tile('.note').textContent).toContain('No open positions yet');
    expect(tile('.kpi-equity').textContent).toContain('$5,000.00');
  });

  it('shows the error instead of figures when positions fail to load', () => {
    render({ positions: [], cashBalance: 5000, error: 'Failed to load positions.' });

    expect(tile('.note.error').textContent).toContain('Failed to load positions.');
    expect(fixture.nativeElement.querySelector('.kpi-grid')).toBeNull();
  });
});
