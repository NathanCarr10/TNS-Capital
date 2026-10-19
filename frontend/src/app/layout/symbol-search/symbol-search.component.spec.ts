/// <reference types="jasmine" />

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { AccountStore } from '../../core/trading/account.store';
import { tradingServiceStub } from '../../core/trading/testing/trading-fixtures';
import { TradingService } from '../../core/trading/trading.service';
import { SymbolSearchComponent } from './symbol-search.component';

describe('SymbolSearchComponent', () => {
  let fixture: ComponentFixture<SymbolSearchComponent>;
  let router: Router;

  const input = (): HTMLInputElement => fixture.nativeElement.querySelector('input');
  const options = (): string[] =>
    Array.from(fixture.nativeElement.querySelectorAll('[role="option"] .symbol') as NodeListOf<HTMLElement>).map(
      (option) => option.textContent!.trim()
    );

  const type = (value: string): void => {
    input().value = value;
    input().dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  const press = (key: string): void => {
    input().dispatchEvent(new KeyboardEvent('keydown', { key }));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SymbolSearchComponent],
      providers: [
        provideRouter([]),
        AccountStore,
        { provide: TradingService, useValue: tradingServiceStub() },
        { provide: AuthService, useValue: { isSupport: () => false } },
      ],
    }).compileComponents();
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(SymbolSearchComponent);
    fixture.detectChanges();
  });

  it('matches symbols by prefix and companies by name', () => {
    type('a');
    expect(options()).toEqual(['AAPL']);

    type('corp');
    expect(options()).toEqual(['MSFT']);
  });

  it('says when nothing matches', () => {
    type('zzz');

    expect(fixture.nativeElement.querySelector('.empty')?.textContent).toContain('No instrument matches');
  });

  it('opens the highlighted instrument on Enter, moving with the arrow keys', () => {
    type('o');
    // OLD by its symbol, then MSFT by its name, Microsoft Corp.
    expect(options()).toEqual(['OLD', 'MSFT']);

    press('ArrowDown');
    press('Enter');

    expect(router.navigate).toHaveBeenCalledWith(['/trade', 'MSFT']);
  });

  it('opens an instrument when it is clicked and clears the search', () => {
    type('aa');

    (fixture.nativeElement.querySelector('[role="option"]') as HTMLElement).dispatchEvent(new MouseEvent('mousedown'));
    fixture.detectChanges();

    expect(router.navigate).toHaveBeenCalledWith(['/trade', 'AAPL']);
    expect(input().value).toBe('');
  });

  it('focuses on Ctrl+K', () => {
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true }));

    expect(document.activeElement).toBe(input());
  });
});
