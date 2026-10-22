import { Injectable, Logger, OnApplicationBootstrap } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { hashPassword, verifyPassword } from './password-hasher';
import { normaliseEmail, StaffRole, UsersService } from './users.service';

/**
 * The staff accounts, each created from its own password variable. The two roles are kept
 * apart (docs/DECISIONS.md, "Roles"):
 * - SUPPORT works in the trading app: views every account, suspends or reactivates them.
 * - ADMIN runs the analytics dashboard and operations (DLQ, metrics); no trader data in the app.
 */
const STAFF: { role: StaffRole; env: string; defaultEmail: string }[] = [
  { role: 'ADMIN', env: 'ADMIN', defaultEmail: 'admin@tns-capital.local' },
  { role: 'SUPPORT', env: 'SUPPORT', defaultEmail: 'support@tns-capital.local' },
];

/**
 * Creates the staff accounts when the service starts, instead of seeding a known password
 * into every database (OWASP A07-4). For each role, from <ROLE>_PASSWORD and <ROLE>_EMAIL:
 *
 * - No password: nothing changes, with a warning (docker-compose.yml requires both).
 * - No such user yet: it is created with the role and no trading account.
 * - The password differs: it is replaced, so changing the variable and restarting rotates it,
 *   and databases seeded with the old default stop accepting it.
 * - Another user already has the email (a trader, or the other staff role): left alone,
 *   with an error; a role is never changed this way.
 */
@Injectable()
export class StaffBootstrap implements OnApplicationBootstrap {
  private readonly logger = new Logger(StaffBootstrap.name);

  constructor(
    private readonly config: ConfigService,
    private readonly users: UsersService,
  ) {}

  async onApplicationBootstrap(): Promise<void> {
    for (const staff of STAFF) {
      await this.ensure(staff.role, staff.env, staff.defaultEmail);
    }
  }

  private async ensure(role: StaffRole, env: string, defaultEmail: string): Promise<void> {
    const password = this.config.get<string>(`${env}_PASSWORD`);
    const email = normaliseEmail(this.config.get<string>(`${env}_EMAIL`) || defaultEmail);
    if (!password) {
      this.logger.warn(`${env}_PASSWORD is not set: no ${role} account is created or updated`);
      return;
    }

    const existing = await this.users.findByEmail(email);
    if (existing?.roles.includes(role) && (await verifyPassword(password, existing.passwordHash))) {
      return;
    }
    try {
      await this.users.saveStaff(email, await hashPassword(password), role);
      this.logger.log(`${role} account "${email}" ${existing ? 'password updated' : 'created'} from ${env}_PASSWORD`);
    } catch (error) {
      this.logger.error((error as Error).message);
    }
  }
}
