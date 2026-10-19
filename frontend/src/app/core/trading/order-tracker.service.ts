import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, ReplaySubject, of, throwError, timer } from 'rxjs';
import { catchError, filter, switchMap, take, timeout } from 'rxjs/operators';

import { OrderResponse } from '../../api/trading';
import { ToastService } from '../notifications/toast.service';
import { AccountStore } from './account.store';
import { readErrorMessage } from './api-error';
import { TradingService } from './trading.service';

/** How a tracked order ended. */
export type OrderOutcome =
  | { status: 'FILLED'; order: OrderResponse; message: string }
  | { status: 'REJECTED'; order: OrderResponse; message: string }
  | { status: 'CANCELLED'; order: OrderResponse; message: string }
  | { status: 'UNKNOWN'; message: string };

/**
 * Follows placed orders until they fill or are rejected, and announces the result with a toast.
 *
 * Provided by the app shell next to the AccountStore, so tracking carries on when the trader
 * leaves the trade page and stops when they sign out.
 */
@Injectable()
export class OrderTrackerService {
  static readonly POLL_INTERVAL_MS = 1500;
  static readonly POLL_MAX_ATTEMPTS = 40;

  private readonly tradingService = inject(TradingService);
  private readonly store = inject(AccountStore);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  private destroyed = false;

  constructor() {
    this.destroyRef.onDestroy(() => (this.destroyed = true));
  }

  /** Starts following an accepted order. The returned stream emits its outcome once. */
  track(orderId: string): Observable<OrderOutcome> {
    const outcome = new ReplaySubject<OrderOutcome>(1);
    let settled = false;
    const finish = (result: OrderOutcome): void => {
      if (settled) {
        return;
      }
      settled = true;
      this.announce(result);
      outcome.next(result);
      outcome.complete();
    };

    timer(0, OrderTrackerService.POLL_INTERVAL_MS)
      .pipe(
        take(OrderTrackerService.POLL_MAX_ATTEMPTS),
        switchMap(() =>
          this.tradingService.getOrder(orderId).pipe(
            timeout(8000),
            catchError((error: HttpErrorResponse) => (error.status === 404 ? of(null) : throwError(() => error)))
          )
        ),
        filter((order): order is OrderResponse => order != null && order.status !== 'NEW'),
        take(1),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (order) => finish(settledOutcome(order)),
        error: (error: unknown) =>
          finish({
            status: 'UNKNOWN',
            message: readErrorMessage(error, 'Failed to retrieve the final status of the order.'),
          }),
        // Also runs after a settled order (finish ignores it then) and on sign-out (nothing to announce)
        complete: () => {
          if (this.destroyed) {
            return;
          }
          finish({
            status: 'UNKNOWN',
            message: 'The order is still processing. Check the order history for its status.',
          });
        },
      });

    return outcome.asObservable();
  }

  private announce(result: OrderOutcome): void {
    if (result.status === 'FILLED') {
      this.toast.success(result.message);
      this.store.refresh();
    } else if (result.status === 'REJECTED') {
      this.toast.error(result.message);
    } else if (result.status === 'UNKNOWN') {
      this.toast.info(result.message);
    }
  }
}

function settledOutcome(order: OrderResponse): OrderOutcome {
  if (order.status === 'FILLED') {
    return {
      status: 'FILLED',
      order,
      message: `Order filled: ${order.side} ${order.quantity} ${order.symbol} at ${order.price.toFixed(2)}.`,
    };
  }
  if (order.status === 'CANCELLED') {
    // The trader cancelled it from the order history, which already shows it
    return { status: 'CANCELLED', order, message: `Order cancelled: ${order.side} ${order.quantity} ${order.symbol}.` };
  }
  return { status: 'REJECTED', order, message: `${order.side} ${order.symbol} rejected: ${rejectionReason(order.statusReason)}` };
}

function rejectionReason(statusReason?: string): string {
  const reason = (statusReason || '').toLowerCase();
  if (reason.includes('insufficient funds')) {
    return 'Insufficient funds (ORD-400).';
  }
  if (reason.includes('insufficient holdings') || reason.includes('no position')) {
    return 'Insufficient holdings (ORD-409).';
  }
  return statusReason || 'Order rejected during processing.';
}
