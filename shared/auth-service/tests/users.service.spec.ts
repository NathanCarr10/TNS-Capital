/**
 * UsersService against a real Postgres with the db/ schema (DB_HOST, DB_PORT,
 * DB_NAME, DB_USER, DB_PASSWORD from the environment). Skipped unless DB_HOST
 * is set. Rows created here are removed afterwards.
 */
import { Pool } from 'pg';
import { hashPassword } from '../src/users/password-hasher';
import { accountNumberFor, UsernameTakenError, UsersService } from '../src/users/users.service';

const describeWithDb = process.env.DB_HOST ? describe : describe.skip;

const pool = new Pool({
  host: process.env.DB_HOST,
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

let passwordHash: string;

beforeAll(async () => {
  passwordHash = await hashPassword('password123');
});

afterAll(async () => {
  if (created.length === 0) {
    await pool.end();
    return;
  }
  const users = await pool.query('DELETE FROM users WHERE username = ANY($1) RETURNING account_id', [created]);
  const accountIds = users.rows.map((row) => row.account_id).filter((id) => id !== null);
  await pool.query('DELETE FROM accounts WHERE id = ANY($1)', [accountIds]);
  await pool.end();
});

describeWithDb('UsersService.create', () => {
  it('stores the hash it is given', async () => {
    const username = uniqueName('hashtest');
    await service.create(username, passwordHash);

    const row = (await pool.query('SELECT password_hash FROM users WHERE username = $1', [username])).rows[0];
    expect(row.password_hash).toBe(passwordHash);
  });

  it('opens an ACTIVE account with a zero cash balance and links it to the user', async () => {
    const username = uniqueName('newtrader');
    const user = await service.create(username, passwordHash, 'Ada Lovelace');

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
    const user = await service.create(username, passwordHash);

    const account = (await pool.query('SELECT holder_name FROM accounts WHERE id = $1', [user.accountId])).rows[0];
    expect(account.holder_name).toBe(username);
  });

  it('rejects a duplicate username without opening a second account (AC#4)', async () => {
    const username = uniqueName('duplicate');
    await service.create(username, passwordHash);
    const accountsBefore = Number((await pool.query('SELECT COUNT(*) FROM accounts')).rows[0].count);

    await expect(service.create(username, passwordHash)).rejects.toBeInstanceOf(UsernameTakenError);

    const accountsAfter = Number((await pool.query('SELECT COUNT(*) FROM accounts')).rows[0].count);
    expect(accountsAfter).toBe(accountsBefore);
  });

  it('rolls back the user when the account cannot be opened', async () => {
    const username = uniqueName('rollback');
    // A holder name longer than the column fails the account insert
    await expect(service.create(username, passwordHash, 'x'.repeat(300))).rejects.toThrow();

    const users = await pool.query('SELECT id FROM users WHERE username = $1', [username]);
    expect(users.rows).toHaveLength(0);
  });
});

describeWithDb('UsersService.findByUsername', () => {
  it('returns the user with their hash, USER role and account', async () => {
    const username = uniqueName('findtest');
    const { id, accountId } = await service.create(username, passwordHash);

    expect(await service.findByUsername(username)).toEqual({ id, username, passwordHash, roles: ['USER'], accountId });
  });

  it('returns null for an unknown user', async () => {
    expect(await service.findByUsername('nonexistent_user')).toBeNull();
  });

  it('finds the seeded admin with ADMIN and no account', async () => {
    const admin = await service.findByUsername('admin');

    expect(admin).toMatchObject({ username: 'admin', roles: ['ADMIN'], accountId: null });
  });
});

describeWithDb('UsersService refresh tokens', () => {
  const hashA = 'a'.repeat(64);
  const hashB = 'b'.repeat(64);
  let userId: number;
  let username: string;

  beforeAll(async () => {
    username = uniqueName('refreshtest');
    userId = (await service.create(username, passwordHash)).id;
  });

  it('finds the user by the stored hash', async () => {
    await service.setRefreshToken(userId, hashA, 3600);

    expect(await service.findByRefreshTokenHash(hashA)).toMatchObject({ id: userId, username });
  });

  it('replaces the previous hash on the next login', async () => {
    await service.setRefreshToken(userId, hashA, 3600);
    await service.setRefreshToken(userId, hashB, 3600);

    expect(await service.findByRefreshTokenHash(hashA)).toBeNull();
    expect(await service.findByRefreshTokenHash(hashB)).toMatchObject({ id: userId });
  });

  it('does not find an expired token', async () => {
    await service.setRefreshToken(userId, hashA, -1);

    expect(await service.findByRefreshTokenHash(hashA)).toBeNull();
  });

  it('clears the token', async () => {
    await service.setRefreshToken(userId, hashA, 3600);
    await service.clearRefreshToken(userId);

    expect(await service.findByRefreshTokenHash(hashA)).toBeNull();
    const row = (
      await pool.query('SELECT refresh_token_hash, refresh_token_expires_at FROM users WHERE id = $1', [userId])
    ).rows[0];
    expect(row).toEqual({ refresh_token_hash: null, refresh_token_expires_at: null });
  });
});
