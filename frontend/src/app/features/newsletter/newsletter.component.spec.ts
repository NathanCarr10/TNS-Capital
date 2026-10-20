/// <reference types="jasmine" />

import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { NewsletterControllerService, NewsletterResponse, NewsletterSummaryResponse } from '../../api/trading';
import { AuthService } from '../../core/auth/auth.service';
import { NewsletterComponent } from './newsletter.component';

const SUMMARIES: NewsletterSummaryResponse[] = [
  { id: 2, weekStart: '2026-10-12', generatedAt: '2026-10-16T21:00:00Z' },
  { id: 1, weekStart: '2026-10-05', generatedAt: '2026-10-09T21:00:00Z' },
];

const ISSUE: NewsletterResponse = {
  id: 2,
  weekStart: '2026-10-12',
  generatedAt: '2026-10-16T21:00:00Z',
  gainers: [
    { symbol: 'INTC', name: 'Intel Corp', startClose: 89.77, endClose: 93.6, changePct: 4.26 },
    { symbol: 'AMZN', name: 'Amazon.com Inc', startClose: 249.92, endClose: 258.17, changePct: 3.3 },
  ],
  losers: [{ symbol: 'META', name: 'Meta Platforms Inc', startClose: 709.42, endClose: 694.82, changePct: -2.06 }],
};

describe('NewsletterComponent', () => {
  let fixture: ComponentFixture<NewsletterComponent>;
  let newsletterApi: jasmine.SpyObj<NewsletterControllerService>;

  const create = ({ id = null as string | null, isSupport = false } = {}): HTMLElement => {
    TestBed.configureTestingModule({
      imports: [NewsletterComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap(id ? { id } : {})) } },
        { provide: NewsletterControllerService, useValue: newsletterApi },
        { provide: AuthService, useValue: { isSupport: () => isSupport } },
      ],
    });
    fixture = TestBed.createComponent(NewsletterComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  };

  const texts = (element: HTMLElement, selector: string): string[] =>
    Array.from(element.querySelectorAll(selector)).map((node) => node.textContent!.replace(/\s+/g, ' ').trim());

  beforeEach(() => {
    newsletterApi = jasmine.createSpyObj<NewsletterControllerService>('NewsletterControllerService', [
      'listIssues',
      'getIssue',
      'previewIssue',
      'publishIssue',
    ]);
    newsletterApi.listIssues.and.returnValue(of(SUMMARIES) as never);
    newsletterApi.getIssue.and.returnValue(of(ISSUE) as never);
  });

  it('opens the newest issue when the URL has no id', () => {
    const element = create();

    expect(newsletterApi.getIssue).toHaveBeenCalledWith(2);
    expect(texts(element, 'header p.muted')[0]).toContain('Week of Oct 12, 2026');
  });

  it('opens the issue in the URL', () => {
    create({ id: '1' });

    expect(newsletterApi.getIssue).toHaveBeenCalledWith(1);
  });

  it('lists gainers and losers with their change', () => {
    const element = create();

    expect(texts(element, '#gainers-title ~ * tbody .symbol, section:nth-of-type(1) tbody .symbol')).toEqual([
      'INTC',
      'AMZN',
    ]);
    const changes = texts(element, 'tbody .change');
    expect(changes).toEqual(['+4.26%', '+3.30%', '-2.06%']);
    expect(element.querySelectorAll('tbody .change-up').length).toBe(2);
    expect(element.querySelectorAll('tbody .change-down').length).toBe(1);
  });

  it('lets the reader switch to a past issue', () => {
    const element = create();
    const router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    const picker = element.querySelector<HTMLSelectElement>('.issue-picker select')!;

    picker.value = '1';
    picker.dispatchEvent(new Event('change'));

    expect(texts(element, '.issue-picker option')).toEqual(['Week of Oct 12, 2026', 'Week of Oct 5, 2026']);
    expect(router.navigate).toHaveBeenCalledWith(['/market-movers', '1']);
  });

  it('explains when nothing has been published yet', () => {
    newsletterApi.listIssues.and.returnValue(of([]) as never);

    const element = create();

    expect(newsletterApi.getIssue).not.toHaveBeenCalled();
    expect(texts(element, '.note')[0]).toContain('No market movers yet');
  });

  it('shows the API message when the issue cannot be loaded', () => {
    newsletterApi.getIssue.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 404,
            error: { errorCode: 'NWS-404', message: 'The requested newsletter issue could not be found' },
          })
      ) as never
    );

    const element = create({ id: '99' });

    expect(texts(element, '.note.error')).toEqual(['The requested newsletter issue could not be found']);
  });

  it('shows traders no preview button', () => {
    const element = create();

    expect(element.querySelector('.header-actions .btn-primary')).toBeNull();
  });

  it("lets support preview this week's issue without publishing it", () => {
    const preview: NewsletterResponse = {
      ...ISSUE,
      id: null,
      weekStart: '2026-10-19',
      gainers: [{ symbol: 'NVDA', name: 'NVIDIA Corp', startClose: 100, endClose: 110, changePct: 10 }],
    };
    newsletterApi.previewIssue.and.returnValue(of(preview) as never);
    const element = create({ isSupport: true });
    const router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);

    element.querySelector<HTMLButtonElement>('.header-actions .btn-primary')!.click();
    fixture.detectChanges();

    expect(texts(element, '.preview-banner strong')).toEqual(['Preview, not published.']);
    expect(texts(element, 'header p.muted')[0]).toContain('Week of Oct 19, 2026 · built');
    expect(texts(element, 'tbody .symbol')[0]).toBe('NVDA');
    expect(element.querySelector('.issue-picker')).toBeNull();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('goes back to the published issue when the preview is closed', () => {
    newsletterApi.previewIssue.and.returnValue(of({ ...ISSUE, id: null, weekStart: '2026-10-19' }) as never);
    const element = create({ isSupport: true });
    element.querySelector<HTMLButtonElement>('.header-actions .btn-primary')!.click();
    fixture.detectChanges();

    element.querySelector<HTMLButtonElement>('.preview-actions .btn:not(.btn-primary)')!.click();
    fixture.detectChanges();

    expect(element.querySelector('.preview-banner')).toBeNull();
    expect(texts(element, 'header p.muted')[0]).toContain('Week of Oct 12, 2026 · published');
  });

  it('shows why the preview failed', () => {
    newsletterApi.previewIssue.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 503,
            error: { errorCode: 'MKT-503', message: 'Market data is unavailable right now; try again later' },
          })
      ) as never
    );
    const element = create({ isSupport: true });

    element.querySelector<HTMLButtonElement>('.header-actions .btn-primary')!.click();
    fixture.detectChanges();

    expect(texts(element, '[role="alert"]')).toEqual(['Market data is unavailable right now; try again later']);
    expect(element.querySelector('.preview-banner')).toBeNull();
  });

  it('lets support publish the preview and opens the published issue', () => {
    newsletterApi.previewIssue.and.returnValue(of({ ...ISSUE, id: null, weekStart: '2026-10-19' }) as never);
    newsletterApi.publishIssue.and.returnValue(of({ ...ISSUE, id: 3, weekStart: '2026-10-19' }) as never);
    const element = create({ isSupport: true });
    const router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    element.querySelector<HTMLButtonElement>('.header-actions .btn-primary')!.click();
    fixture.detectChanges();

    element.querySelector<HTMLButtonElement>('.preview-actions .btn-primary')!.click();
    fixture.detectChanges();

    expect(newsletterApi.publishIssue).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/market-movers', 3]);
    expect(element.querySelector('.preview-banner')).toBeNull();
  });

  it('explains when publishing before Friday is switched off', () => {
    newsletterApi.previewIssue.and.returnValue(of({ ...ISSUE, id: null }) as never);
    newsletterApi.publishIssue.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 403,
            error: { errorCode: 'NWS-403', message: 'Publishing the newsletter before the weekly run is switched off' },
          })
      ) as never
    );
    const element = create({ isSupport: true });
    element.querySelector<HTMLButtonElement>('.header-actions .btn-primary')!.click();
    fixture.detectChanges();

    element.querySelector<HTMLButtonElement>('.preview-actions .btn-primary')!.click();
    fixture.detectChanges();

    expect(texts(element, '[role="alert"]')).toEqual([
      'Publishing before Friday is switched off. Set NEWSLETTER_EARLY_PUBLISH=true for a demo.',
    ]);
    expect(element.querySelector('.preview-banner')).not.toBeNull();
  });
});
