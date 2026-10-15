import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { interval } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { ThemeService } from '../../core/theme/theme.service';
import { AccountStore } from '../../core/trading/account.store';
import { IconComponent, IconName } from '../icon/icon.component';
import { nyseStatus } from '../market-status/market-hours';
import { SymbolSearchComponent } from '../symbol-search/symbol-search.component';

interface NavItem {
  label: string;
  link: string;
  icon: IconName;
}

/** The signed-in area: navigation on the left, search and account on top, the page in between. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent, SymbolSearchComponent],
  providers: [AccountStore],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly store = inject(AccountStore);
  readonly theme = inject(ThemeService);

  readonly nav: NavItem[] = [
    { label: 'Portfolio', link: '/portfolio', icon: 'portfolio' },
    { label: 'Trade', link: '/trade', icon: 'trade' },
    { label: 'Order History', link: '/orders', icon: 'history' },
  ];

  private readonly now = signal(new Date());
  readonly market = computed(() => nyseStatus(this.now()));

  readonly isAdmin = this.store.isAdmin;

  /** The account holder's name, or the admin's username as admins have no account. */
  readonly displayName = computed(() =>
    this.isAdmin ? (this.store.profile()?.username ?? '') : (this.store.account()?.holderName ?? '')
  );
  readonly subtitle = computed(() =>
    this.isAdmin ? 'Administrator' : (this.store.account()?.accountNumber ?? '')
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
    interval(1000)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.now.set(new Date()));
  }

  logout(): void {
    this.authService.logout().subscribe(() => {
      void this.router.navigate(['/login']);
    });
  }
}
