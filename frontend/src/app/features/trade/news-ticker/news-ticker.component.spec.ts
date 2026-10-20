/// <reference types="jasmine" />

import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { NewsHeadlineResponse } from '../../../api/trading';
import { MarketDataService } from '../../../core/trading/market-data.service';
import { NewsTickerComponent } from './news-ticker.component';

const HEADLINES: NewsHeadlineResponse[] = [
  {
    headline: 'Amazon opens its <b>AI chips</b> to every developer',
    source: 'benzinga',
    url: 'https://www.benzinga.com/news/1',
    publishedAt: '2026-10-19T13:06:00Z',
  },
  {
    headline: 'Amazon stock edges higher',
    source: 'benzinga',
    url: 'https://www.benzinga.com/news/2',
  },
];

describe('NewsTickerComponent', () => {
  let fixture: ComponentFixture<NewsTickerComponent>;
  let marketData: jasmine.SpyObj<MarketDataService>;

  const element = (): HTMLElement => fixture.nativeElement;
  const headlines = (selector: string): HTMLAnchorElement[] =>
    Array.from(element().querySelectorAll<HTMLAnchorElement>(selector));

  /** Creates the ticker for the symbol and lets its first request complete. */
  const create = (symbol = 'AMZN'): void => {
    fixture = TestBed.createComponent(NewsTickerComponent);
    fixture.componentRef.setInput('symbol', symbol);
    fixture.detectChanges();
    tick(0);
    fixture.detectChanges();
  };

  beforeEach(() => {
    marketData = jasmine.createSpyObj<MarketDataService>('MarketDataService', ['getNews']);
    marketData.getNews.and.returnValue(of(HEADLINES));
    TestBed.configureTestingModule({
      imports: [NewsTickerComponent],
      providers: [{ provide: MarketDataService, useValue: marketData }],
    });
  });

  it('shows the news about the symbol as text, each link opening in a new tab', fakeAsync(() => {
    create();

    expect(marketData.getNews).toHaveBeenCalledWith('AMZN');
    expect(element().querySelector('.news-ticker')?.getAttribute('aria-label')).toBe('Latest news about AMZN');
    const [first] = headlines('li:not(.copy) .headline');
    expect(first.textContent).toBe('Amazon opens its <b>AI chips</b> to every developer');
    expect(first.querySelector('b')).toBeNull();
    expect(first.getAttribute('href')).toBe('https://www.benzinga.com/news/1');
    expect(first.getAttribute('target')).toBe('_blank');
    expect(first.getAttribute('rel')).toBe('noopener noreferrer');
    discardPeriodicTasks();
  }));

  it('repeats the headlines for the loop, hidden from screen readers and the keyboard', fakeAsync(() => {
    create();

    const copies = Array.from(element().querySelectorAll('li.copy'));
    expect(headlines('li:not(.copy) .headline').length).toBe(2);
    expect(copies.length).toBe(2);
    expect(copies.every((copy) => copy.getAttribute('aria-hidden') === 'true')).toBeTrue();
    expect(headlines('li.copy .headline').every((link) => link.getAttribute('tabindex') === '-1')).toBeTrue();
    expect(headlines('li:not(.copy) .headline').some((link) => link.hasAttribute('tabindex'))).toBeFalse();
    discardPeriodicTasks();
  }));

  it('scrolls a longer list for longer, at the same speed', fakeAsync(() => {
    marketData.getNews.and.returnValue(
      of(
        [...HEADLINES, ...HEADLINES, ...HEADLINES].map((item, index) => ({
          ...item,
          url: `${item.url}/${index}`,
        })),
      ),
    );

    create();

    expect(element().querySelector<HTMLElement>('.track')!.style.animationDuration).toBe(
      `${6 * NewsTickerComponent.SECONDS_PER_HEADLINE}s`,
    );
    discardPeriodicTasks();
  }));

  it('is left out when there is no news', fakeAsync(() => {
    marketData.getNews.and.returnValue(of([]));

    create();

    expect(element().querySelector('.news-ticker')).toBeNull();
    discardPeriodicTasks();
  }));

  it('is left out when the news cannot be loaded', fakeAsync(() => {
    marketData.getNews.and.returnValue(throwError(() => new Error('down')));

    create();

    expect(element().querySelector('.news-ticker')).toBeNull();
    discardPeriodicTasks();
  }));

  it('refreshes the news, keeping the headlines shown when a refresh fails', fakeAsync(() => {
    create();
    marketData.getNews.and.returnValue(throwError(() => new Error('down')));

    tick(NewsTickerComponent.REFRESH_INTERVAL_MS);
    fixture.detectChanges();

    expect(marketData.getNews).toHaveBeenCalledTimes(2);
    expect(headlines('li:not(.copy) .headline').length).toBe(2);
    discardPeriodicTasks();
  }));

  it('loads the news of a newly opened symbol', fakeAsync(() => {
    create();

    fixture.componentRef.setInput('symbol', 'TSLA');
    fixture.detectChanges();
    tick(0);

    expect(marketData.getNews).toHaveBeenCalledWith('TSLA');
    discardPeriodicTasks();
  }));

  it('pauses and plays with its button', fakeAsync(() => {
    create();
    const toggle = element().querySelector<HTMLButtonElement>('.toggle')!;

    toggle.click();
    fixture.detectChanges();

    expect(element().querySelector('.news-ticker')!.classList).toContain('paused');
    expect(toggle.getAttribute('aria-pressed')).toBe('true');
    expect(toggle.getAttribute('aria-label')).toBe('Play the news ticker');

    toggle.click();
    fixture.detectChanges();

    expect(element().querySelector('.news-ticker')!.classList).not.toContain('paused');
    expect(toggle.getAttribute('aria-label')).toBe('Pause the news ticker');
    discardPeriodicTasks();
  }));
});
