import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { interval } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { StrategyActivityService } from '../../core/strategy/strategy-activity.service';
import { DeadLetterCountService } from '../../core/support/dead-letter-count.service';
import { SupportService } from '../../core/support/support.service';
import { ThemeService } from '../../core/theme/theme.service';
import { AccountStore } from '../../core/trading/account.store';
import { OrderTrackerService } from '../../core/trading/order-tracker.service';
import { IconComponent, IconName } from '../icon/icon.component';
import { nyseStatus } from '../market-status/market-hours';
import { SymbolSearchComponent } from '../symbol-search/symbol-search.component';
import { SupportWidgetComponent } from '../support-widget/support-widget.component';

interface NavItem {
  label: string;
  link: string;
  icon: IconName;
}

/** The signed-in area: navigation on the left, search and account on top, the page in between. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent, SymbolSearchComponent, SupportWidgetComponent],
  providers: [AccountStore, OrderTrackerService, StrategyActivityService],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly store = inject(AccountStore);
  private readonly supportService = inject(SupportService);
  private readonly deadLetterCount = inject(DeadLetterCountService);
  readonly theme = inject(ThemeService);

  readonly isSupport = this.store.isSupport;
  readonly supportUnreadCount = signal(0);
  readonly pendingDeadLetters = this.deadLetterCount.pending;

  // Support staff neither trade nor have an account of their own, so they get no trade page or
  // settings; they watch strategies read-only, read the newsletter and close dead letters instead
  readonly nav: NavItem[] = this.isSupport
    ? [
        { label: 'Portfolio', link: '/portfolio', icon: 'portfolio' },
        { label: 'Order History', link: '/orders', icon: 'history' },
        { label: 'Strategy Lab', link: '/strategies', icon: 'strategy' },
        { label: 'Newsletter', link: '/newsletter', icon: 'newsletter' },
        { label: 'Dead Letters', link: '/dead-letters', icon: 'inbox' },
        { label: 'Support Inbox', link: '/support', icon: 'help' },
      ]
    : [
        { label: 'Portfolio', link: '/portfolio', icon: 'portfolio' },
        { label: 'Trade', link: '/trade', icon: 'trade' },
        { label: 'Order History', link: '/orders', icon: 'history' },
        { label: 'Strategy Lab', link: '/strategies', icon: 'strategy' },
        { label: 'Newsletter', link: '/newsletter', icon: 'newsletter' },
        { label: 'Settings', link: '/settings', icon: 'settings' },
      ];

  private readonly now = signal(new Date());
  readonly market = computed(() => nyseStatus(this.now()));

  /** The account holder's name, or the support user's username as they have no account. */
  readonly displayName = computed(() =>
    this.isSupport ? (this.store.profile()?.username ?? '') : (this.store.account()?.holderName ?? '')
  );
  readonly subtitle = computed(() =>
    this.isSupport ? 'Support' : (this.store.account()?.accountNumber ?? '')
  );
  readonly initials = computed(() =>
    this.displayName()
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((word) => word[0].toUpperCase())
      .join('')
  );

  constructor() {
    // Traders hear when one of their strategies trades, on any page
    if (!this.isSupport) {
      inject(StrategyActivityService).start();
    } else {
      this.refreshSupportNotifications();
      interval(10000)
        .pipe(takeUntilDestroyed())
        .subscribe(() => this.refreshSupportNotifications());
    }
    interval(1000)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.now.set(new Date()));
  }

  private refreshSupportNotifications(): void {
    this.deadLetterCount.refresh();
    this.supportService.getUnreadNotificationCount().subscribe({
      next: ({ unreadCount }) => this.supportUnreadCount.set(unreadCount),
    });
  }

  logout(): void {
    this.authService.logout().subscribe(() => {
      void this.router.navigate(['/login']);
    });
  }

}
