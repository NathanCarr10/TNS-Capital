import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SupportAccountBarComponent } from './support-account-bar.component';

describe('SupportAccountBarComponent', () => {
  let fixture: ComponentFixture<SupportAccountBarComponent>;
  let element: HTMLElement;

  const accounts = [
    { id: 1, accountNumber: 'ACC-1', holderName: 'Ann', cashBalance: 10, status: 'ACTIVE', lastUpdated: '' },
    { id: 2, accountNumber: 'ACC-2', holderName: 'Bob', cashBalance: 20, status: 'ACTIVE', lastUpdated: '' },
  ];

  beforeEach(() => {
    fixture = TestBed.createComponent(SupportAccountBarComponent);
    fixture.componentRef.setInput('accounts', accounts);
    fixture.componentRef.setInput('selectedId', 2);
    fixture.detectChanges();
    element = fixture.nativeElement;
  });

  it('lists the accounts with the selected one chosen', () => {
    const select = element.querySelector('select')!;

    expect(Array.from(select.options).map((option) => option.textContent?.trim())).toEqual([
      'ACC-1 - Ann',
      'ACC-2 - Bob',
    ]);
    expect(select.value).toBe('2');
  });

  it('emits the chosen account id', () => {
    const chosen: number[] = [];
    fixture.componentInstance.accountChange.subscribe((id) => chosen.push(id));

    const select = element.querySelector('select')!;
    select.value = '1';
    select.dispatchEvent(new Event('change'));

    expect(chosen).toEqual([1]);
  });
});
