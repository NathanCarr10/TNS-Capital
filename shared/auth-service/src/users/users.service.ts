import { Inject, Injectable } from '@nestjs/common';
import type { Pool } from 'pg';
import { PG_POOL } from '../database/database.module';

/**
 * USER trades on their own account. The two staff roles have no account and are kept apart:
 * SUPPORT works in the trading app, ADMIN runs the analytics dashboard and operations.
 */
export type Role = 'USER' | 'SUPPORT' | 'ADMIN';
export type StaffRole = Exclude<Role, 'USER'>;

export interface RegisteredUser {
  id: number;
  username: string;
  email: string;
  createdAt: Date;
  accountId: number;
  accountNumber: string;
}

export interface AuthenticatedUser {
  id: number;
  username: string;
  email?: string | null;
  roles: Role[];
  /** The user's trading account; null for support and admin users, who have none */
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

export class EmailTakenError extends Error {
  constructor() {
    super('Email already exists');
  }
}

/** A staff username already belongs to a user with another role, which is never changed. */
export class StaffNameTakenError extends Error {
  constructor(username: string, role: StaffRole, existingRoles: Role[]) {
    const owner = existingRoles.includes('USER') ? "a trader's username" : `already the ${existingRoles.join(', ')} account`;
    super(`"${username}" is ${owner}, so it cannot be the ${role} account`);
  }
}

// Postgres error code for a unique constraint violation
const UNIQUE_VIOLATION = '23505';

const USER_COLUMNS = 'id, username, email, password_hash, role, account_id';

/**
 * Account numbers for self-registered users: "ACC-U" plus the zero-padded
 * user id, e.g. ACC-U000042. The "U" keeps them apart from the seeded
 * ACC-1001-style numbers and accounts opened through the trading API.
 */
export function accountNumberFor(userId: number): string {
  return `ACC-U${String(userId).padStart(6, '0')}`;
}

/**
 * The users table. Stores what it is given: hashing passwords is
 * AuthService's job.
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
  async create(username: string, passwordHash: string, email = `${username}@example.invalid`, holderName?: string): Promise<RegisteredUser> {
    if (!email.includes('@')) {
      holderName = email;
      email = `${username}@example.invalid`;
    }
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');

      const userResult = await client.query(
        'INSERT INTO users (username, email, password_hash, created_at, updated_at) VALUES ($1, $2, $3, NOW(), NOW()) RETURNING id, username, email, created_at',
        [username, email, passwordHash],
      );
      const user = userResult.rows[0];
      const userId = Number(user.id);

      const accountResult = await client.query(
        'INSERT INTO accounts (account_number, holder_name, email, cash_balance) VALUES ($1, $2, $3, 0) RETURNING id, account_number',
        [accountNumberFor(userId), holderName?.trim() || username, email],
      );
      const account = accountResult.rows[0];

      await client.query('UPDATE users SET account_id = $1 WHERE id = $2', [account.id, userId]);
      await client.query('COMMIT');

      return {
        id: userId,
        username: user.username,
        email: user.email,
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
      if (pgError.code === UNIQUE_VIOLATION && pgError.constraint === 'users_email_key') {
        throw new EmailTakenError();
      }
      throw error;
    } finally {
      client.release();
    }
  }

  /**
   * Creates a staff user (SUPPORT or ADMIN), or sets a new password on it. Staff have no
   * trading account.
   *
   * @throws StaffNameTakenError if a user with another role (a trader, or the other staff
   *   role) already has this username
   */
  async saveStaff(username: string, passwordHash: string, role: StaffRole, email = `${username}@tns-capital.local`): Promise<void> {
    const existing = await this.findByUsername(username);
    if (existing && !existing.roles.includes(role)) {
      throw new StaffNameTakenError(username, role, existing.roles);
    }
    if (existing) {
      await this.pool.query('UPDATE users SET password_hash = $1, email = COALESCE(email, $3), updated_at = NOW() WHERE id = $2', [
        passwordHash,
        existing.id,
        email,
      ]);
      return;
    }
    await this.pool.query(
      'INSERT INTO users (username, email, password_hash, role, created_at, updated_at) VALUES ($1, $2, $3, $4, NOW(), NOW())',
      [username, email, passwordHash, role],
    );
  }

  /** Creates or updates the local demo trader without opening a second account. */
  async saveDemoUser(username: string, passwordHash: string, email: string, accountNumber: string): Promise<void> {
    const account = await this.pool.query('SELECT id FROM accounts WHERE account_number = $1', [accountNumber]);
    if (account.rows.length === 0) {
      throw new Error(`Demo account ${accountNumber} does not exist`);
    }

    const existing = await this.findByUsername(username);
    if (existing && (existing.roles.length !== 1 || existing.roles[0] !== 'USER' || existing.accountId !== Number(account.rows[0].id))) {
      throw new Error(`Demo username "${username}" is already linked to another user`);
    }
    if (existing) {
      await this.pool.query('UPDATE users SET password_hash = $1, email = $2, updated_at = NOW() WHERE id = $3', [passwordHash, email, existing.id]);
      return;
    }

    await this.pool.query(
      'INSERT INTO users (username, email, password_hash, role, account_id, created_at, updated_at) VALUES ($1, $2, $3, $4, $5, NOW(), NOW())',
      [username, email, passwordHash, 'USER', account.rows[0].id],
    );
  }

  async findByUsername(username: string): Promise<UserRecord | null> {
    const result = await this.pool.query(`SELECT ${USER_COLUMNS} FROM users WHERE username = $1`, [username]);
    return result.rows.length === 0 ? null : toUserRecord(result.rows[0]);
  }

  async findByEmail(email: string): Promise<UserRecord | null> {
    const result = await this.pool.query(`SELECT ${USER_COLUMNS} FROM users WHERE email = $1`, [email]);
    return result.rows.length === 0 ? null : toUserRecord(result.rows[0]);
  }

  async findById(id: number): Promise<UserRecord | null> {
    const result = await this.pool.query(`SELECT ${USER_COLUMNS} FROM users WHERE id = $1`, [id]);
    return result.rows.length === 0 ? null : toUserRecord(result.rows[0]);
  }
}

function toUserRecord(row: {
  id: string | number;
  username: string;
  email: string | null;
  password_hash: string;
  role: Role;
  account_id: string | number | null;
}): UserRecord {
  return {
    id: Number(row.id),
    username: row.username,
    email: row.email,
    passwordHash: row.password_hash,
    roles: [row.role],
    accountId: row.account_id === null ? null : Number(row.account_id),
  };
}
