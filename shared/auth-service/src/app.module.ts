import { INestApplication, Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { APP_INTERCEPTOR } from '@nestjs/core';
import type { NextFunction, Request, Response } from 'express';
import { AuthModule } from './auth/auth.module';
import { parseTrustProxy } from './auth/rate-limit';
import { ApiExceptionFilter } from './common/api-exception.filter';
import { metrics } from './common/metrics';
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
  // Take the client's IP from the frontend's nginx, for the rate limit and the login log
  const trustProxy = parseTrustProxy(app.get(ConfigService).get<string>('AUTH_TRUST_PROXY'));
  app.getHttpAdapter().getInstance().set('trust proxy', trustProxy);
  // Every response's status, guard rejections (429) included, for the metrics
  app.use((_request: Request, response: Response, next: NextFunction) => {
    response.on('finish', () => metrics.httpResponse(response.statusCode));
    next();
  });
  app.useGlobalPipes(createValidationPipe());
  app.useGlobalFilters(new ApiExceptionFilter());
  setupSwagger(app);
  return app;
}
