import { CommonModule } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { map, startWith } from 'rxjs/operators';

import { InstrumentResponse, PlaceOrderRequest, PlaceOrderResponse } from '../../../api/trading';
import { AccountStore } from '../../../core/trading/account.store';
import { readApiError, readErrorMessage } from '../../../core/trading/api-error';
import { OrderTrackerService } from '../../../core/trading/order-tracker.service';
import { OrderSide } from '../../../core/trading/trading.models';
import { TradingService } from '../../../core/trading/trading.service';
import { SideToggleComponent } from './side-toggle/side-toggle.component';

/** Market orders fill at the latest price; limit orders at the price entered. */
export type OrderType = 'MARKET' | 'LIMIT';

const LIMIT_PRICE_VALIDATORS = [Validators.required, Validators.min(0.01), Validators.max(999999.99)];

/** Places a buy or sell order for one instrument; the OrderTrackerService follows it and toasts the result. */
@Component({
  selector: 'app-order-ticket',
  imports: [CommonModule, ReactiveFormsModule, SideToggleComponent],
  templateUrl: './order-ticket.component.html',
  styleUrl: './order-ticket.component.scss',
})
export class OrderTicketComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly tradingService = inject(TradingService);
  private readonly store = inject(AccountStore);
  private readonly orderTracker = inject(OrderTrackerService);
  private trackingSubscription?: Subscription;

  /** The instrument to trade, or null while it is still loading. */
  readonly instrument = input<InstrumentResponse | null>(null);
  readonly symbol = input.required<string>();
  /** The latest price, or null when there is none. */
  readonly livePrice = input<number | null>(null);

  readonly isAdmin = this.store.isAdmin;
  readonly account = this.store.account;
  readonly accountLoading = this.store.accountLoading;

  readonly isSubmitting = signal(false);
  readonly isTracking = signal(false);
  readonly successMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  readonly orderForm = this.formBuilder.group({
    side: this.formBuilder.nonNullable.control<OrderSide>('BUY', [Validators.required]),
    orderType: this.formBuilder.nonNullable.control<OrderType>('LIMIT'),
    quantity: this.formBuilder.nonNullable.control(1, [Validators.required, Validators.min(1), Validators.max(1000000)]),
    price: this.formBuilder.control<number | null>(null, LIMIT_PRICE_VALIDATORS),
  });

  private readonly formValue = toSignal(
    this.orderForm.valueChanges.pipe(
      map(() => this.orderForm.getRawValue()),
      startWith(this.orderForm.getRawValue())
    ),
    { requireSync: true }
  );

  readonly tradable = computed(() => this.instrument()?.tradable ?? false);
  readonly side = computed(() => this.formValue().side);
  readonly isLimit = computed(() => this.formValue().orderType === 'LIMIT');

  /** Shares of this symbol held, which caps a sell. */
  readonly heldQuantity = computed(
    () => this.store.positions()?.find((position) => position.symbol === this.symbol())?.quantity ?? 0
  );

  /** Quantity at the limit price, or at the latest price for a market order. */
  readonly estimatedTotal = computed(() => {
    const { quantity, price } = this.formValue();
    const unitPrice = this.isLimit() ? price : this.livePrice();
    return unitPrice != null && quantity > 0 ? unitPrice * quantity : null;
  });

  /** A warning when the order looks like it will be rejected; the server still decides. */
  readonly shortfall = computed(() => {
    const quantity = this.formValue().quantity;
    if (this.side() === 'SELL') {
      return quantity > this.heldQuantity() ? `You hold ${this.heldQuantity()} ${this.symbol()}.` : null;
    }
    const total = this.estimatedTotal();
    const cash = this.account()?.cashBalance;
    return total != null && cash != null && total > cash ? 'The estimated cost is more than your available cash.' : null;
  });

  constructor() {
    // A new symbol starts a fresh ticket
    effect(() => {
      this.symbol();
      untracked(() => {
        this.orderForm.controls.price.setValue(null);
        this.successMessage.set(null);
        this.errorMessage.set(null);
      });
    });

    // Fills the limit price with the latest quote until the trader enters their own
    effect(() => {
      const livePrice = this.livePrice();
      const price = this.orderForm.controls.price;
      untracked(() => {
        if (livePrice != null && price.value == null) {
          price.setValue(livePrice);
        }
      });
    });

    // A limit order needs a price; a market order sends none
    this.orderForm.controls.orderType.valueChanges.pipe(takeUntilDestroyed()).subscribe((type) => {
      const price = this.orderForm.controls.price;
      price.setValidators(type === 'LIMIT' ? LIMIT_PRICE_VALIDATORS : []);
      price.updateValueAndValidity();
    });

    inject(DestroyRef).onDestroy(() => this.trackingSubscription?.unsubscribe());
  }

  isSubmitDisabled(): boolean {
    return (
      this.isAdmin ||
      !this.tradable() ||
      this.orderForm.invalid ||
      this.isSubmitting() ||
      this.isTracking() ||
      this.accountLoading() ||
      !this.account()
    );
  }

  onSubmit(): void {
    if (this.isAdmin) {
      return; // the admin view is read-only
    }
    const account = this.account();
    if (!account) {
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

    this.tradingService.placeOrder(this.buildRequest(account.id)).subscribe({
      next: (accepted: PlaceOrderResponse) => {
        this.isSubmitting.set(false);
        this.isTracking.set(true);
        this.successMessage.set(`Order ${accepted.status || 'ACCEPTED'}. Tracking order ${accepted.orderId}.`);
        this.follow(accepted.orderId);
      },
      error: (error: unknown) => {
        this.isSubmitting.set(false);
        this.isTracking.set(false);
        this.errorMessage.set(this.mapImmediateError(error));
      },
    });
  }

  private buildRequest(accountId: number): PlaceOrderRequest {
    const { side, quantity, price } = this.orderForm.getRawValue();
    const request: PlaceOrderRequest = {
      accountId,
      symbol: this.symbol(),
      side,
      quantity,
      idempotencyKey: generateIdempotencyKey(),
    };
    if (this.isLimit() && price != null) {
      request.price = price;
    }
    return request;
  }

  private follow(orderId: string): void {
    this.trackingSubscription?.unsubscribe();
    // The tracker announces the result with a toast; the ticket only unlocks for the next order
    this.trackingSubscription = this.orderTracker.track(orderId).subscribe(() => {
      this.isTracking.set(false);
      this.successMessage.set(null);
    });
  }

  private mapImmediateError(error: unknown): string {
    const messages: Record<string, string> = {
      'ACC-404': 'Account not found (ACC-404).',
      'ACC-403': 'Account inactive (ACC-403).',
      'INS-404': 'Instrument not found or not tradable (INS-404).',
      'VAL-422': 'Invalid input (VAL-422). Check quantity and price.',
    };
    const code = readApiError(error)?.errorCode;
    return (code && messages[code]) || readErrorMessage(error, 'Order submission failed.');
  }
}

function generateIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
