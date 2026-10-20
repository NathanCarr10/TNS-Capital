import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';

import { DeadLetterMessageDTO, DeadLetterQueueControllerService } from '../../api/trading';
import { readErrorMessage } from '../../core/trading/api-error';

export type DeadLetterStatus = 'PENDING' | 'RESOLVED' | 'IGNORED';
export type DeadLetterFilter = 'ALL' | DeadLetterStatus;

/**
 * Support's dead-letter tickets: orders the Trade Executor could not process. Support replays one to
 * send the order to the broker again, or dismisses it to close the ticket; the order then stays REJECTED.
 */
@Component({
  selector: 'app-dead-letters',
  imports: [CommonModule],
  templateUrl: './dead-letters.component.html',
  styleUrl: './dead-letters.component.scss',
})
export class DeadLettersComponent {
  private readonly dlqApi = inject(DeadLetterQueueControllerService);

  readonly messages = signal<DeadLetterMessageDTO[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly filters: { value: DeadLetterFilter; label: string }[] = [
    { value: 'PENDING', label: 'Pending' },
    { value: 'RESOLVED', label: 'Resolved' },
    { value: 'IGNORED', label: 'Dismissed' },
    { value: 'ALL', label: 'All' },
  ];
  readonly filter = signal<DeadLetterFilter>('PENDING');

  /** The message being replayed or dismissed, so its buttons can show progress. */
  readonly busyId = signal<string | null>(null);
  /** The message whose dismiss form is open, and the notes typed into it. */
  readonly dismissingId = signal<string | null>(null);
  readonly notes = signal('');
  readonly actionMessage = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);

  readonly visibleRows = computed(() => {
    const filter = this.filter();
    return filter === 'ALL' ? this.messages() : this.messages().filter((message) => message.status === filter);
  });

  readonly counts = computed(() => {
    const counts: Record<DeadLetterFilter, number> = { ALL: 0, PENDING: 0, RESOLVED: 0, IGNORED: 0 };
    for (const message of this.messages()) {
      counts.ALL++;
      counts[message.status as DeadLetterStatus]++;
    }
    return counts;
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.dlqApi.getDLQMessages().subscribe({
      next: (messages) => {
        this.messages.set(messages);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.messages.set([]);
        this.loading.set(false);
        this.loadError.set(readErrorMessage(error, 'Failed to load dead letters.'));
      },
    });
  }

  /** Sends the order through the Trade Executor again; the ticket closes as RESOLVED if it works. */
  replay(message: DeadLetterMessageDTO): void {
    if (message.status !== 'PENDING' || this.busyId()) {
      return;
    }
    this.startAction(message.id!);
    this.dlqApi.replayMessage(message.id!).subscribe({
      next: (updated) => {
        this.busyId.set(null);
        this.replaceRow(updated);
        if (updated.status === 'RESOLVED') {
          this.actionMessage.set(`Order ${shortId(message.originalOrderId)} was replayed and the ticket closed.`);
        } else {
          this.actionError.set(`Order ${shortId(message.originalOrderId)} failed again; the ticket stays pending.`);
        }
      },
      error: (error: unknown) => {
        this.busyId.set(null);
        this.actionError.set(readErrorMessage(error, `Order ${shortId(message.originalOrderId)} could not be replayed.`));
      },
    });
  }

  openDismiss(message: DeadLetterMessageDTO): void {
    this.dismissingId.set(message.id!);
    this.notes.set('');
    this.actionMessage.set(null);
    this.actionError.set(null);
  }

  cancelDismiss(): void {
    this.dismissingId.set(null);
  }

  /** Closes the ticket without replaying: the order stays REJECTED. */
  dismiss(message: DeadLetterMessageDTO): void {
    if (message.status !== 'PENDING' || this.busyId()) {
      return;
    }
    const notes = this.notes().trim() || undefined;
    this.startAction(message.id!);
    this.dlqApi.dismissMessage(message.id!, notes).subscribe({
      next: () => {
        this.busyId.set(null);
        this.dismissingId.set(null);
        this.replaceRow({ ...message, status: 'IGNORED', adminNotes: notes, resolvedOn: new Date().toISOString() });
        this.actionMessage.set(`The ticket for order ${shortId(message.originalOrderId)} was dismissed.`);
      },
      error: (error: unknown) => {
        this.busyId.set(null);
        this.actionError.set(readErrorMessage(error, `The ticket for order ${shortId(message.originalOrderId)} could not be dismissed.`));
      },
    });
  }

  statusLabel(status: string | undefined): string {
    switch (status) {
      case 'IGNORED':
        return 'Dismissed';
      case 'RESOLVED':
        return 'Resolved';
      default:
        return 'Pending';
    }
  }

  readonly shortId = shortId;

  private startAction(id: string): void {
    this.busyId.set(id);
    this.actionMessage.set(null);
    this.actionError.set(null);
  }

  private replaceRow(updated: DeadLetterMessageDTO): void {
    this.messages.update((rows) => rows.map((row) => (row.id === updated.id ? updated : row)));
  }
}

/** The first block of a UUID, enough to tell orders apart on screen. */
function shortId(id: string | undefined): string {
  return id ? id.split('-')[0] : '';
}
