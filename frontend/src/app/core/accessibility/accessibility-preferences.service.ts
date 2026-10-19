import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

export type TextSize = 'normal' | 'large';
export type TableDensity = 'comfortable' | 'compact';

export interface AccessibilityPreferences {
  reducedMotion: boolean;
  textSize: TextSize;
  highContrast: boolean;
  tableDensity: TableDensity;
}

const STORAGE_KEY = 'tns-accessibility-preferences';
const DEFAULTS: AccessibilityPreferences = {
  reducedMotion: false,
  textSize: 'normal',
  highContrast: false,
  tableDensity: 'comfortable',
};

@Injectable({ providedIn: 'root' })
export class AccessibilityPreferencesService {
  private readonly document = inject(DOCUMENT);

  readonly preferences = signal<AccessibilityPreferences>(this.initialPreferences());

  constructor() {
    this.apply(this.preferences());
  }

  setReducedMotion(reducedMotion: boolean): void {
    this.update({ reducedMotion });
  }

  setTextSize(textSize: TextSize): void {
    this.update({ textSize });
  }

  setHighContrast(highContrast: boolean): void {
    this.update({ highContrast });
  }

  setTableDensity(tableDensity: TableDensity): void {
    this.update({ tableDensity });
  }

  private update(changes: Partial<AccessibilityPreferences>): void {
    const next = { ...this.preferences(), ...changes };
    this.preferences.set(next);
    this.apply(next);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    } catch {
      // Storage can be blocked; the choice then lasts for this page only.
    }
  }

  private apply(preferences: AccessibilityPreferences): void {
    const root = this.document.documentElement;
    root.dataset['motion'] = preferences.reducedMotion ? 'reduced' : 'normal';
    root.dataset['textSize'] = preferences.textSize;
    root.dataset['contrast'] = preferences.highContrast ? 'high' : 'normal';
    root.dataset['density'] = preferences.tableDensity;
  }

  private initialPreferences(): AccessibilityPreferences {
    try {
      const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}') as Partial<AccessibilityPreferences>;
      return {
        reducedMotion: typeof stored.reducedMotion === 'boolean' ? stored.reducedMotion : this.systemPrefersReducedMotion(),
        textSize: stored.textSize === 'large' ? 'large' : DEFAULTS.textSize,
        highContrast: typeof stored.highContrast === 'boolean' ? stored.highContrast : DEFAULTS.highContrast,
        tableDensity: stored.tableDensity === 'compact' ? 'compact' : DEFAULTS.tableDensity,
      };
    } catch {
      return { ...DEFAULTS, reducedMotion: this.systemPrefersReducedMotion() };
    }
  }

  private systemPrefersReducedMotion(): boolean {
    return this.document.defaultView?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  }
}