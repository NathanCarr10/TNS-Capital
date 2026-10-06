import { Injectable } from "@nestjs/common";
import { createHash } from "crypto";

interface StoredRefreshToken {
  username: string;
  familyId: string;
  expiresAt: number;
  used: boolean;
}

export type ConsumeResult =
  | { status: "valid"; username: string; familyId: string }
  | { status: "reused"; username: string; familyId: string }
  | { status: "unknown" };

// A refresh token carries a random jti and an HMAC signature, so it cannot be
// guessed and a fast SHA-256 is enough; a slow hash like bcrypt only matters
// for low-entropy secrets such as passwords.
export function hashToken(token: string): string {
  return createHash("sha256").update(token).digest("hex");
}

// Only hashes are stored: a leak of this store does not hand out usable tokens.
// In-memory for now, so sessions do not survive a restart.
@Injectable()
export class RefreshTokenStore {
  private readonly tokens = new Map<string, StoredRefreshToken>();

  save(token: string, username: string, familyId: string, expiresAtSeconds: number): void {
    this.pruneExpired();
    this.tokens.set(hashToken(token), { username, familyId, expiresAt: expiresAtSeconds * 1000, used: false });
  }

  // Marks the token used. A token that was already used means it was copied:
  // the whole family is revoked, so neither the thief nor the user can carry on
  // with that session.
  consume(token: string): ConsumeResult {
    const entry = this.tokens.get(hashToken(token));
    if (!entry || entry.expiresAt <= Date.now()) {
      return { status: "unknown" };
    }
    if (entry.used) {
      this.revokeFamily(entry.familyId);
      return { status: "reused", username: entry.username, familyId: entry.familyId };
    }
    entry.used = true;
    return { status: "valid", username: entry.username, familyId: entry.familyId };
  }

  revokeFamily(familyId: string): void {
    for (const [hash, entry] of this.tokens) {
      if (entry.familyId === familyId) {
        this.tokens.delete(hash);
      }
    }
  }

  private pruneExpired(): void {
    const now = Date.now();
    for (const [hash, entry] of this.tokens) {
      if (entry.expiresAt <= now) {
        this.tokens.delete(hash);
      }
    }
  }
}
