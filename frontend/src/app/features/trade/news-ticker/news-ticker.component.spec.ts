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
    expect(element().querySelector('.news')?.getAttribute('aria-label')).toBe('Latest news about AMZN');
    const [first] = headlines('.news-card');
    const text = first.querySelector('.headline')!;
    expect(text.textContent).toBe('Amazon opens its <b>AI chips</b> to every developer');
    expect(text.querySelector('b')).toBeNull();
    expect(first.getAttribute('href')).toBe('https://www.benzinga.com/news/1');
    expect(first.getAttribute('target')).toBe('_blank');
    expect(first.getAttribute('rel')).toBe('noopener noreferrer');
    discardPeriodicTasks();
  }));

  it('shows each headline as its own card, with its source and time', fakeAsync(() => {
    create();

    const cards = headlines('.news-card');
    expect(cards).toHaveSize(2);
    expect(cards[0].querySelector('.meta')?.textContent).toContain('benzinga');
    expect(cards[0].querySelector('.meta')?.textContent).toContain('Oct 19');
    expect(cards[1].querySelector('.meta')?.textContent?.trim()).toBe('benzinga');
    discardPeriodicTasks();
  }));

  it('is left out when there is no news', fakeAsync(() => {
    marketData.getNews.and.returnValue(of([]));

    create();

    expect(element().querySelector('.news')).toBeNull();
    discardPeriodicTasks();
  }));

  it('is left out when the news cannot be loaded', fakeAsync(() => {
    marketData.getNews.and.returnValue(throwError(() => new Error('down')));

    create();

    expect(element().querySelector('.news')).toBeNull();
    discardPeriodicTasks();
  }));

  it('refreshes the news, keeping the headlines shown when a refresh fails', fakeAsync(() => {
    create();
    marketData.getNews.and.returnValue(throwError(() => new Error('down')));

    tick(NewsTickerComponent.REFRESH_INTERVAL_MS);
    fixture.detectChanges();

    expect(marketData.getNews).toHaveBeenCalledTimes(2);
    expect(headlines('.news-card')).toHaveSize(2);
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
});
