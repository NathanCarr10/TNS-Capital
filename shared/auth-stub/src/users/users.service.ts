import { Inject, Injectable } from '@nestjs/common';
import type { Pool } from 'pg';
import { PG_POOL } from '../database/database.module';

export type Role = 'USER' | 'ADMIN';

export interface RegisteredUser {
  id: number;
  username: string;
  createdAt: Date;
  accountId: number;
  accountNumber: string;
}

export interface AuthenticatedUser {
  id: number;
  username: string;
  roles: Role[];
  /** The user's trading account; null for admins, who have none */
  accountId: number | null;
}

/** A user as stored, including the password hash. Never leaves the auth service. */
export interface UserRecord extends AuthenticatedUser {
  passwordHash: string;
}

export class UsernameTakenError extends Error {
  constructor() {
    super('Username already exists');
  }
}

// Postgres error code for a unique constraint violation
const UNIQUE_VIOLATION = '23505';

const USER_COLUMNS = 'id, username, password_hash, role, account_id';

/**
 * Account numbers for self-registered users: "ACC-U" plus the zero-padded
 * user id, e.g. ACC-U000042. The "U" keeps them apart from the seeded
 * ACC-1001-style numbers and accounts opened through the trading API.
 */
export function accountNumberFor(userId: number): string {
  return `ACC-U${String(userId).padStart(6, '0')}`;
}

/**
 * The users table. Stores what it is given: hashing passwords and refresh
 * tokens is AuthService's job.
 */
@Injectable()
export class UsersService {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  /**
   * Creates a user and opens their trading account (ACTIVE, cash balance 0).
   *
   * The user row, the account row and the link between them are written in
   * one transaction, so a user never exists without an account.
   *
   * @param passwordHash Argon2id hash; the plaintext never reaches this layer
   * @param holderName account holder name; defaults to the username
   * @throws UsernameTakenError if the username is already registered
   *
   * Satisfies AC#4: Duplicate username throws, doesn't overwrite
   */
  async create(username: string, passwordHash: string, holderName?: string): Promise<RegisteredUser> {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');

      const userResult = await client.query(
        'INSERT INTO users (username, password_hash, created_at, updated_at) VALUES ($1, $2, NOW(), NOW()) RETURNING id, username, created_at',
        [username, passwordHash],
      );
      const user = userResult.rows[0];
      const userId = Number(user.id);

      const accountResult = await client.query(
        'INSERT INTO accounts (account_number, holder_name, cash_balance) VALUES ($1, $2, 0) RETURNING id, account_number',
        [accountNumberFor(userId), holderName?.trim() || username],
      );
      const account = accountResult.rows[0];

      await client.query('UPDATE users SET account_id = $1 WHERE id = $2', [account.id, userId]);
      await client.query('COMMIT');

      return {
        id: userId,
        username: user.username,
        createdAt: user.created_at,
        accountId: Number(account.id),
        accountNumber: account.account_number,
      };
    } catch (error) {
      await client.query('ROLLBACK');
      // Two registrations for the same username can both pass AuthService's check
      const pgError = error as { code?: string; constraint?: string };
      if (pgError.code === UNIQUE_VIOLATION && pgError.constraint === 'users_username_key') {
        throw new UsernameTakenError();
      }
      throw error;
    } finally {
      client.release();
    }
  }

  async findByUsername(username: string): Promise<UserRecord | null> {
    const result = await this.pool.query(`SELECT ${USER_COLUMNS} FROM users WHERE username = $1`, [username]);
    return result.rows.length === 0 ? null : toUserRecord(result.rows[0]);
  }

  /**
   * Stores the hash of the user's new refresh token, replacing any previous
   * one. The expiry is computed by Postgres, so it and the check in
   * findByRefreshTokenHash use the same clock.
   */
  async setRefreshToken(userId: number, tokenHash: string, ttlSeconds: number): Promise<void> {
    await this.pool.query(
      `UPDATE users
          SET refresh_token_hash = $2,
              refresh_token_expires_at = NOW() + make_interval(secs => $3),
              updated_at = NOW()
        WHERE id = $1`,
      [userId, tokenHash, ttlSeconds],
    );
  }

  /** @returns the user holding this refresh token, or null if none does or it has expired */
  async findByRefreshTokenHash(tokenHash: string): Promise<UserRecord | null> {
    const result = await this.pool.query(
      `SELECT ${USER_COLUMNS} FROM users WHERE refresh_token_hash = $1 AND refresh_token_expires_at > NOW()`,
      [tokenHash],
    );
    return result.rows.length === 0 ? null : toUserRecord(result.rows[0]);
  }

  async clearRefreshToken(userId: number): Promise<void> {
    await this.pool.query(
      'UPDATE users SET refresh_token_hash = NULL, refresh_token_expires_at = NULL, updated_at = NOW() WHERE id = $1',
      [userId],
    );
  }
}

function toUserRecord(row: {
  id: string | number;
  username: string;
  password_hash: string;
  role: Role;
  account_id: string | number | null;
}): UserRecord {
  return {
    id: Number(row.id),
    username: row.username,
    passwordHash: row.password_hash,
    roles: [row.role],
    accountId: row.account_id === null ? null : Number(row.account_id),
  };
}
