import { Inject, Injectable } from '@nestjs/common';
import type { Pool, PoolClient } from 'pg';
import { PG_POOL } from '../database/database.module';

export type RotateResult =
  | { status: 'rotated'; userId: number }
  | { status: 'reused'; userId: number }
  | { status: 'invalid' };

/**
 * The refresh_tokens table. Stores what it is given: generating and hashing
 * tokens is AuthService's job. Every token rotated from one login shares a
 * family id, so a whole session can be revoked at once.
 */
@Injectable()
export class RefreshTokensService {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  /**
   * Stores the hash of a new refresh token. Also removes the user's expired
   * tokens, so the table does not grow without bound.
   */
  async create(userId: number, tokenHash: string, familyId: string, ttlSeconds: number): Promise<void> {
    await this.pool.query('DELETE FROM refresh_tokens WHERE user_id = $1 AND expires_at <= NOW()', [userId]);
    await insert(this.pool, userId, tokenHash, familyId, ttlSeconds);
  }

  /**
   * Spends the presented token and stores its successor in the same family,
   * in one transaction. A token that was already spent means it was copied:
   * the whole family is revoked and "reused" returned.
   *
   * Expiry is computed and checked by Postgres, so both use the same clock.
   */
  async rotate(presentedHash: string, nextHash: string, ttlSeconds: number): Promise<RotateResult> {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');

      // Only one of two concurrent refreshes with the same token can match used_at IS NULL
      const spent = await client.query(
        `UPDATE refresh_tokens SET used_at = NOW()
          WHERE token_hash = $1 AND used_at IS NULL AND expires_at > NOW()
          RETURNING user_id, family_id`,
        [presentedHash],
      );
      if (spent.rows.length > 0) {
        const { user_id, family_id } = spent.rows[0];
        await insert(client, user_id, nextHash, family_id, ttlSeconds);
        await client.query('COMMIT');
        return { status: 'rotated', userId: Number(user_id) };
      }

      const reused = await client.query(
        `DELETE FROM refresh_tokens
          WHERE family_id = (SELECT family_id FROM refresh_tokens
                              WHERE token_hash = $1 AND used_at IS NOT NULL AND expires_at > NOW())
          RETURNING user_id`,
        [presentedHash],
      );
      await client.query('COMMIT');
      return reused.rows.length > 0 ? { status: 'reused', userId: Number(reused.rows[0].user_id) } : { status: 'invalid' };
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  /**
   * Revokes the session the token belongs to, spent or not, as long as it has
   * not expired.
   *
   * @returns false if no such token exists
   */
  async revokeFamily(tokenHash: string): Promise<boolean> {
    const result = await this.pool.query(
      `DELETE FROM refresh_tokens
        WHERE family_id = (SELECT family_id FROM refresh_tokens WHERE token_hash = $1 AND expires_at > NOW())`,
      [tokenHash],
    );
    return (result.rowCount ?? 0) > 0;
  }
}

function insert(
  db: Pool | PoolClient,
  userId: number,
  tokenHash: string,
  familyId: string,
  ttlSeconds: number,
): Promise<unknown> {
  return db.query(
    `INSERT INTO refresh_tokens (user_id, token_hash, family_id, expires_at)
     VALUES ($1, $2, $3, NOW() + make_interval(secs => $4))`,
    [userId, tokenHash, familyId, ttlSeconds],
  );
}
