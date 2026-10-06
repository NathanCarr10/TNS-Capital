import "reflect-metadata";
import { NestFactory } from "@nestjs/core";
import { AppModule, configureApp } from "./app.module";
import { loadConfig } from "./config";

async function bootstrap() {
  const app = await NestFactory.create(AppModule.forConfig(loadConfig()));
  configureApp(app);
  const port = Number(process.env.PORT) || 3000;
  await app.listen(port);
  console.log(`tns-capital-auth-service listening on http://localhost:${port}`);
}

bootstrap();
