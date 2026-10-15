/// <reference types="jasmine" />

import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MarketQuotesStore } from '../../../core/trading/market-quotes.store';
import { MarketQuote, PositionResponse } from '../../../api/trading';
import { PortfolioSplitComponent } from './portfolio-split.component';

describe('PortfolioSplitComponent', () => {
  let fixture: ComponentFixture<PortfolioSplitComponent>;
  let store: Pick<MarketQuotesStore, 'quotes' | 'lastUpdated' | 'errorMessage'>;

  const positions: PositionResponse[] = [
    { accountId: 11, symbol: 'AAPL', quantity: 10, averageCost: 100, marketValue: null },
    { accountId: 11, symbol: 'MSFT', quantity: 5, averageCost: 200, marketValue: null },
  ];

  const quote = (symbol: string, price: number | null): MarketQuote => ({
    symbol,
    price,
    previousClose: null,
    change: null,
    changePercent: null,
    tradedAt: null,
  });

  const render = (inputs: { positions: PositionResponse[] | null; cashBalance: number | null; error?: string }) => {
    fixture.componentRef.setInput('positions', inputs.positions);
    fixture.componentRef.setInput('cashBalance', inputs.cashBalance);
    fixture.componentRef.setInput('error', inputs.error ?? null);
    fixture.detectChanges();
  };

  const setQuotes = (...quotes: MarketQuote[]) => {
    store.quotes.set(new Map(quotes.map((q) => [q.symbol, q])));
    store.lastUpdated.set(new Date());
    fixture.detectChanges();
  };

  const segments = (): HTMLElement[] => Array.from(fixture.nativeElement.querySelectorAll('.segment'));
  const tooltip = (): HTMLElement | null => fixture.nativeElement.querySelector('.split-tooltip');

  beforeEach(async () => {
    store = { quotes: signal(new Map()), lastUpdated: signal(null), errorMessage: signal(null) };

    await TestBed.configureTestingModule({
      imports: [PortfolioSplitComponent],
      providers: [{ provide: MarketQuotesStore, useValue: store }],
    }).compileComponents();

    fixture = TestBed.createComponent(PortfolioSplitComponent);
  });

  it('shows a skeleton bar until positions, cash and the first quotes arrive', () => {
    render({ positions, cashBalance: null });
    expect(fixture.nativeElement.querySelector('.skeleton')).not.toBeNull();

    render({ positions, cashBalance: 1000 });
    expect(fixture.nativeElement.querySelector('.skeleton')).not.toBeNull();

    setQuotes(quote('AAPL', 120), quote('MSFT', 200));
    expect(fixture.nativeElement.querySelector('.skeleton')).toBeNull();
    expect(segments()).toHaveSize(3);
  });

  it('sizes each segment by value and lists every part in the legend', () => {
    render({ positions, cashBalance: 800 });
    setQuotes(quote('AAPL', 120), quote('MSFT', 200));

    expect(segments().map((segment) => segment.style.flexGrow)).toEqual(['1200', '1000', '800']);

    const legend = Array.from(fixture.nativeElement.querySelectorAll('.split-legend li') as NodeListOf<HTMLElement>).map(
      (item) => `${item.querySelector('.legend-label')!.textContent!.trim()} ${item.querySelector('.legend-percent')!.textContent!.trim()}`
    );
    expect(legend).toEqual(['AAPL 40.0%', 'MSFT 33.3%', 'Cash 26.7%']);
  });

  it('shows the symbol, value and share of the portfolio on hover, and hides it on leave', () => {
    render({ positions, cashBalance: 800 });
    setQuotes(quote('AAPL', 120), quote('MSFT', 200));
    expect(tooltip()).toBeNull();

    const msft = segments()[1];
    msft.dispatchEvent(new PointerEvent('pointerenter'));
    fixture.detectChanges();

    const text = tooltip()!.textContent!.replace(/\s+/g, ' ');
    expect(text).toContain('MSFT');
    expect(text).toContain('$1,000.00');
    expect(text).toContain('33.3%');
    expect(msft.classList).toContain('active');

    msft.dispatchEvent(new PointerEvent('pointerleave'));
    fixture.detectChanges();
    expect(tooltip()).toBeNull();
  });

  it('shows the same details on keyboard focus', () => {
    render({ positions, cashBalance: 800 });
    setQuotes(quote('AAPL', 120), quote('MSFT', 200));

    const cash = segments()[2];
    expect(cash.getAttribute('aria-label')).toBe('Cash: $800.00, 26.7% of portfolio');

    cash.focus();
    fixture.detectChanges();
    expect(tooltip()!.textContent).toContain('Cash');

    cash.blur();
    fixture.detectChanges();
    expect(tooltip()).toBeNull();
  });

  it('notes holdings left out because they have no price', () => {
    render({ positions, cashBalance: 800 });
    setQuotes(quote('AAPL', 120), quote('MSFT', null));

    expect(segments()).toHaveSize(2);
    expect(fixture.nativeElement.querySelector('.note')?.textContent).toContain('1 unpriced holding');
  });

  it('shows a note when there is nothing to split', () => {
    render({ positions: [], cashBalance: 0 });

    expect(fixture.nativeElement.querySelector('.split-bar')).toBeNull();
    expect(fixture.nativeElement.querySelector('.note')?.textContent).toContain('Nothing to split yet');
  });

  it('shows the error when positions could not be loaded', () => {
    render({ positions: [], cashBalance: 800, error: 'Failed to load positions.' });

    expect(fixture.nativeElement.querySelector('.note.error')?.textContent).toContain('Failed to load positions.');
    expect(fixture.nativeElement.querySelector('.split-bar')).toBeNull();
  });
});
