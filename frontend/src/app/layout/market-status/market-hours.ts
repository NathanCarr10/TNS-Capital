export interface MarketStatus {
  open: boolean;
  /** Wall-clock time in New York, HH:mm:ss. */
  time: string;
}

const NEW_YORK = new Intl.DateTimeFormat('en-US', {
  timeZone: 'America/New_York',
  weekday: 'short',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

const OPEN_MINUTE = 9 * 60 + 30;
const CLOSE_MINUTE = 16 * 60;

/**
 * Whether the NYSE regular session (9:30 to 16:00 New York time, Monday to Friday) is open.
 * Exchange holidays and early closes are not known here, so those days show as open.
 */
export function nyseStatus(now: Date): MarketStatus {
  const parts = Object.fromEntries(NEW_YORK.formatToParts(now).map((part) => [part.type, part.value]));
  const minute = Number(parts['hour']) * 60 + Number(parts['minute']);
  const weekday = parts['weekday'] !== 'Sat' && parts['weekday'] !== 'Sun';
  return {
    open: weekday && minute >= OPEN_MINUTE && minute < CLOSE_MINUTE,
    time: `${parts['hour']}:${parts['minute']}:${parts['second']}`,
  };
}
