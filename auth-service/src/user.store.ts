import { Injectable } from "@nestjs/common";
import * as bcrypt from "bcrypt";

export interface User {
  username: string;
  roles: string[];
}

interface StoredUser extends User {
  passwordHash: string;
}

const SALT_ROUNDS = 10;

// Same accounts and roles as shared/auth-stub, so this service is a drop-in
// replacement for it. Stand-in until registration and the Postgres users table land.
const SEED_USERS = [
  { username: "alice", password: "mission123", roles: ["MISSION_OPERATOR", "ADMIN"] },
  { username: "bob", password: "wrongpermissions", roles: ["GUEST"] },
];

@Injectable()
export class UserStore {
  private readonly users = new Map<string, StoredUser>();
  private readonly ready: Promise<void> = this.seed();

  async verifyCredentials(username: string, password: string): Promise<User | undefined> {
    await this.ready;
    const user = this.users.get(username);
    if (!user || !(await bcrypt.compare(password, user.passwordHash))) {
      return undefined;
    }
    return { username: user.username, roles: user.roles };
  }

  async find(username: string): Promise<User | undefined> {
    await this.ready;
    const user = this.users.get(username);
    return user && { username: user.username, roles: user.roles };
  }

  private async seed(): Promise<void> {
    for (const { username, password, roles } of SEED_USERS) {
      const passwordHash = await bcrypt.hash(password, SALT_ROUNDS);
      this.users.set(username, { username, roles, passwordHash });
    }
  }
}
