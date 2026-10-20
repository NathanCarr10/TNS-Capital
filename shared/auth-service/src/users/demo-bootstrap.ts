import { Injectable, Logger, OnApplicationBootstrap } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { hashPassword, verifyPassword } from './password-hasher';
import { UsersService } from './users.service';

const DEMO_EMAIL = 'jane.doe@example.com';
const DEMO_ACCOUNT = 'ACC-DEMO-JANE';

@Injectable()
export class DemoBootstrap implements OnApplicationBootstrap {
  private readonly logger = new Logger(DemoBootstrap.name);

  constructor(
    private readonly config: ConfigService,
    private readonly users: UsersService,
  ) {}

  async onApplicationBootstrap(): Promise<void> {
    const password = this.config.get<string>('DEMO_PASSWORD');
    if (!password) {
      this.logger.warn('DEMO_PASSWORD is not set: Jane Doe demo login is disabled');
      return;
    }

    const existing = await this.users.findByEmail(DEMO_EMAIL);
    if (existing && (await verifyPassword(password, existing.passwordHash))) {
      return;
    }

    try {
      await this.users.saveDemoUser(DEMO_EMAIL, await hashPassword(password), DEMO_ACCOUNT);
      this.logger.log(`Demo user "${DEMO_EMAIL}" is ready from DEMO_PASSWORD`);
    } catch (error) {
      this.logger.error((error as Error).message);
    }
  }
}