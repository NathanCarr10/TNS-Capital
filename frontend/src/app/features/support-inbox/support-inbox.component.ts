import { Component } from '@angular/core';

import { SupportWidgetComponent } from '../../layout/support-widget/support-widget.component';

@Component({
  selector: 'app-support-inbox',
  imports: [SupportWidgetComponent],
  template: `
    <section class="support-inbox-page" aria-labelledby="support-inbox-heading">
      <header>
        <p class="eyebrow">Customer care</p>
        <h1 id="support-inbox-heading">Support Inbox</h1>
        <p>Review customer requests and reply from one place.</p>
      </header>
      <div class="inbox-surface"><app-support-widget [embedded]="true" /></div>
    </section>
  `,
  styles: `
    .support-inbox-page { display: grid; gap: 1.25rem; min-height: calc(100vh - 8rem); }
    header { display: grid; gap: .35rem; }
    .eyebrow { margin: 0; color: var(--accent); font-size: .7rem; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; }
    h1 { margin: 0; font-size: clamp(1.6rem, 3vw, 2.25rem); }
    header p:last-child { margin: 0; color: var(--text-muted); }
    .inbox-surface { min-height: 34rem; overflow: hidden; border: 1px solid var(--border); border-radius: 12px; background: var(--surface); }
    app-support-widget { position: static; display: block; height: 100%; }
  `,
})
export class SupportInboxComponent {}