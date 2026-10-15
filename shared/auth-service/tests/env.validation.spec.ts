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
});
