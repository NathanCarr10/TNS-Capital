import { Component, ElementRef, HostListener, computed, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';

import { InstrumentResponse } from '../../api/trading';
import { AccountStore } from '../../core/trading/account.store';
import { IconComponent } from '../icon/icon.component';

/** Finds an instrument by symbol or name and opens its trade page; Ctrl+K or Cmd+K focuses it. */
@Component({
  selector: 'app-symbol-search',
  imports: [IconComponent],
  templateUrl: './symbol-search.component.html',
  styleUrl: './symbol-search.component.scss',
})
export class SymbolSearchComponent {
  static readonly MAX_RESULTS = 8;

  private readonly store = inject(AccountStore);
  private readonly router = inject(Router);
  private readonly input = viewChild.required<ElementRef<HTMLInputElement>>('input');

  readonly query = signal('');
  readonly open = signal(false);
  readonly activeIndex = signal(0);
  readonly shortcut = /Mac|iPhone|iPad/.test(navigator.platform) ? '⌘K' : 'Ctrl K';

  /** Symbols starting with the query first, then names containing it. */
  readonly results = computed<InstrumentResponse[]>(() => {
    const query = this.query().trim().toUpperCase();
    if (!query) {
      return [];
    }
    const instruments = this.store.instruments() ?? [];
    const bySymbol = instruments.filter((instrument) => instrument.symbol.startsWith(query));
    bySymbol.sort((a, b) => a.symbol.localeCompare(b.symbol));
    const byName = instruments.filter(
      (instrument) => !instrument.symbol.startsWith(query) && instrument.name.toUpperCase().includes(query)
    );
    return [...bySymbol, ...byName].slice(
      0,
      SymbolSearchComponent.MAX_RESULTS
    );
  });

  @HostListener('document:keydown', ['$event'])
  focusShortcut(event: KeyboardEvent): void {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.input().nativeElement.focus();
    }
  }

  onInput(value: string): void {
    this.query.set(value);
    this.activeIndex.set(0);
    this.open.set(true);
  }

  onKeydown(event: KeyboardEvent): void {
    const count = this.results().length;
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (count > 0) {
        const step = event.key === 'ArrowDown' ? 1 : -1;
        this.open.set(true);
        this.activeIndex.set((this.activeIndex() + step + count) % count);
      }
    } else if (event.key === 'Enter') {
      const choice = this.results()[this.activeIndex()];
      if (choice) {
        event.preventDefault();
        this.choose(choice);
      }
    } else if (event.key === 'Escape') {
      this.close();
      this.input().nativeElement.blur();
    }
  }

  choose(instrument: InstrumentResponse): void {
    void this.router.navigate(['/trade', instrument.symbol]);
    this.close();
    this.query.set('');
    this.input().nativeElement.blur();
  }

  close(): void {
    this.open.set(false);
  }
}
