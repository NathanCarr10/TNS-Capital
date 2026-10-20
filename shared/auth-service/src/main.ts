import 'reflect-metadata';
import { ConsoleLogger, Logger } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { AppModule, configureApp } from './app.module';
import { startMetricsServer } from './common/metrics';

async function bootstrap(): Promise<void> {
  // LOG_FORMAT=json (as in Docker Compose): one JSON object per line, like the Java services' ECS logs
  const logger = process.env.LOG_FORMAT === 'json' ? new ConsoleLogger({ json: true }) : undefined;
  const app = configureApp(await NestFactory.create(AppModule, { logger }));
  app.enableShutdownHooks();

  const port = Number(process.env.PORT ?? 4000);
  await app.listen(port);
  Logger.log(`Auth service listening on http://localhost:${port}`, 'Bootstrap');

  // For the monitoring stack; off unless METRICS_PORT is set
  if (process.env.METRICS_PORT) {
    startMetricsServer(Number(process.env.METRICS_PORT));
    Logger.log(`Serving metrics on port ${process.env.METRICS_PORT}`, 'Bootstrap');
  }
}

bootstrap().catch((error: Error) => {
  // e.g. JWT_SECRET missing or too short: refuse to start
  Logger.error(error.message, 'Bootstrap');
  process.exit(1);
});
