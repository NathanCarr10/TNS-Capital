/// <reference types="jasmine" />

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/notifications/toast.service';
import { AccountStore } from '../../core/trading/account.store';
import { ACCOUNT, tradingServiceStub } from '../../core/trading/testing/trading-fixtures';
import { TradingService } from '../../core/trading/trading.service';
import { SettingsComponent } from './settings.component';

describe('SettingsComponent', () => {
  let fixture: ComponentFixture<SettingsComponent>;
  let tradingService: jasmine.SpyObj<TradingService>;

  const create = (isSupport = false): HTMLElement => {
    TestBed.configureTestingModule({
      imports: [SettingsComponent],
      providers: [
        provideRouter([]),
        AccountStore,
        { provide: TradingService, useValue: tradingService },
        { provide: AuthService, useValue: { isSupport: () => isSupport } },
      ],
    });
    fixture = TestBed.createComponent(SettingsComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  };

  const click = (element: HTMLElement, selector: string): void => {
    (element.querySelector(selector) as HTMLButtonElement).click();
    fixture.detectChanges();
  };

  const typeName = (element: HTMLElement, value: string): void => {
    const input = element.querySelector('#holder-name') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  const submit = (element: HTMLElement): void => {
    (element.querySelector('.name-form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  };

  beforeEach(() => {
    tradingService = tradingServiceStub();
  });

  it('shows the account number, holder, username, email, status and dates', () => {
    const element = create();
    const text = element.querySelector('.details')!.textContent!;

    expect(text).toContain('ACC-U000011');
    expect(text).toContain('Trader One');
    expect(text).toContain('trader1');
    expect(text).toContain('ada@example.com');
    expect(text).toContain('ACTIVE');
    expect(element.querySelector('.opened-on')?.textContent).toContain('Sep 1, 2026');
    expect(element.querySelector('.last-updated')?.textContent).toContain('Oct 13, 2026');
  });

  it('renames the account holder and shows the new name', () => {
    tradingService.updateAccount.and.returnValue(of({ ...ACCOUNT, holderName: 'Ada Lovelace' }));
    const element = create();
    const toast = spyOn(TestBed.inject(ToastService), 'success');

    click(element, '.edit-name');
    typeName(element, '  Ada Lovelace ');
    submit(element);

    expect(tradingService.updateAccount).toHaveBeenCalledWith(ACCOUNT.id, { holderName: 'Ada Lovelace' });
    expect(element.querySelector('#holder-name')).toBeNull();
    expect(element.querySelector('.holder-name')?.textContent).toContain('Ada Lovelace');
    expect(TestBed.inject(AccountStore).account()?.holderName).toBe('Ada Lovelace');
    expect(toast).toHaveBeenCalledWith('Account holder name updated.');
  });

  it('rejects characters outside the allow-list without calling the API', () => {
    const element = create();

    click(element, '.edit-name');
    typeName(element, '<script>alert(1)</script>');
    submit(element);

    expect(tradingService.updateAccount).not.toHaveBeenCalled();
    expect(element.querySelector('.field-error')?.textContent).toContain("letters, numbers, spaces and . , ' -");
  });

  it('rejects a blank name', () => {
    const element = create();

    click(element, '.edit-name');
    typeName(element, '   ');
    submit(element);

    expect(tradingService.updateAccount).not.toHaveBeenCalled();
    expect(element.querySelector('.field-error')?.textContent).toContain("Enter the account holder's name.");
  });

  it('does not call the API when the name is unchanged', () => {
    const element = create();

    click(element, '.edit-name');
    submit(element);

    expect(tradingService.updateAccount).not.toHaveBeenCalled();
    expect(element.querySelector('#holder-name')).toBeNull();
  });

  it("shows the API's message when the rename fails and keeps the form open", () => {
    tradingService.updateAccount.and.returnValue(
      throwError(() => ({ error: { errorCode: 'VAL-422', message: 'holderName: not allowed', timestamp: '' } }))
    );
    const element = create();

    click(element, '.edit-name');
    typeName(element, 'Ada Lovelace');
    submit(element);

    expect(element.querySelector('.field-error')?.textContent).toContain('holderName: not allowed');
    expect(element.querySelector('#holder-name')).not.toBeNull();
  });

  it('cancelling discards the edit', () => {
    const element = create();

    click(element, '.edit-name');
    typeName(element, 'Someone Else');
    click(element, '.name-form button[type="button"]');

    expect(element.querySelector('.holder-name')?.textContent).toContain('Trader One');
    expect(tradingService.updateAccount).not.toHaveBeenCalled();
  });

  it('tells support staff they have no account of their own', () => {
    tradingService.getAccounts.and.returnValue(of([ACCOUNT]));

    const element = create(true);

    expect(element.querySelector('.note')?.textContent).toContain('Support staff have no trading account');
    expect(element.querySelector('.details')).toBeNull();
  });
});
