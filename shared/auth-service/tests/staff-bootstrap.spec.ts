/**
 * StaffBootstrap with UsersService mocked: when the admin and support accounts are created,
 * updated or left alone at startup (OWASP A07-4).
 */
import { Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { StaffBootstrap } from '../src/users/staff-bootstrap';
import { hashPassword, verifyPassword } from '../src/users/password-hasher';
import { StaffEmailTakenError, UserRecord, UsersService } from '../src/users/users.service';

describe('StaffBootstrap', () => {
  const users = { findByEmail: jest.fn(), saveStaff: jest.fn() };
  const PASSWORD = 'a-strong-admin-password';
  const SUPPORT_PASSWORD = 'a-strong-support-password';

  const bootstrap = (env: Record<string, string | undefined>) =>
    new StaffBootstrap(
      { get: (key: string) => env[key] } as unknown as ConfigService,
      users as unknown as UsersService,
    ).onApplicationBootstrap();

  const admin = async (password: string): Promise<UserRecord> => ({
    id: 1,
    email: 'admin@tns-capital.local',
    roles: ['ADMIN'],
    accountId: null,
    passwordHash: await hashPassword(password),
  });

  /** The saveStaff call for one role. */
  const savedAs = (role: string) => users.saveStaff.mock.calls.find((call) => call[2] === role);

  beforeEach(() => {
    jest.clearAllMocks();
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => undefined);
    jest.spyOn(Logger.prototype, 'warn').mockImplementation(() => undefined);
    jest.spyOn(Logger.prototype, 'error').mockImplementation(() => undefined);
  });

  afterEach(() => jest.restoreAllMocks());

  it('creates the admin from ADMIN_PASSWORD, storing only an Argon2id hash', async () => {
    users.findByEmail.mockResolvedValue(null);

    await bootstrap({ ADMIN_PASSWORD: PASSWORD });

    const [email, hash] = savedAs('ADMIN')!;
    expect(email).toBe('admin@tns-capital.local');
    expect(hash).toMatch(/^\$argon2id\$/);
    expect(await verifyPassword(PASSWORD, hash)).toBe(true);
  });

  it('creates the support account from SUPPORT_PASSWORD, apart from the admin', async () => {
    users.findByEmail.mockResolvedValue(null);

    await bootstrap({ ADMIN_PASSWORD: PASSWORD, SUPPORT_PASSWORD });

    const [email, hash] = savedAs('SUPPORT')!;
    expect(email).toBe('support@tns-capital.local');
    expect(await verifyPassword(SUPPORT_PASSWORD, hash)).toBe(true);
    expect(savedAs('ADMIN')![0]).toBe('admin@tns-capital.local');
  });

  it('replaces a different password, such as the old seeded default', async () => {
    users.findByEmail.mockResolvedValue(await admin('adminPassword'));

    await bootstrap({ ADMIN_PASSWORD: PASSWORD });

    expect(await verifyPassword(PASSWORD, savedAs('ADMIN')![1])).toBe(true);
  });

  it('leaves the admin alone when the password already matches', async () => {
    users.findByEmail.mockResolvedValue(await admin(PASSWORD));

    await bootstrap({ ADMIN_PASSWORD: PASSWORD });

    expect(users.saveStaff).not.toHaveBeenCalled();
  });

  it('uses ADMIN_EMAIL and SUPPORT_EMAIL when they are set, in lower case', async () => {
    users.findByEmail.mockResolvedValue(null);

    await bootstrap({ ADMIN_PASSWORD: PASSWORD, ADMIN_EMAIL: 'Ops@Example.com', SUPPORT_PASSWORD, SUPPORT_EMAIL: 'helpdesk@example.com' });

    expect(savedAs('ADMIN')![0]).toBe('ops@example.com');
    expect(savedAs('SUPPORT')![0]).toBe('helpdesk@example.com');
  });

  it('changes nothing without the passwords, and warns for each', async () => {
    await bootstrap({});

    expect(users.findByEmail).not.toHaveBeenCalled();
    expect(users.saveStaff).not.toHaveBeenCalled();
    expect(Logger.prototype.warn).toHaveBeenCalledWith(expect.stringContaining('ADMIN_PASSWORD is not set'));
    expect(Logger.prototype.warn).toHaveBeenCalledWith(expect.stringContaining('SUPPORT_PASSWORD is not set'));
  });

  it('never promotes a trader who registered the admin email, and still starts', async () => {
    users.findByEmail.mockResolvedValue({ ...(await admin('trader-password')), roles: ['USER'], accountId: 7 });
    users.saveStaff.mockRejectedValue(new StaffEmailTakenError('admin@tns-capital.local', 'ADMIN', ['USER']));

    await expect(bootstrap({ ADMIN_PASSWORD: PASSWORD })).resolves.toBeUndefined();

    expect(Logger.prototype.error).toHaveBeenCalledWith(expect.stringContaining("trader's email"));
  });
});
