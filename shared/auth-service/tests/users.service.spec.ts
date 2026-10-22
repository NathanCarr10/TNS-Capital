/**
 * UsersService and RefreshTokensService against a real Postgres with the db/ schema (DB_HOST, DB_PORT,
 * DB_NAME, DB_USER, DB_PASSWORD from the environment). Skipped unless DB_HOST
 * is set. Rows created here are removed afterwards.
 */
import { Pool } from 'pg';
import { randomUUID } from 'node:crypto';
import { hashPassword } from '../src/users/password-hasher';
import { RefreshTokensService } from '../src/users/refresh-tokens.service';
import { accountNumberFor, EmailTakenError, StaffEmailTakenError, UsersService } from '../src/users/users.service';

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
const uniqueEmail = (prefix: string) => {
  const email = `${prefix}_${runId}_${created.length}@example.com`;
  created.push(email);
  return email;
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
  const users = await pool.query('DELETE FROM users WHERE email = ANY($1) RETURNING account_id', [created]);
  const accountIds = users.rows.map((row) => row.account_id).filter((id) => id !== null);
  await pool.query('DELETE FROM accounts WHERE id = ANY($1)', [accountIds]);
  await pool.end();
});

describeWithDb('UsersService.create', () => {
  it('stores the hash it is given', async () => {
    const email = uniqueEmail('hashtest');
    await service.create(email, passwordHash);

    const row = (await pool.query('SELECT password_hash FROM users WHERE email = $1', [email])).rows[0];
    expect(row.password_hash).toBe(passwordHash);
  });

  it('opens an ACTIVE account with a zero cash balance and links it to the user', async () => {
    const email = uniqueEmail('newtrader');
    const user = await service.create(email, passwordHash, 'Ada Lovelace');

    expect(user.accountNumber).toBe(accountNumberFor(user.id));

    const account = (
      await pool.query('SELECT account_number, holder_name, email, cash_balance, status FROM accounts WHERE id = $1', [
        user.accountId,
      ])
    ).rows[0];
    expect(account).toEqual({
      account_number: user.accountNumber,
      holder_name: 'Ada Lovelace',
      email,
      cash_balance: '0.00',
      status: 'ACTIVE',
    });

    const link = (await pool.query('SELECT account_id, role FROM users WHERE id = $1', [user.id])).rows[0];
    expect(Number(link.account_id)).toBe(user.accountId);
    expect(link.role).toBe('USER');
  });

  it('uses the part of the email before the "@" as the holder name when none is given', async () => {
    const email = uniqueEmail('noholder');
    const user = await service.create(email, passwordHash);

    const account = (await pool.query('SELECT holder_name FROM accounts WHERE id = $1', [user.accountId])).rows[0];
    expect(account.holder_name).toBe(email.split('@')[0]);
  });

  it('rejects a duplicate email without opening a second account (AC#4)', async () => {
    const email = uniqueEmail('duplicate');
    await service.create(email, passwordHash);
    const accountsBefore = Number((await pool.query('SELECT COUNT(*) FROM accounts')).rows[0].count);

    await expect(service.create(email, passwordHash)).rejects.toBeInstanceOf(EmailTakenError);

    const accountsAfter = Number((await pool.query('SELECT COUNT(*) FROM accounts')).rows[0].count);
    expect(accountsAfter).toBe(accountsBefore);
  });

  it('rolls back the user when the account cannot be opened', async () => {
    const email = uniqueEmail('rollback');
    // A holder name longer than the column fails the account insert
    await expect(service.create(email, passwordHash, 'x'.repeat(300))).rejects.toThrow();

    const users = await pool.query('SELECT id FROM users WHERE email = $1', [email]);
    expect(users.rows).toHaveLength(0);
  });
});

describeWithDb('UsersService.findByEmail', () => {
  it('returns the user with their hash, USER role and account', async () => {
    const email = uniqueEmail('findtest');
    const { id, accountId } = await service.create(email, passwordHash);

    expect(await service.findByEmail(email)).toEqual({
      id,
      email,
      passwordHash,
      roles: ['USER'],
      accountId,
    });
  });

  it('returns null for an unknown user', async () => {
    expect(await service.findByEmail('nonexistent_user@example.com')).toBeNull();
  });

  it('finds the same user by id', async () => {
    const email = uniqueEmail('findbyid');
    const { id } = await service.create(email, passwordHash);

    expect(await service.findById(id)).toEqual(await service.findByEmail(email));
    expect(await service.findById(-1)).toBeNull();
  });

});

describeWithDb('UsersService.saveStaff', () => {
  it.each(['ADMIN', 'SUPPORT'] as const)('creates a %s with no trading account, then sets a new password on it', async (role) => {
    const email = uniqueEmail(`${role.toLowerCase()}test`);

    await service.saveStaff(email, passwordHash, role);
    expect(await service.findByEmail(email)).toMatchObject({ email, roles: [role], accountId: null, passwordHash });

    await service.saveStaff(email, 'new-hash', role);
    expect((await service.findByEmail(email))?.passwordHash).toBe('new-hash');
  });

  it("never promotes a trader's email", async () => {
    const email = uniqueEmail('notadmin');
    await service.create(email, passwordHash);

    await expect(service.saveStaff(email, 'new-hash', 'ADMIN')).rejects.toThrow(StaffEmailTakenError);
    expect(await service.findByEmail(email)).toMatchObject({ roles: ['USER'], passwordHash });
  });

  it('never turns the support account into the admin one', async () => {
    const email = uniqueEmail('supportonly');
    await service.saveStaff(email, passwordHash, 'SUPPORT');

    await expect(service.saveStaff(email, 'new-hash', 'ADMIN')).rejects.toThrow('already the SUPPORT account');
    expect(await service.findByEmail(email)).toMatchObject({ roles: ['SUPPORT'], passwordHash });
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
    userId = (await service.create(uniqueEmail('refreshtest'), passwordHash)).id;
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
    const { id, accountId } = await service.create(uniqueEmail('cascade'), passwordHash);
    const token = hash();
    await refreshTokens.create(id, token, randomUUID(), 3600);

    await pool.query('DELETE FROM users WHERE id = $1', [id]);
    await pool.query('DELETE FROM accounts WHERE id = $1', [accountId]);
    expect(await row(token)).toBeUndefined();
  });
});
