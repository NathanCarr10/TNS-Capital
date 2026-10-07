import { Global, Inject, Logger, Module, OnApplicationShutdown } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Pool } from 'pg';

export const PG_POOL = Symbol('PG_POOL');

/**
 * Provides one shared Postgres connection pool. The pool connects lazily, on
 * the first query.
 */
@Global()
@Module({
  providers: [
    {
      provide: PG_POOL,
      inject: [ConfigService],
      useFactory: (config: ConfigService): Pool => {
        const pool = new Pool({
          // In Docker: use 'postgres' (service name). Locally: use localhost or env var
          host: config.get<string>('DB_HOST', 'postgres'),
          port: Number(config.get('DB_PORT', 5432)),
          database: config.get<string>('DB_NAME', 'tns_capital'),
          user: config.get<string>('DB_USER', 'tns-capital-db-user'),
          password: config.get<string>('DB_PASSWORD'),
        });
        // An idle client dropping (e.g. Postgres restarting) must not crash the service;
        // the pool discards the broken client and opens a new one on the next query
        pool.on('error', (err) => new Logger('Database').error(`Idle client error: ${err.message}`));
        return pool;
      },
    },
  ],
  exports: [PG_POOL],
})
export class DatabaseModule implements OnApplicationShutdown {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async onApplicationShutdown(): Promise<void> {
    await this.pool.end();
  }
}
