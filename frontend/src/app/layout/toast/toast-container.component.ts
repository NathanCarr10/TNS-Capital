import { Component, inject } from '@angular/core';

import { ToastService } from '../../core/notifications/toast.service';
import { IconComponent } from '../icon/icon.component';

/** Shows the ToastService's messages stacked in the corner of every page. */
@Component({
  selector: 'app-toast-container',
  imports: [IconComponent],
  templateUrl: './toast-container.component.html',
  styleUrl: './toast-container.component.scss',
})
export class ToastContainerComponent {
  readonly toast = inject(ToastService);
}
