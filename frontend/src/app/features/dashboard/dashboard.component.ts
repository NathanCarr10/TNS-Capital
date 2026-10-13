import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { EMPTY, Subscription, forkJoin, of, throwError, timer } from 'rxjs';
import { catchError, finalize, map, switchMap, take, tap, timeout } from 'rxjs/operators';

import { AnalyticsDashboardService } from '../../core/analytics/analytics-dashboard.service';
import { AuthService } from '../../core/auth/auth.service';
import { AdminAccountBarComponent } from './admin-account-bar/admin-account-bar.component';
import { HoldingsComponent } from './holdings/holdings.component';
import { MarketDataService } from './market-data.service';
import { MarketQuotesStore } from './market-quotes.store';
import { MarketWatchComponent } from './market-watch/market-watch.component';
import { PerformanceSummaryComponent } from './performance-summary/performance-summary.component';
import { PortfolioSplitComponent } from './portfolio-split/portfolio-split.component';
import { SideToggleComponent } from './side-toggle/side-toggle.component';
import {
  AccountResponse,
  ArchivedOrderResponse,
  InstrumentResponse,
  OrderAcceptedResponse,
  OrderHistoryRow,
  OrderResponse,
  OrderSide,
  PlaceOrderRequest,
  PlatformErrorResponse,
  PositionResponse,
} from './trading.models';
import { TradingService } from './trading.service';

@Component({
  selector: 'app-dashboard',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    AdminAccountBarComponent,
    HoldingsComponent,
    MarketWatchComponent,
    PerformanceSummaryComponent,
    PortfolioSplitComponent,
    SideToggleComponent,
  ],
  providers: [MarketQuotesStore],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnDestroy {
  private static readonly POLL_INTERVAL_MS = 1500;
  private static readonly POLL_MAX_ATTEMPTS = 40;

  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly tradingService = inject(TradingService);
  private readonly marketDataService = inject(MarketDataService);
  private readonly router = inject(Router);
  private readonly analyticsDashboard = inject(AnalyticsDashboardService);
  private pollingSubscription?: Subscription;

  /** Admins have no account of their own: they pick any account and view it read-only. */
  readonly isAdmin = this.authService.isAdmin();
  readonly adminAccounts = signal<AccountResponse[]>([]);

  readonly account = signal<AccountResponse | null>(null);
  readonly instruments = signal<InstrumentResponse[]>([]);
  readonly orderHistory = signal<OrderHistoryRow[]>([]);
  readonly positions = signal<PositionResponse[] | null>(null);
  readonly positionsError = signal<string | null>(null);
  readonly heldSymbols = computed(() => (this.positions() ?? []).map((position) => position.symbol));
  readonly accountLoading = signal(true);
  readonly instrumentsLoading = signal(true);
  readonly orderHistoryLoading = signal(true);
  readonly orderHistoryError = signal<string | null>(null);
  readonly isSubmitting = signal(false);
  readonly isTracking = signal(false);
  readonly priceLoading = signal(false);
  readonly successMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  readonly orderForm = this.formBuilder.group({
    symbol: this.formBuilder.nonNullable.control('', [
      Validators.required,
      Validators.pattern(/^[A-Z]{1,10}$/),
    ]),
    side: this.formBuilder.nonNullable.control<OrderSide>('BUY', [Validators.required]),
    quantity: this.formBuilder.nonNullable.control(1, [Validators.required, Validators.min(1)]),
    price: this.formBuilder.control<number | null>(null, [Validators.min(0.01)]),
  });

  constructor() {
    if (this.isAdmin) {
      this.loadAdminAccounts();
    } else {
      this.loadAccount();
    }
    this.loadInstruments();
    this.prefillLivePrice();
  }

  openAnalytics(): void {
    this.analyticsDashboard.open();
  }

  /** Admin view: switch to another account. */
  viewAccount(accountId: number): void {
    this.accountLoading.set(true);
    this.errorMessage.set(null);
    this.tradingService.getAccount(accountId).subscribe({
      next: (account) => this.showAccount(account),
      error: (error: unknown) => {
        this.accountLoading.set(false);
        this.errorMessage.set(this.readErrorMessage(error, 'Failed to load account details.'));
      },
    });
  }

  ngOnDestroy(): void {
    this.pollingSubscription?.unsubscribe();
  }

  logout(): void {
    this.pollingSubscription?.unsubscribe();
    this.authService.logout().subscribe(() => {
      void this.router.navigate(['/login']);
    });
  }

  onSubmit(): void {
    if (this.isAdmin) {
      return; // the admin view is read-only
    }
    const loadedAccount = this.account();
    if (!loadedAccount) {
      this.errorMessage.set('Account information is unavailable. Please log in again.');
      return;
    }

    if (this.orderForm.invalid || this.isSubmitting()) {
      this.orderForm.markAllAsTouched();
      return;
    }

    this.successMessage.set(null);
    this.errorMessage.set(null);
    this.isSubmitting.set(true);

    const request = this.buildRequest(loadedAccount.id);

    this.tradingService.placeOrder(request).subscribe({
      next: (accepted: OrderAcceptedResponse) => {
        this.isSubmitting.set(false);
        this.isTracking.set(true);
        const acceptedStatus = accepted.status || 'ACCEPTED';
        this.successMessage.set(`Order ${acceptedStatus}. Tracking orderId ${accepted.orderId}.`);
        this.startPolling(accepted.orderId);
      },
      error: (error: unknown) => {
        this.isSubmitting.set(false);
        this.isTracking.set(false);
        this.errorMessage.set(this.mapImmediateError(error));
      },
    });
  }

  isSubmitDisabled(): boolean {
    return (
      this.orderForm.invalid ||
      this.isSubmitting() ||
      this.isTracking() ||
      this.instrumentsLoading() ||
      this.instruments().length === 0 ||
      this.accountLoading() ||
      !this.account()
    );
  }

  private loadInstruments(): void {
    this.instrumentsLoading.set(true);

    this.tradingService.getInstruments().subscribe({
      next: (instruments) => {
        const tradableInstruments = instruments
          .filter((instrument) => instrument.tradable)
          .sort((a, b) => a.symbol.localeCompare(b.symbol));
        this.instruments.set(tradableInstruments);

        const selected = this.orderForm.controls.symbol.value;
        const selectedExists = tradableInstruments.some((instrument) => instrument.symbol === selected);
        if (!selectedExists) {
          this.orderForm.controls.symbol.setValue('');
        }

        this.instrumentsLoading.set(false);
      },
      error: (error: unknown) => {
        this.instrumentsLoading.set(false);
        this.instruments.set([]);
        this.errorMessage.set(this.readErrorMessage(error, 'Failed to load available instruments.'));
      },
    });
  }

  // Picking a symbol fills Price with its latest quote; the trader can still edit it before submitting
  private prefillLivePrice(): void {
    this.orderForm.controls.symbol.valueChanges
      .pipe(
        switchMap((symbol) => {
          if (!symbol) {
            this.priceLoading.set(false);
            return EMPTY;
          }

          this.priceLoading.set(true);
          return this.marketDataService.getQuotes([symbol]).pipe(
            tap((quotes) => {
              const price = quotes.find((quote) => quote.symbol === symbol)?.price;
              if (price != null) {
                this.orderForm.controls.price.setValue(price);
              }
            }),
            catchError(() => EMPTY),
            finalize(() => this.priceLoading.set(false))
          );
        }),
        takeUntilDestroyed()
      )
      .subscribe();
  }

  private loadAccount(): void {
    this.accountLoading.set(true);
    this.errorMessage.set(null);

    this.tradingService
      .getCurrentUserProfile()
      .pipe(
        switchMap((profile) => {
          if (profile.accountId == null) {
            return throwError(
              () => new Error('No trading account is linked to this user. Please contact support.')
            );
          }

          return this.tradingService.getAccount(profile.accountId);
        })
      )
      .subscribe({
        next: (account) => this.showAccount(account),
        error: (error: unknown) => {
          this.accountLoading.set(false);
          this.errorMessage.set(this.readErrorMessage(error, 'Failed to load account details.'));
        },
      });
  }

  private loadAdminAccounts(): void {
    this.accountLoading.set(true);
    this.tradingService.getAccounts().subscribe({
      next: (accounts) => {
        const sorted = [...accounts].sort((a, b) => a.accountNumber.localeCompare(b.accountNumber));
        this.adminAccounts.set(sorted);
        if (sorted.length === 0) {
          this.accountLoading.set(false);
          this.errorMessage.set('There are no trading accounts yet.');
          return;
        }
        this.showAccount(sorted[0]);
      },
      error: (error: unknown) => {
        this.accountLoading.set(false);
        this.errorMessage.set(this.readErrorMessage(error, 'Failed to load accounts.'));
      },
    });
  }

  private showAccount(account: AccountResponse): void {
    this.account.set(account);
    this.accountLoading.set(false);
    this.loadOrderHistory(account.id);
    this.loadPositions(account.id);
  }

  private loadPositions(accountId: number): void {
    this.tradingService.getAccountPositions(accountId).subscribe({
      next: (positions) => {
        this.positions.set(positions.filter((position) => position.quantity > 0));
        this.positionsError.set(null);
      },
      error: (error: unknown) => {
        this.positions.set([]);
        this.positionsError.set(this.readErrorMessage(error, 'Failed to load positions.'));
      },
    });
  }

  // A fill moves cash and holdings; reload them without the loading state so the panels do not flicker
  private refreshAfterFill(accountId: number): void {
    this.tradingService.getAccount(accountId).subscribe({
      next: (account) => this.account.set(account),
      error: () => undefined,
    });
    this.loadOrderHistory(accountId);
    this.loadPositions(accountId);
  }

  private loadOrderHistory(accountId: number): void {
    this.orderHistoryLoading.set(true);
    this.orderHistoryError.set(null);

    forkJoin({
      activeOrders: this.tradingService.getAccountOrders(accountId),
      archivedOrders: this.tradingService.getAccountOrderHistory(accountId),
    })
      .pipe(
        map(({ activeOrders, archivedOrders }) => {
          const activeRows = activeOrders.map((order) => this.mapActiveOrderToRow(order));
          const archivedRows = archivedOrders.map((order) => this.mapArchivedOrderToRow(order));

          return [...activeRows, ...archivedRows].sort(
            (a, b) => new Date(b.dateTime).getTime() - new Date(a.dateTime).getTime()
          );
        })
      )
      .subscribe({
        next: (rows) => {
          this.orderHistory.set(rows);
          this.orderHistoryLoading.set(false);
        },
        error: (error: unknown) => {
          this.orderHistoryLoading.set(false);
          this.orderHistory.set([]);
          this.orderHistoryError.set(this.readErrorMessage(error, 'Failed to load order history.'));
        },
      });
  }

  private buildRequest(accountId: number): PlaceOrderRequest {
    const formValue = this.orderForm.getRawValue();
    const normalizedSymbol = formValue.symbol.trim().toUpperCase();

    if (formValue.symbol !== normalizedSymbol) {
      this.orderForm.controls.symbol.setValue(normalizedSymbol, { emitEvent: false });
    }

    const request: PlaceOrderRequest = {
      accountId,
      symbol: normalizedSymbol,
      side: formValue.side,
      quantity: formValue.quantity,
      idempotencyKey: this.generateIdempotencyKey(),
    };

    const price = formValue.price;
    if (price != null) {
      request.price = price;
    }

    return request;
  }

  private startPolling(orderId: string): void {
    this.pollingSubscription?.unsubscribe();

    let resolved = false;

    this.pollingSubscription = timer(0, DashboardComponent.POLL_INTERVAL_MS)
      .pipe(
        take(DashboardComponent.POLL_MAX_ATTEMPTS),
        switchMap(() =>
          this.tradingService.getOrder(orderId).pipe(
            timeout(8000),
            catchError((error: HttpErrorResponse) => {
              if (error.status === 404) {
                return of(null);
              }
              return throwError(() => error);
            })
          )
        )
      )
      .subscribe({
        next: (order: OrderResponse | null) => {
          if (!order) {
            return;
          }

          if (order.status === 'FILLED') {
            resolved = true;
            this.isTracking.set(false);
            this.successMessage.set(`Order FILLED. orderId: ${orderId}.`);
            this.errorMessage.set(null);
            const loadedAccount = this.account();
            if (loadedAccount) {
              this.refreshAfterFill(loadedAccount.id);
            }
            this.pollingSubscription?.unsubscribe();
            return;
          }

          if (order.status === 'REJECTED') {
            resolved = true;
            this.isTracking.set(false);
            this.successMessage.set(null);
            this.errorMessage.set(this.mapRejectedError(order.statusReason));
            const loadedAccount = this.account();
            if (loadedAccount) {
              this.loadOrderHistory(loadedAccount.id);
            }
            this.pollingSubscription?.unsubscribe();
          }
        },
        error: (error: unknown) => {
          this.isSubmitting.set(false);
          this.isTracking.set(false);
          this.successMessage.set(null);
          this.errorMessage.set(this.readErrorMessage(error, 'Failed to retrieve final order status.'));
        },
        complete: () => {
          if (!resolved) {
            this.isSubmitting.set(false);
            this.isTracking.set(false);
            this.errorMessage.set('Order accepted but final status is taking too long. Please refresh order history.');
          }
        },
      });
  }

  private mapImmediateError(error: unknown): string {
    const apiError = this.readApiError(error);
    const code = apiError?.errorCode;
    if (code === 'ACC-404') {
      return 'Account not found (ACC-404).';
    }
    if (code === 'ACC-403') {
      return 'Account inactive (ACC-403).';
    }
    if (code === 'INS-404') {
      return 'Instrument not found (INS-404).';
    }
    if (code === 'VAL-422') {
      return 'Invalid input (VAL-422). Check symbol, side, quantity, and price.';
    }

    return this.readErrorMessage(error, 'Order submission failed.');
  }

  private mapRejectedError(statusReason?: string): string {
    const reason = (statusReason || '').toLowerCase();

    if (reason.includes('insufficient funds')) {
      return 'Insufficient funds (ORD-400).';
    }

    if (reason.includes('insufficient holdings') || reason.includes('no position')) {
      return 'Insufficient holdings (ORD-409).';
    }

    return statusReason || 'Order rejected during processing.';
  }

  private generateIdempotencyKey(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }

    return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  private readApiError(error: unknown): PlatformErrorResponse | null {
    if (!error || typeof error !== 'object') {
      return null;
    }

    const httpError = error as { error?: unknown };
    if (!httpError.error || typeof httpError.error !== 'object') {
      return null;
    }

    const api = httpError.error as PlatformErrorResponse;
    return api.errorCode && api.message ? api : null;
  }

  private readErrorMessage(error: unknown, fallback: string): string {
    const apiError = this.readApiError(error);
    return apiError?.message ?? fallback;
  }

  private mapActiveOrderToRow(order: OrderResponse): OrderHistoryRow {
    return {
      id: order.id,
      dateTime: order.createdOn,
      symbol: order.symbol,
      side: order.side,
      quantity: order.quantity,
      price: order.price,
      status: order.status,
    };
  }

  private mapArchivedOrderToRow(order: ArchivedOrderResponse): OrderHistoryRow {
    return {
      id: order.orderId,
      dateTime: order.orderCreatedOn,
      symbol: order.symbol,
      side: order.side,
      quantity: order.quantity,
      price: order.price,
      status: order.status,
    };
  }
}
