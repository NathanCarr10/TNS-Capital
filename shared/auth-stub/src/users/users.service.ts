import { Inject, Injectable } from '@nestjs/common';
import type { Pool } from 'pg';
import { PG_POOL } from '../database/database.module';
import { hashPassword, verifyPassword } from './password-hasher';

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

export class UsernameTakenError extends Error {
  constructor() {
    super('Username already exists');
  }
}

// Postgres error code for a unique constraint violation
const UNIQUE_VIOLATION = '23505';

/**
 * Account numbers for self-registered users: "ACC-U" plus the zero-padded
 * user id, e.g. ACC-U000042. The "U" keeps them apart from the seeded
 * ACC-1001-style numbers and accounts opened through the trading API.
 */
export function accountNumberFor(userId: number): string {
  return `ACC-U${String(userId).padStart(6, '0')}`;
}

@Injectable()
export class UsersService {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  /**
   * Registers a user and opens their trading account (ACTIVE, cash balance 0).
   *
   * The user row, the account row and the link between them are written in
   * one transaction, so a user never exists without an account.
   *
   * @param holderName account holder name; defaults to the username
   * @throws UsernameTakenError if the username is already registered
   *
   * Satisfies AC#1: Plaintext never stored in database
   * Satisfies AC#4: Duplicate username throws, doesn't overwrite
   */
  async register(username: string, password: string, holderName?: string): Promise<RegisteredUser> {
    const existing = await this.pool.query('SELECT id FROM users WHERE username = $1', [username]);
    if (existing.rows.length > 0) {
      throw new UsernameTakenError();
    }

    // Hash password (never stored plaintext)
    const passwordHash = await hashPassword(password);

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
      // Two registrations for the same username can both pass the check above
      const pgError = error as { code?: string; constraint?: string };
      if (pgError.code === UNIQUE_VIOLATION && pgError.constraint === 'users_username_key') {
        throw new UsernameTakenError();
      }
      throw error;
    } finally {
      client.release();
    }
  }

  /**
   * Checks a username and password against the stored Argon2id hash.
   *
   * @returns the user with their roles, or null if the credentials are wrong
   *          (callers must not reveal which part was wrong)
   *
   * Satisfies AC#1: Compares plaintext input to stored hash only
   * Satisfies AC#5: Result never includes the password hash
   */
  async authenticate(username: string, password: string): Promise<AuthenticatedUser | null> {
    if (!username || !password) {
      return null;
    }

    const result = await this.pool.query(
      'SELECT id, username, password_hash, role, account_id FROM users WHERE username = $1',
      [username],
    );
    if (result.rows.length === 0) {
      return null;
    }

    const user = result.rows[0];
    if (!(await verifyPassword(password, user.password_hash))) {
      return null;
    }

    return {
      id: Number(user.id),
      username: user.username,
      roles: [user.role],
      accountId: user.account_id === null ? null : Number(user.account_id),
    };
  }
}
