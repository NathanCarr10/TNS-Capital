/// <reference types="jasmine" />

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideApiClients } from '../api/api.providers';
import { StrategyLabService } from './strategy-lab.service';

describe('StrategyLabService', () => {
  let service: StrategyLabService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApiClients()],
    });
    service = TestBed.inject(StrategyLabService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lists, gets, creates and deletes strategies at the strategy service', () => {
    service.list().subscribe();
    httpMock.expectOne({ method: 'GET', url: '/api/v1/strategies' }).flush([]);

    service.get(4).subscribe();
    httpMock.expectOne({ method: 'GET', url: '/api/v1/strategies/4' }).flush({ id: 4 });

    service.create({ name: 'MA', symbol: 'SPY', ruleType: 'MA_CROSSOVER', quantity: 1 }).subscribe();
    const create = httpMock.expectOne({ method: 'POST', url: '/api/v1/strategies' });
    expect(create.request.body).toEqual({ name: 'MA', symbol: 'SPY', ruleType: 'MA_CROSSOVER', quantity: 1 });
    create.flush({ id: 5 });

    let deleted = false;
    service.remove(5).subscribe(() => (deleted = true));
    httpMock.expectOne({ method: 'DELETE', url: '/api/v1/strategies/5' }).flush(null, { status: 204, statusText: 'No Content' });
    expect(deleted).toBeTrue();
  });

  it('switches a strategy on and reads its signals and recent activity', () => {
    service.setStatus(4, 'ACTIVE').subscribe();
    const change = httpMock.expectOne({ method: 'PUT', url: '/api/v1/strategies/4/status' });
    expect(change.request.body).toEqual({ status: 'ACTIVE' });
    change.flush({ id: 4, status: 'ACTIVE' });

    service.signals(4).subscribe();
    httpMock.expectOne({ method: 'GET', url: '/api/v1/strategies/4/signals' }).flush([]);

    service.activity(new Date('2026-10-19T14:00:00Z')).subscribe();
    const activity = httpMock.expectOne((req) => req.url === '/api/v1/strategies/activity');
    expect(activity.request.params.get('since')).toBe('2026-10-19T14:00:00.000Z');
    activity.flush([]);
  });

  it("reads the strategies against the trader's own trading", () => {
    service.performance().subscribe();

    httpMock.expectOne({ method: 'GET', url: '/api/v1/strategies/performance' }).flush({ accountId: 11 });
  });

  it('runs a backtest', () => {
    service.backtest({ symbol: 'SPY', ruleType: 'BOLLINGER' }).subscribe();

    const request = httpMock.expectOne({ method: 'POST', url: '/api/v1/strategies/backtests' });
    expect(request.request.body).toEqual({ symbol: 'SPY', ruleType: 'BOLLINGER' });
    request.flush({ symbol: 'SPY' });
  });

  it('starts, reads and stops a replay at the trading API', () => {
    service.startReplay({ symbol: 'SPY', date: '2026-10-16', barsPerSecond: 10 }).subscribe();
    httpMock.expectOne({ method: 'POST', url: '/api/v1/market-data/replays' }).flush({ running: true });

    let current: unknown = 'unset';
    service.currentReplay().subscribe((replay) => (current = replay));
    httpMock.expectOne({ method: 'GET', url: '/api/v1/market-data/replays/current' }).flush(null, { status: 204, statusText: 'No Content' });
    expect(current).toBeNull();

    service.stopReplay().subscribe();
    httpMock.expectOne({ method: 'DELETE', url: '/api/v1/market-data/replays/current' }).flush({ running: false });
  });
});
