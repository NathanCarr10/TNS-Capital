import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EMPTY, forkJoin } from 'rxjs';
import { catchError, distinctUntilChanged, map, switchMap } from 'rxjs/operators';

import { OrderHistoryResponse, OrderResponse } from '../../api/trading';
import { AccountStore } from '../../core/trading/account.store';
import { readApiError, readErrorMessage } from '../../core/trading/api-error';
import { OrderHistoryRow } from '../../core/trading/trading.models';
import { TradingService } from '../../core/trading/trading.service';

export type StatusFilter = 'ALL' | OrderHistoryRow['status'];

/** Every order of the account, newest first: open ones and those already archived. */
@Component({
  selector: 'app-order-history',
  imports: [CommonModule, RouterLink],
  templateUrl: './order-history.component.html',
  styleUrl: './order-history.component.scss',
})
export class OrderHistoryComponent {
  private readonly tradingService = inject(TradingService);
  private readonly store = inject(AccountStore);

  readonly account = this.store.account;
  readonly accountError = this.store.accountError;
  /** Support views traders' accounts read-only, so they get no Cancel button. */
  readonly isSupport = this.store.isSupport;

  readonly orderHistory = signal<OrderHistoryRow[]>([]);
  readonly orderHistoryLoading = signal(true);
  readonly orderHistoryError = signal<string | null>(null);

  readonly filters: { value: StatusFilter; label: string }[] = [
    { value: 'ALL', label: 'All' },
    { value: 'NEW', label: 'Open' },
    { value: 'FILLED', label: 'Filled' },
    { value: 'REJECTED', label: 'Rejected' },
    { value: 'CANCELLED', label: 'Cancelled' },
  ];
  readonly filter = signal<StatusFilter>('ALL');
  /** The order being cancelled, so its button can show progress. */
  readonly cancellingId = signal<string | null>(null);
  readonly cancelError = signal<string | null>(null);

  readonly visibleRows = computed(() => {
    const filter = this.filter();
    return filter === 'ALL' ? this.orderHistory() : this.orderHistory().filter((row) => row.status === filter);
  });

  constructor() {
    // Reloads when support switches to another account
    toObservable(this.store.account)
      .pipe(
        map((account) => account?.id ?? null),
        distinctUntilChanged(),
        switchMap((accountId) => {
          if (accountId == null) {
            return EMPTY;
          }
          this.orderHistoryLoading.set(true);
          this.orderHistoryError.set(null);
          return forkJoin({
            active: this.tradingService.getAccountOrders(accountId),
            archived: this.tradingService.getAccountOrderHistory(accountId),
          }).pipe(
            map(({ active, archived }) => toRows(active, archived)),
            catchError((error: unknown) => {
              this.orderHistoryLoading.set(false);
              this.orderHistory.set([]);
              this.orderHistoryError.set(readErrorMessage(error, 'Failed to load order history.'));
              return EMPTY;
            })
          );
        }),
        takeUntilDestroyed()
      )
      .subscribe((rows) => {
        this.orderHistory.set(rows);
        this.orderHistoryLoading.set(false);
      });
  }

  /** Cancels an open order. It may fill first; the API then answers ORD-409. */
  cancel(row: OrderHistoryRow): void {
    if (this.isSupport || row.status !== 'NEW' || this.cancellingId()) {
      return;
    }
    this.cancellingId.set(row.id);
    this.cancelError.set(null);
    this.tradingService.cancelOrder(row.id).subscribe({
      next: (order) => {
        this.cancellingId.set(null);
        this.setStatus(row.id, order.status);
        // Cancelling releases the cash or shares the order reserved
        this.store.refresh();
      },
      error: (error: unknown) => {
        this.cancellingId.set(null);
        this.cancelError.set(
          readApiError(error)?.errorCode === 'ORD-409'
            ? `The ${row.symbol} order could not be cancelled: it has already been filled or cancelled.`
            : readErrorMessage(error, `The ${row.symbol} order could not be cancelled.`)
        );
        // Show what happened to it instead
        this.tradingService.getOrder(row.id).subscribe({
          next: (order) => this.setStatus(row.id, order.status),
          error: () => undefined,
        });
      },
    });
  }

  private setStatus(orderId: string, status: OrderHistoryRow['status']): void {
    this.orderHistory.update((rows) => rows.map((row) => (row.id === orderId ? { ...row, status } : row)));
  }
}

function toRows(active: OrderResponse[], archived: OrderHistoryResponse[]): OrderHistoryRow[] {
  const rows: OrderHistoryRow[] = [
    ...active.map((order) => ({
      id: order.id,
      dateTime: order.createdOn,
      symbol: order.symbol,
      side: order.side,
      quantity: order.quantity,
      price: order.price,
      status: order.status,
    })),
    ...archived.map((order) => ({
      id: order.orderId,
      dateTime: order.orderCreatedOn,
      symbol: order.symbol,
      side: order.side,
      quantity: order.quantity,
      price: order.price,
      status: order.status,
    })),
  ];
  return rows.sort((a, b) => new Date(b.dateTime).getTime() - new Date(a.dateTime).getTime());
}
