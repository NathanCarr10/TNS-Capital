import { Injectable, Logger, OnApplicationBootstrap } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { hashPassword, verifyPassword } from './password-hasher';
import { UsersService } from './users.service';

/**
 * Creates the admin account from ADMIN_PASSWORD when the service starts, instead of seeding
 * a known password into every database (OWASP A07-4).
 *
 * - No ADMIN_PASSWORD: nothing changes, with a warning (docker-compose.yml requires it).
 * - No admin yet: the admin is created with the ADMIN role and no trading account.
 * - The admin's password differs: it is replaced, so changing ADMIN_PASSWORD and restarting
 *   rotates it, and databases seeded with the old default stop accepting it.
 * - A trader already has the username: left alone, with an error; it is never promoted.
 */
@Injectable()
export class AdminBootstrap implements OnApplicationBootstrap {
  private readonly logger = new Logger(AdminBootstrap.name);

  constructor(
    private readonly config: ConfigService,
    private readonly users: UsersService,
  ) {}

  async onApplicationBootstrap(): Promise<void> {
    const password = this.config.get<string>('ADMIN_PASSWORD');
    const username = this.config.get<string>('ADMIN_USERNAME') || 'admin';
    if (!password) {
      this.logger.warn('ADMIN_PASSWORD is not set: no admin account is created or updated');
      return;
    }

    const existing = await this.users.findByUsername(username);
    if (existing?.roles.includes('ADMIN') && (await verifyPassword(password, existing.passwordHash))) {
      return;
    }
    try {
      await this.users.saveAdmin(username, await hashPassword(password));
      this.logger.log(`Admin account "${username}" ${existing ? 'password updated' : 'created'} from ADMIN_PASSWORD`);
    } catch (error) {
      this.logger.error((error as Error).message);
    }
  }
}
