/// <reference types="jasmine" />

import { TestBed, fakeAsync, flush, tick } from '@angular/core/testing';

import { ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;

  const messages = (): string[] => service.toasts().map((toast) => toast.message);

  beforeEach(() => {
    service = TestBed.inject(ToastService);
  });

  it('shows a toast and closes it after its duration', fakeAsync(() => {
    service.success('Order filled');

    expect(service.toasts()).toEqual([jasmine.objectContaining({ kind: 'success', message: 'Order filled' })]);

    tick(4999);
    expect(messages()).toEqual(['Order filled']);
    tick(1);
    expect(messages()).toEqual([]);
  }));

  it('keeps errors on screen longer than successes', fakeAsync(() => {
    service.success('Filled');
    service.error('Rejected');

    tick(5000);

    expect(messages()).toEqual(['Rejected']);
    flush();
  }));

  it('dismisses a toast on request', fakeAsync(() => {
    const id = service.info('Still processing');

    service.dismiss(id);

    expect(messages()).toEqual([]);
  }));

  it('holds a toast open while paused and finishes its time on resume', fakeAsync(() => {
    const id = service.success('Order filled');
    tick(3000);

    service.pause(id);
    tick(10000);
    expect(messages()).toEqual(['Order filled']);

    service.resume(id);
    tick(1999);
    expect(messages()).toEqual(['Order filled']);
    tick(1);
    expect(messages()).toEqual([]);
  }));

  it('drops the oldest toasts beyond the visible limit', fakeAsync(() => {
    for (let i = 1; i <= ToastService.MAX_VISIBLE + 2; i++) {
      service.info(`Toast ${i}`);
    }

    expect(messages()).toEqual(['Toast 3', 'Toast 4', 'Toast 5', 'Toast 6']);
    flush();
  }));
});
