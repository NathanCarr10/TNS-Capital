import 'reflect-metadata';
import { Logger } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { AppModule, configureApp } from './app.module';

async function bootstrap(): Promise<void> {
  const app = configureApp(await NestFactory.create(AppModule));
  app.enableShutdownHooks();

  const port = Number(process.env.PORT ?? 4000);
  await app.listen(port);
  Logger.log(`Auth service listening on http://localhost:${port}`, 'Bootstrap');
}

bootstrap().catch((error: Error) => {
  // e.g. JWT_SECRET missing or too short: refuse to start
  Logger.error(error.message, 'Bootstrap');
  process.exit(1);
});
