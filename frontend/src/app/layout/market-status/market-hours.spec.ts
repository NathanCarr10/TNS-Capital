/// <reference types="jasmine" />

import { nyseStatus } from './market-hours';

describe('nyseStatus', () => {
  it('is open during the regular session on a weekday', () => {
    // 14:30 UTC on Tuesday 16 June 2026 is 10:30 in New York (EDT)
    expect(nyseStatus(new Date('2026-06-16T14:30:05Z'))).toEqual({ open: true, time: '10:30:05' });
  });

  it('opens at 9:30 and closes at 16:00 New York time', () => {
    expect(nyseStatus(new Date('2026-06-16T13:29:59Z')).open).toBeFalse();
    expect(nyseStatus(new Date('2026-06-16T13:30:00Z')).open).toBeTrue();
    expect(nyseStatus(new Date('2026-06-16T19:59:59Z')).open).toBeTrue();
    expect(nyseStatus(new Date('2026-06-16T20:00:00Z')).open).toBeFalse();
  });

  it('follows New York winter time', () => {
    // 14:30 UTC on Tuesday 13 January 2026 is 9:30 in New York (EST)
    expect(nyseStatus(new Date('2026-01-13T14:30:00Z'))).toEqual({ open: true, time: '09:30:00' });
  });

  it('is closed at weekends', () => {
    expect(nyseStatus(new Date('2026-06-20T15:00:00Z')).open).toBeFalse();
  });
});
