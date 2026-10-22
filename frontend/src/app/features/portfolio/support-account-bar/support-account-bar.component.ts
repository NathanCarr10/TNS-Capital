import { Component, input, output } from '@angular/core';

import { AccountResponse } from '../../../api/trading';

/** Header control for support staff: choose which trader's account to view. */
@Component({
  selector: 'app-support-account-bar',
  templateUrl: './support-account-bar.component.html',
  styleUrl: './support-account-bar.component.scss',
})
export class SupportAccountBarComponent {
  readonly accounts = input<AccountResponse[]>([]);
  readonly selectedId = input<number | null>(null);

  readonly accountChange = output<number>();

  onSelect(event: Event): void {
    this.accountChange.emit(Number((event.target as HTMLSelectElement).value));
  }
}
