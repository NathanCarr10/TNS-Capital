/**
 * UsersService against a real Postgres with the db/ schema (DB_HOST, DB_PORT,
 * DB_NAME, DB_USER, DB_PASSWORD from the environment). Rows created here are
 * removed afterwards.
 */
import { Pool } from 'pg';
import { accountNumberFor, UsernameTakenError, UsersService } from '../src/users/users.service';

const pool = new Pool({
  host: process.env.DB_HOST ?? 'localhost',
  port: Number(process.env.DB_PORT ?? 5432),
  database: process.env.DB_NAME ?? 'tns_capital',
  user: process.env.DB_USER ?? 'tns-capital-db-user',
  password: process.env.DB_PASSWORD,
});
const service = new UsersService(pool);

const runId = Date.now();
const created: string[] = [];
const uniqueName = (prefix: string) => {
  const name = `${prefix}_${runId}_${created.length}`;
  created.push(name);
  return name;
};

afterAll(async () => {
  const users = await pool.query('DELETE FROM users WHERE username = ANY($1) RETURNING account_id', [created]);
  const accountIds = users.rows.map((row) => row.account_id).filter((id) => id !== null);
  await pool.query('DELETE FROM accounts WHERE id = ANY($1)', [accountIds]);
  await pool.end();
});

describe('UsersService.register', () => {
  it('stores an Argon2id hash, never the plaintext password (AC#1)', async () => {
    const username = uniqueName('hashtest');
    await service.register(username, 'mySecretPassword123');

    const row = (await pool.query('SELECT password_hash FROM users WHERE username = $1', [username])).rows[0];
    expect(row.password_hash).toMatch(/^\$argon2id\$/);
    expect(row.password_hash).not.toContain('mySecretPassword123');
  });

  it('opens an ACTIVE account with a zero cash balance and links it to the user', async () => {
    const username = uniqueName('newtrader');
    const user = await service.register(username, 'password123', 'Ada Lovelace');

    expect(user.accountNumber).toBe(accountNumberFor(user.id));

    const account = (
      await pool.query('SELECT account_number, holder_name, cash_balance, status FROM accounts WHERE id = $1', [
        user.accountId,
      ])
    ).rows[0];
    expect(account).toEqual({
      account_number: user.accountNumber,
      holder_name: 'Ada Lovelace',
      cash_balance: '0.00',
      status: 'ACTIVE',
    });

    const link = (await pool.query('SELECT account_id, role FROM users WHERE id = $1', [user.id])).rows[0];
    expect(Number(link.account_id)).toBe(user.accountId);
    expect(link.role).toBe('USER');
  });

  it('uses the username as the holder name when none is given', async () => {
    const username = uniqueName('noholder');
    const user = await service.register(username, 'password123');

    const account = (await pool.query('SELECT holder_name FROM accounts WHERE id = $1', [user.accountId])).rows[0];
    expect(account.holder_name).toBe(username);
  });

  it('rejects a duplicate username without opening a second account (AC#4)', async () => {
    const username = uniqueName('duplicate');
    await service.register(username, 'password123');
    const accountsBefore = Number((await pool.query('SELECT COUNT(*) FROM accounts')).rows[0].count);

    await expect(service.register(username, 'password123')).rejects.toBeInstanceOf(UsernameTakenError);

    const accountsAfter = Number((await pool.query('SELECT COUNT(*) FROM accounts')).rows[0].count);
    expect(accountsAfter).toBe(accountsBefore);
  });

  it('rolls back the user when the account cannot be opened', async () => {
    const username = uniqueName('rollback');
    // A holder name longer than the column fails the account insert
    await expect(service.register(username, 'password123', 'x'.repeat(300))).rejects.toThrow();

    const users = await pool.query('SELECT id FROM users WHERE username = $1', [username]);
    expect(users.rows).toHaveLength(0);
  });
});

describe('UsersService.authenticate', () => {
  const password = 'testPassword123';
  let username: string;
  let accountId: number;

  beforeAll(async () => {
    username = uniqueName('authtest');
    accountId = (await service.register(username, password)).accountId;
  });

  it('returns the user with the USER role and their account (AC#6)', async () => {
    const user = await service.authenticate(username, password);

    expect(user).toEqual({ id: expect.any(Number), username, roles: ['USER'], accountId });
    expect(user).not.toHaveProperty('password_hash'); // AC#5
  });

  it('rejects a wrong password (AC#6)', async () => {
    expect(await service.authenticate(username, 'wrongPassword')).toBeNull();
  });

  it('rejects an unknown user', async () => {
    expect(await service.authenticate('nonexistent_user', password)).toBeNull();
  });

  it('rejects empty credentials', async () => {
    expect(await service.authenticate('', password)).toBeNull();
    expect(await service.authenticate(username, '')).toBeNull();
  });

  it('logs in the seeded admin with ADMIN and no account', async () => {
    const admin = await service.authenticate('admin', 'adminPassword');

    expect(admin).toEqual({ id: expect.any(Number), username: 'admin', roles: ['ADMIN'], accountId: null });
  });
});
