import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Observable, tap } from 'rxjs';

import { CurrentUser } from '../../api/auth';
import { AccountResponse, InstrumentResponse, PositionResponse } from '../../api/trading';
import { AuthService } from '../auth/auth.service';
import { readErrorMessage } from './api-error';
import { TradingService } from './trading.service';

/**
 * The signed-in user's account, its open positions and the instrument list,
 * loaded once and shared by every page of the signed-in area.
 *
 * Admins have no account of their own: they pick any account and view it read-only.
 * Provided by the app shell, so it is rebuilt for each sign-in.
 */
@Injectable()
export class AccountStore {
  private readonly tradingService = inject(TradingService);
  private readonly destroyRef = inject(DestroyRef);

  readonly isAdmin = inject(AuthService).isAdmin();

  readonly profile = signal<CurrentUser | null>(null);
  readonly adminAccounts = signal<AccountResponse[]>([]);
  readonly account = signal<AccountResponse | null>(null);
  readonly accountLoading = signal(true);
  readonly accountError = signal<string | null>(null);

  /** Open positions, or null while they are still loading. */
  readonly positions = signal<PositionResponse[] | null>(null);
  readonly positionsError = signal<string | null>(null);
  readonly heldSymbols = computed(() => (this.positions() ?? []).map((position) => position.symbol));

  /** Every instrument, or null while they are still loading. */
  readonly instruments = signal<InstrumentResponse[] | null>(null);
  readonly instrumentsError = signal<string | null>(null);
  readonly tradableInstruments = computed(() =>
    (this.instruments() ?? [])
      .filter((instrument) => instrument.tradable)
      .sort((a, b) => a.symbol.localeCompare(b.symbol))
  );

  /** The symbol last opened on the trade page, which the Trade tab returns to. */
  readonly recentSymbol = signal<string | null>(null);

  constructor() {
    this.loadProfileAndAccount();
    this.loadInstruments();
  }

  /** Admin view: switch to another account. */
  viewAccount(accountId: number): void {
    this.accountLoading.set(true);
    this.accountError.set(null);
    this.subscribe(this.tradingService.getAccount(accountId), {
      next: (account) => this.showAccount(account),
      error: (error) => this.failAccount(error, 'Failed to load account details.'),
    });
  }

  deposit(amount: number): Observable<AccountResponse> {
    const account = this.account();
    if (!account) {
      return EMPTY;
    }
    return this.tradingService.deposit(account.id, { amount }).pipe(tap((updated) => this.account.set(updated)));
  }

  /** Reloads cash and positions after a fill, without the loading state so the panels do not flicker. */
  refresh(): void {
    const account = this.account();
    if (!account) {
      return;
    }
    this.subscribe(this.tradingService.getAccount(account.id), {
      next: (updated) => this.account.set(updated),
      error: () => undefined,
    });
    this.loadPositions(account.id);
  }

  private loadProfileAndAccount(): void {
    this.subscribe(this.tradingService.getCurrentUserProfile(), {
      next: (profile) => {
        this.profile.set(profile);
        if (this.isAdmin) {
          this.loadAdminAccounts();
        } else if (profile.accountId == null) {
          this.failAccount(new Error(), 'No trading account is linked to this user. Please contact support.');
        } else {
          this.viewAccount(profile.accountId);
        }
      },
      error: (error) => this.failAccount(error, 'Failed to load account details.'),
    });
  }

  private loadAdminAccounts(): void {
    this.subscribe(this.tradingService.getAccounts(), {
      next: (accounts) => {
        const sorted = [...accounts].sort((a, b) => a.accountNumber.localeCompare(b.accountNumber));
        this.adminAccounts.set(sorted);
        if (sorted.length === 0) {
          this.failAccount(new Error(), 'There are no trading accounts yet.');
          this.positions.set([]);
          return;
        }
        this.showAccount(sorted[0]);
      },
      error: (error) => this.failAccount(error, 'Failed to load accounts.'),
    });
  }

  private loadInstruments(): void {
    this.subscribe(this.tradingService.getInstruments(), {
      next: (instruments) => this.instruments.set(instruments),
      error: (error) => {
        this.instruments.set([]);
        this.instrumentsError.set(readErrorMessage(error, 'Failed to load available instruments.'));
      },
    });
  }

  private showAccount(account: AccountResponse): void {
    this.account.set(account);
    this.accountLoading.set(false);
    this.loadPositions(account.id);
  }

  private loadPositions(accountId: number): void {
    this.subscribe(this.tradingService.getAccountPositions(accountId), {
      next: (positions) => {
        this.positions.set(positions.filter((position) => position.quantity > 0));
        this.positionsError.set(null);
      },
      error: (error) => {
        this.positions.set([]);
        this.positionsError.set(readErrorMessage(error, 'Failed to load positions.'));
      },
    });
  }

  private failAccount(error: unknown, fallback: string): void {
    this.accountLoading.set(false);
    this.accountError.set(readErrorMessage(error, fallback));
  }

  private subscribe<T>(source: Observable<T>, observer: { next: (value: T) => void; error: (error: unknown) => void }): void {
    source.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(observer);
  }
}

