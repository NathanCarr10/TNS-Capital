import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class SupportWidgetStateService {
  readonly open = signal(false);
}