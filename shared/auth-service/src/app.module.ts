import { INestApplication, Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { APP_INTERCEPTOR } from '@nestjs/core';
import { AuthModule } from './auth/auth.module';
import { ApiExceptionFilter } from './common/api-exception.filter';
import { RequestLoggingInterceptor } from './common/request-logging.interceptor';
import { createValidationPipe } from './common/validation';
import { validateEnv } from './config/env.validation';
import { DatabaseModule } from './database/database.module';
import { HealthController } from './health/health.controller';
import { setupSwagger } from './swagger';

@Module({
  imports: [
    // Reads .env from the working directory; real environment variables win
    ConfigModule.forRoot({ isGlobal: true, validate: validateEnv }),
    DatabaseModule,
    AuthModule,
  ],
  controllers: [HealthController],
  // One log line per request, for every route
  providers: [{ provide: APP_INTERCEPTOR, useClass: RequestLoggingInterceptor }],
})
export class AppModule {}

/**
 * Applies the request validation, error envelope and live API docs. Shared
 * by main.ts and the tests so both run the same pipeline.
 */
export function configureApp<T extends INestApplication>(app: T): T {
  app.useGlobalPipes(createValidationPipe());
  app.useGlobalFilters(new ApiExceptionFilter());
  setupSwagger(app);
  return app;
}
