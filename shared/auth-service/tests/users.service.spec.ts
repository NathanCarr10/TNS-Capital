/**
 * UsersService and RefreshTokensService against a real Postgres with the db/ schema (DB_HOST, DB_PORT,
 * DB_NAME, DB_USER, DB_PASSWORD from the environment). Skipped unless DB_HOST
 * is set. Rows created here are removed afterwards.
 */
import { Pool } from 'pg';
import { randomUUID } from 'crypto';
import { hashPassword } from '../src/users/password-hasher';
import { RefreshTokensService } from '../src/users/refresh-tokens.service';
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
const refreshTokens = new RefreshTokensService(pool);

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

  it('finds the same user by id', async () => {
    const username = uniqueName('findbyid');
    const { id } = await service.create(username, passwordHash);

    expect(await service.findById(id)).toEqual(await service.findByUsername(username));
    expect(await service.findById(-1)).toBeNull();
  });

  it('finds the seeded admin with ADMIN and no account', async () => {
    const admin = await service.findByUsername('admin');

    expect(admin).toMatchObject({ username: 'admin', roles: ['ADMIN'], accountId: null });
  });
});

describeWithDb('RefreshTokensService', () => {
  let userId: number;
  let n = 0;
  // A fresh 64-character hex hash per call
  const hash = () => (runId.toString(16) + (n++).toString(16).padStart(8, '0')).padEnd(64, 'f');
  const row = async (tokenHash: string) =>
    (await pool.query('SELECT user_id, family_id, used_at FROM refresh_tokens WHERE token_hash = $1', [tokenHash]))
      .rows[0];

  beforeAll(async () => {
    userId = (await service.create(uniqueName('refreshtest'), passwordHash)).id;
  });

  it('rotates a token: spends it and stores its successor in the same family', async () => {
    const [first, second, family] = [hash(), hash(), randomUUID()];
    await refreshTokens.create(userId, first, family, 3600);

    expect(await refreshTokens.rotate(first, second, 3600)).toEqual({ status: 'rotated', userId });
    expect((await row(first)).used_at).not.toBeNull();
    expect(await row(second)).toMatchObject({ family_id: family, used_at: null });
  });

  it('revokes the whole family when a spent token is presented again', async () => {
    const [first, second, third] = [hash(), hash(), hash()];
    await refreshTokens.create(userId, first, randomUUID(), 3600);
    await refreshTokens.rotate(first, second, 3600);

    expect(await refreshTokens.rotate(first, third, 3600)).toEqual({ status: 'reused', userId });
    expect(await row(first)).toBeUndefined();
    expect(await row(second)).toBeUndefined();
    expect(await row(third)).toBeUndefined();
  });

  it('lets only one of two concurrent refreshes with the same token win', async () => {
    const first = hash();
    await refreshTokens.create(userId, first, randomUUID(), 3600);

    const results = await Promise.all([refreshTokens.rotate(first, hash(), 3600), refreshTokens.rotate(first, hash(), 3600)]);
    expect(results.map((r) => r.status).sort()).toEqual(['reused', 'rotated']);
  });

  it.each([
    ['an unknown token', async () => hash()],
    [
      'an expired token',
      async () => {
        const expired = hash();
        await refreshTokens.create(userId, expired, randomUUID(), -1);
        return expired;
      },
    ],
  ])('does not rotate %s', async (_case, makeToken) => {
    const next = hash();
    expect(await refreshTokens.rotate(await makeToken(), next, 3600)).toEqual({ status: 'invalid' });
    expect(await row(next)).toBeUndefined();
  });

  it('revokes a family on logout, leaving other families alone', async () => {
    const [mine, other] = [hash(), hash()];
    await refreshTokens.create(userId, mine, randomUUID(), 3600);
    await refreshTokens.create(userId, other, randomUUID(), 3600);

    expect(await refreshTokens.revokeFamily(mine)).toBe(true);
    expect(await row(mine)).toBeUndefined();
    expect(await row(other)).toBeDefined();
    expect(await refreshTokens.revokeFamily(mine)).toBe(false);
  });

  it("removes the user's expired tokens when a new one is created", async () => {
    const expired = hash();
    await refreshTokens.create(userId, expired, randomUUID(), -1);
    await refreshTokens.create(userId, hash(), randomUUID(), 3600);

    expect(await row(expired)).toBeUndefined();
  });

  it("deletes the user's tokens along with the user", async () => {
    const username = uniqueName('cascade');
    const { id, accountId } = await service.create(username, passwordHash);
    const token = hash();
    await refreshTokens.create(id, token, randomUUID(), 3600);

    await pool.query('DELETE FROM users WHERE id = $1', [id]);
    await pool.query('DELETE FROM accounts WHERE id = $1', [accountId]);
    expect(await row(token)).toBeUndefined();
  });
});
