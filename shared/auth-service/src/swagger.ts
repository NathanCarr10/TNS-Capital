import { INestApplication } from '@nestjs/common';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';

export const DOCS_PATH = 'docs';

/**
 * Serves live OpenAPI docs generated from the controllers and DTOs:
 * Swagger UI at /docs and the JSON document at /docs-json. Both are public,
 * like /health; contracts/auth-api.yaml remains the agreed contract.
 */
export function setupSwagger(app: INestApplication): void {
  const config = new DocumentBuilder()
    .setTitle('TNS Capital Auth Service')
    .setDescription('Registers users, issues access and refresh tokens, and rotates refresh tokens.')
    .setVersion('2.0.0')
    .addBearerAuth({ type: 'http', scheme: 'bearer', bearerFormat: 'JWT' })
    .build();
  SwaggerModule.setup(DOCS_PATH, app, () => SwaggerModule.createDocument(app, config));
}
