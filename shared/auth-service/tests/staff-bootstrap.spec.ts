/**
 * AdminBootstrap with UsersService mocked: when the admin account is created, updated or left
 * alone at startup (OWASP A07-4).
 */
import { Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AdminBootstrap } from '../src/users/admin-bootstrap';
import { hashPassword, verifyPassword } from '../src/users/password-hasher';
import { AdminNameTakenError, UserRecord, UsersService } from '../src/users/users.service';

describe('AdminBootstrap', () => {
  const users = { findByUsername: jest.fn(), saveAdmin: jest.fn() };
  const PASSWORD = 'a-strong-admin-password';

  const bootstrap = (env: Record<string, string | undefined>) =>
    new AdminBootstrap(
      { get: (key: string) => env[key] } as unknown as ConfigService,
      users as unknown as UsersService,
    ).onApplicationBootstrap();

  const admin = async (password: string): Promise<UserRecord> => ({
    id: 1,
    username: 'admin',
    roles: ['ADMIN'],
    accountId: null,
    passwordHash: await hashPassword(password),
  });

  beforeEach(() => {
    jest.clearAllMocks();
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => undefined);
    jest.spyOn(Logger.prototype, 'warn').mockImplementation(() => undefined);
    jest.spyOn(Logger.prototype, 'error').mockImplementation(() => undefined);
  });

  afterEach(() => jest.restoreAllMocks());

  it('creates the admin from ADMIN_PASSWORD, storing only an Argon2id hash', async () => {
    users.findByUsername.mockResolvedValue(null);

    await bootstrap({ ADMIN_PASSWORD: PASSWORD });

    const [username, hash] = users.saveAdmin.mock.calls[0];
    expect(username).toBe('admin');
    expect(hash).toMatch(/^\$argon2id\$/);
    expect(await verifyPassword(PASSWORD, hash)).toBe(true);
  });

  it('replaces a different password, such as the old seeded default', async () => {
    users.findByUsername.mockResolvedValue(await admin('adminPassword'));

    await bootstrap({ ADMIN_PASSWORD: PASSWORD });

    expect(await verifyPassword(PASSWORD, users.saveAdmin.mock.calls[0][1])).toBe(true);
  });

  it('leaves the admin alone when the password already matches', async () => {
    users.findByUsername.mockResolvedValue(await admin(PASSWORD));

    await bootstrap({ ADMIN_PASSWORD: PASSWORD });

    expect(users.saveAdmin).not.toHaveBeenCalled();
  });

  it('uses ADMIN_USERNAME when it is set', async () => {
    users.findByUsername.mockResolvedValue(null);

    await bootstrap({ ADMIN_PASSWORD: PASSWORD, ADMIN_USERNAME: 'ops' });

    expect(users.saveAdmin.mock.calls[0][0]).toBe('ops');
  });

  it('changes nothing without ADMIN_PASSWORD, and warns', async () => {
    await bootstrap({});

    expect(users.findByUsername).not.toHaveBeenCalled();
    expect(users.saveAdmin).not.toHaveBeenCalled();
    expect(Logger.prototype.warn).toHaveBeenCalledWith(expect.stringContaining('ADMIN_PASSWORD is not set'));
  });

  it('never promotes a trader who registered the admin username, and still starts', async () => {
    users.findByUsername.mockResolvedValue({ ...(await admin('trader-password')), roles: ['USER'], accountId: 7 });
    users.saveAdmin.mockRejectedValue(new AdminNameTakenError('admin'));

    await expect(bootstrap({ ADMIN_PASSWORD: PASSWORD })).resolves.toBeUndefined();

    expect(Logger.prototype.error).toHaveBeenCalledWith(expect.stringContaining("trader's username"));
  });
});
