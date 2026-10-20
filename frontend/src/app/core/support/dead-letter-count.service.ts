import { Injectable, inject, signal } from '@angular/core';

import { DeadLetterQueueControllerService } from '../../api/trading';

/** How many dead letters are still pending, for support's nav badge. */
@Injectable({ providedIn: 'root' })
export class DeadLetterCountService {
  private readonly dlqApi = inject(DeadLetterQueueControllerService);

  readonly pending = signal(0);

  refresh(): void {
    this.dlqApi.getDLQStatistics().subscribe({
      next: ({ pendingMessages }) => this.pending.set(pendingMessages ?? 0),
      // Keep the last count; the next poll tries again
      error: () => undefined,
    });
  }
}
