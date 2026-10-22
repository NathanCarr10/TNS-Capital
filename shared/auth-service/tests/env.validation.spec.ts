import { validateEnv } from '../src/config/env.validation';

describe('validateEnv', () => {
  const SECRET = 'a'.repeat(64);

  it('accepts a real secret, with or without an admin password', () => {
    expect(() => validateEnv({ JWT_SECRET: SECRET })).not.toThrow();
    expect(() => validateEnv({ JWT_SECRET: SECRET, ADMIN_PASSWORD: 'a-strong-admin-password' })).not.toThrow();
  });

  it('rejects a missing or short JWT_SECRET', () => {
    expect(() => validateEnv({})).toThrow('JWT_SECRET is not set');
    expect(() => validateEnv({ JWT_SECRET: 'short' })).toThrow('at least 32 bytes');
  });

  it("rejects the .env.example placeholder, which passes the length check (A02-5)", () => {
    expect(() => validateEnv({ JWT_SECRET: 'change-me-to-a-random-value-of-at-least-32-bytes' })).toThrow(
      'placeholder',
    );
  });

  it.each([
    ['too short', 'short-pass', 'at least 12 characters'],
    ['the old seeded default', 'adminPassword', 'placeholder or default'],
    ['a placeholder', 'change-me-admin-password', 'placeholder'],
  ])('rejects an ADMIN_PASSWORD that is %s', (_case, password, message) => {
    expect(() => validateEnv({ JWT_SECRET: SECRET, ADMIN_PASSWORD: password })).toThrow(message);
  });

  it('accepts no STRATEGY_CLIENT_SECRET, which leaves the strategy service without a login', () => {
    expect(() => validateEnv({ JWT_SECRET: SECRET, STRATEGY_CLIENT_SECRET: '' })).not.toThrow();
    expect(() => validateEnv({ JWT_SECRET: SECRET, STRATEGY_CLIENT_SECRET: 'b'.repeat(32) })).not.toThrow();
  });

  it.each([
    ['too short', 'short-secret', 'at least 32 characters'],
    ['a placeholder', 'change-me-to-a-random-value-of-at-least-32-bytes', 'placeholder'],
  ])('rejects a STRATEGY_CLIENT_SECRET that is %s', (_case, secret, message) => {
    expect(() => validateEnv({ JWT_SECRET: SECRET, STRATEGY_CLIENT_SECRET: secret })).toThrow(message);
  });

  it.each([
    ['too short', 'short-pass', 'SUPPORT_PASSWORD must be at least 12 characters'],
    ['a placeholder', 'change-me-support-password', 'SUPPORT_PASSWORD is a published placeholder'],
  ])('rejects a SUPPORT_PASSWORD that is %s', (_case, password, message) => {
    expect(() => validateEnv({ JWT_SECRET: SECRET, SUPPORT_PASSWORD: password })).toThrow(message);
  });

  it('accepts different admin and support passwords, but not the same one for both', () => {
    expect(() =>
      validateEnv({ JWT_SECRET: SECRET, ADMIN_PASSWORD: 'a-strong-admin-password', SUPPORT_PASSWORD: 'a-strong-support-password' }),
    ).not.toThrow();
    expect(() =>
      validateEnv({ JWT_SECRET: SECRET, ADMIN_PASSWORD: 'a-strong-shared-password', SUPPORT_PASSWORD: 'a-strong-shared-password' }),
    ).toThrow('SUPPORT_PASSWORD must differ from ADMIN_PASSWORD');
  });
});
