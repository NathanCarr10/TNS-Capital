/// <reference types="jasmine" />

import { ComponentFixture, TestBed, fakeAsync, flush } from '@angular/core/testing';

import { ToastService } from '../../core/notifications/toast.service';
import { ToastContainerComponent } from './toast-container.component';

describe('ToastContainerComponent', () => {
  let fixture: ComponentFixture<ToastContainerComponent>;
  let service: ToastService;

  const toastElements = (): HTMLElement[] => Array.from(fixture.nativeElement.querySelectorAll('.toast'));

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ToastContainerComponent] });
    fixture = TestBed.createComponent(ToastContainerComponent);
    service = TestBed.inject(ToastService);
    fixture.detectChanges();
  });

  it('shows each toast with a role screen readers announce', fakeAsync(() => {
    service.success('Order filled');
    service.error('Order rejected');
    fixture.detectChanges();

    const [success, error] = toastElements();
    expect(success.classList).toContain('success');
    expect(success.getAttribute('role')).toBe('status');
    expect(success.textContent).toContain('Order filled');
    expect(error.classList).toContain('error');
    expect(error.getAttribute('role')).toBe('alert');
    flush();
  }));

  it('closes a toast from its dismiss button', fakeAsync(() => {
    service.info('Still processing');
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('button.close') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(toastElements().length).toBe(0);
  }));

  it('pauses a toast while the pointer is on it', fakeAsync(() => {
    spyOn(service, 'pause');
    spyOn(service, 'resume');
    const id = service.success('Order filled');
    fixture.detectChanges();

    toastElements()[0].dispatchEvent(new MouseEvent('mouseenter'));
    toastElements()[0].dispatchEvent(new MouseEvent('mouseleave'));

    expect(service.pause).toHaveBeenCalledWith(id);
    expect(service.resume).toHaveBeenCalledWith(id);
    flush();
  }));
});
