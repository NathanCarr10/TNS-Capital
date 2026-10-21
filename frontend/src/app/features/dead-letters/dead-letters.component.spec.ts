/// <reference types="jasmine" />

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { DeadLetterMessageDTO, DeadLetterQueueControllerService } from '../../api/trading';
import { DeadLetterCountService } from '../../core/support/dead-letter-count.service';
import { DeadLettersComponent } from './dead-letters.component';

describe('DeadLettersComponent', () => {
  let fixture: ComponentFixture<DeadLettersComponent>;
  let dlqApi: jasmine.SpyObj<DeadLetterQueueControllerService>;

  const PENDING: DeadLetterMessageDTO = {
    id: 'dlq-1',
    originalOrderId: 'aaaa1111-0000-0000-0000-000000000000',
    failureType: 'BrokerUnavailableException',
    failureReason: 'Broker did not answer',
    status: 'PENDING',
    retryCount: 0,
    createdOn: '2026-10-01T10:00:00Z',
  };
  const RESOLVED: DeadLetterMessageDTO = { ...PENDING, id: 'dlq-2', originalOrderId: 'bbbb2222-0000', status: 'RESOLVED' };

  const rows = (): HTMLElement[] =>
    Array.from(fixture.nativeElement.querySelectorAll('tbody tr:not(.dismiss-row)') as NodeListOf<HTMLElement>);
  const button = (label: string): HTMLButtonElement =>
    Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(
      (item) => item.textContent!.trim() === label
    )!;

  beforeEach(() => {
    dlqApi = jasmine.createSpyObj<DeadLetterQueueControllerService>('DeadLetterQueueControllerService', [
      'getDLQMessages',
      'replayMessage',
      'dismissMessage',
    ]);
    dlqApi.getDLQMessages.and.returnValue(of([PENDING, RESOLVED]) as never);

    TestBed.configureTestingModule({
      imports: [DeadLettersComponent],
      providers: [{ provide: DeadLetterQueueControllerService, useValue: dlqApi }],
    });
    fixture = TestBed.createComponent(DeadLettersComponent);
    fixture.detectChanges();
  });

  it('shows the pending tickets first', () => {
    expect(rows()).toHaveSize(1);
    expect(rows()[0].textContent).toContain('aaaa1111');

    button('All 2').click();
    fixture.detectChanges();
    expect(rows()).toHaveSize(2);
  });

  it('dismisses a ticket with notes', () => {
    dlqApi.dismissMessage.and.returnValue(of(undefined) as never);

    button('Dismiss').click();
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('.dismiss-form input') as HTMLInputElement;
    input.value = 'Duplicate order';
    input.dispatchEvent(new Event('input'));
    button('Dismiss ticket').click();
    fixture.detectChanges();

    expect(dlqApi.dismissMessage).toHaveBeenCalledWith('dlq-1', 'Duplicate order');
    expect(rows()).toHaveSize(0);
    expect(fixture.componentInstance.counts().IGNORED).toBe(1);
  });

  it('keeps the nav badge in step with the pending tickets', () => {
    const pending = TestBed.inject(DeadLetterCountService).pending;
    expect(pending()).toBe(1);

    dlqApi.dismissMessage.and.returnValue(of(undefined) as never);
    button('Dismiss').click();
    fixture.detectChanges();
    button('Dismiss ticket').click();
    fixture.detectChanges();

    expect(pending()).toBe(0);
  });

  it('replays a ticket and reports when it fails again', () => {
    dlqApi.replayMessage.and.returnValue(of({ ...PENDING, retryCount: 1 }) as never);

    button('Replay').click();
    fixture.detectChanges();

    expect(dlqApi.replayMessage).toHaveBeenCalledWith('dlq-1');
    expect(fixture.nativeElement.querySelector('.note.error').textContent).toContain('failed again');
  });
});
