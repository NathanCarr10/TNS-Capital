import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Subscription, of, throwError, timer } from 'rxjs';
import { catchError, switchMap, take, timeout } from 'rxjs/operators';

import { AuthService } from '../../core/auth/auth.service';
import {
  AccountResponse,
  InstrumentResponse,
  OrderAcceptedResponse,
  OrderResponse,
  OrderSide,
  PlaceOrderRequest,
  PlatformErrorResponse,
} from './trading.models';
import { TradingService } from './trading.service';

@Component({
  selector: 'app-dashboard',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnDestroy {
  private static readonly POLL_INTERVAL_MS = 1500;
  private static readonly POLL_MAX_ATTEMPTS = 40;

  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly tradingService = inject(TradingService);
  private readonly router = inject(Router);
  private pollingSubscription?: Subscription;

  readonly account = signal<AccountResponse | null>(null);
  readonly instruments = signal<InstrumentResponse[]>([]);
  readonly accountLoading = signal(true);
  readonly instrumentsLoading = signal(true);
  readonly isSubmitting = signal(false);
  readonly isTracking = signal(false);
  readonly successMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  readonly orderForm = this.formBuilder.group({
    symbol: this.formBuilder.nonNullable.control('', [
      Validators.required,
      Validators.pattern(/^[A-Z]{1,10}$/),
    ]),
    side: this.formBuilder.nonNullable.control<OrderSide | ''>('', [Validators.required]),
    quantity: this.formBuilder.nonNullable.control(1, [Validators.required, Validators.min(1)]),
    price: this.formBuilder.control<number | null>(null, [Validators.min(0.01)]),
  });

  constructor() {
    this.loadAccount();
    this.loadInstruments();
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
        next: (account) => {
          this.account.set(account);
          this.accountLoading.set(false);
        },
        error: (error: unknown) => {
          this.accountLoading.set(false);
          this.errorMessage.set(this.readErrorMessage(error, 'Failed to load account details.'));
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
      side: formValue.side as OrderSide,
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
            this.pollingSubscription?.unsubscribe();
            return;
          }

          if (order.status === 'REJECTED') {
            resolved = true;
            this.isTracking.set(false);
            this.successMessage.set(null);
            this.errorMessage.set(this.mapRejectedError(order.statusReason));
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
}
