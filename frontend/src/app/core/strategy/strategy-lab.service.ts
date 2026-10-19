import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import {
  BacktestRequest,
  BacktestResult,
  CreateStrategyRequest,
  SignalResponse,
  StatusChangeRequest,
  StrategiesService,
  StrategyResponse,
} from '../../api/strategy';
import { MarketReplayControllerService, ReplayStatus, StartReplayRequest } from '../../api/trading';

export type RuleType = CreateStrategyRequest.RuleTypeEnum;
export type SwitchStatus = StatusChangeRequest.StatusEnum;

/** Strategy Lab's calls: the strategy service, and the trading API's market replay. */
@Injectable({ providedIn: 'root' })
export class StrategyLabService {
  private readonly strategiesApi = inject(StrategiesService);
  private readonly replayApi = inject(MarketReplayControllerService);

  list(): Observable<StrategyResponse[]> {
    return this.strategiesApi.listStrategies();
  }

  get(id: number): Observable<StrategyResponse> {
    return this.strategiesApi.getStrategy(id);
  }

  create(request: CreateStrategyRequest): Observable<StrategyResponse> {
    return this.strategiesApi.createStrategy(request);
  }

  setStatus(id: number, status: SwitchStatus): Observable<StrategyResponse> {
    return this.strategiesApi.changeStrategyStatus(id, { status });
  }

  remove(id: number): Observable<void> {
    return this.strategiesApi.deleteStrategy(id).pipe(map(() => undefined));
  }

  signals(id: number): Observable<SignalResponse[]> {
    return this.strategiesApi.listStrategySignals(id);
  }

  /** Signals of the caller's strategies since the given time, newest first. */
  activity(since: Date): Observable<SignalResponse[]> {
    return this.strategiesApi.listStrategyActivity(since.toISOString());
  }

  backtest(request: BacktestRequest): Observable<BacktestResult> {
    return this.strategiesApi.runBacktest(request);
  }

  startReplay(request: StartReplayRequest): Observable<ReplayStatus> {
    return this.replayApi.startReplay(request);
  }

  /** The running or last replay; null if none was started since the API started (204). */
  currentReplay(): Observable<ReplayStatus | null> {
    return this.replayApi.currentReplay().pipe(map((status) => status ?? null));
  }

  stopReplay(): Observable<ReplayStatus> {
    return this.replayApi.stopReplay();
  }
}
