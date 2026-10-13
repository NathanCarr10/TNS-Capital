import { Component, input, output } from '@angular/core';

import { AccountResponse } from '../trading.models';

/** Header controls for admins: choose which account to view, or go to the analytics dashboard. */
@Component({
  selector: 'app-admin-account-bar',
  templateUrl: './admin-account-bar.component.html',
  styleUrl: './admin-account-bar.component.scss',
})
export class AdminAccountBarComponent {
  readonly accounts = input<AccountResponse[]>([]);
  readonly selectedId = input<number | null>(null);

  readonly accountChange = output<number>();
  readonly openAnalytics = output<void>();

  onSelect(event: Event): void {
    this.accountChange.emit(Number((event.target as HTMLSelectElement).value));
  }
}
