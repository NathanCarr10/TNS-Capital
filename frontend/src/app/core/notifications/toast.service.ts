import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
}

interface Timer {
  handle: ReturnType<typeof setTimeout>;
  startedAt: number;
  remainingMs: number;
}

/** How long each kind stays on screen; errors stay longer so they can be read. */
const DURATION_MS: Record<ToastKind, number> = {
  success: 5000,
  info: 6000,
  error: 10000,
};

/**
 * Short-lived messages shown in a corner of every page, such as an order filling.
 * Each toast closes on its own unless the pointer or focus is on it.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  static readonly MAX_VISIBLE = 4;

  private readonly items = signal<Toast[]>([]);
  private readonly timers = new Map<number, Timer>();
  private nextId = 1;

  readonly toasts = this.items.asReadonly();

  success(message: string): number {
    return this.show('success', message);
  }

  error(message: string): number {
    return this.show('error', message);
  }

  info(message: string): number {
    return this.show('info', message);
  }

  show(kind: ToastKind, message: string, durationMs = DURATION_MS[kind]): number {
    const id = this.nextId++;
    this.items.update((toasts) => [...toasts, { id, kind, message }]);
    this.startTimer(id, durationMs);

    // The oldest toasts make way so the stack never covers the page
    const overflow = this.items().slice(0, -ToastService.MAX_VISIBLE);
    overflow.forEach((toast) => this.dismiss(toast.id));
    return id;
  }

  dismiss(id: number): void {
    const timer = this.timers.get(id);
    if (timer) {
      clearTimeout(timer.handle);
      this.timers.delete(id);
    }
    this.items.update((toasts) => toasts.filter((toast) => toast.id !== id));
  }

  /** Holds a toast open while it is being read. */
  pause(id: number): void {
    const timer = this.timers.get(id);
    if (!timer) {
      return;
    }
    clearTimeout(timer.handle);
    timer.remainingMs = Math.max(0, timer.remainingMs - (Date.now() - timer.startedAt));
  }

  resume(id: number): void {
    const timer = this.timers.get(id);
    if (timer) {
      this.startTimer(id, timer.remainingMs);
    }
  }

  private startTimer(id: number, durationMs: number): void {
    this.timers.set(id, {
      handle: setTimeout(() => this.dismiss(id), durationMs),
      startedAt: Date.now(),
      remainingMs: durationMs,
    });
  }
}
