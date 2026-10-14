import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EMPTY, forkJoin } from 'rxjs';
import { catchError, distinctUntilChanged, map, switchMap } from 'rxjs/operators';

import { OrderHistoryResponse, OrderResponse } from '../../api/trading';
import { AccountStore } from '../../core/trading/account.store';
import { readErrorMessage } from '../../core/trading/api-error';
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

  readonly visibleRows = computed(() => {
    const filter = this.filter();
    return filter === 'ALL' ? this.orderHistory() : this.orderHistory().filter((row) => row.status === filter);
  });

  constructor() {
    // Reloads when an admin switches to another account
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
