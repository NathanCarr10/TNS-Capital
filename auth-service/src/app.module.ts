import { DynamicModule, INestApplication, Module, ValidationPipe } from "@nestjs/common";
import { AuthController } from "./auth.controller";
import { AuthService } from "./auth.service";
import { AUTH_CONFIG, AuthConfig } from "./config";
import { HealthController } from "./health.controller";
import { JwtAuthGuard } from "./jwt-auth.guard";
import { RefreshTokenStore } from "./refresh-token.store";
import { TokenService } from "./token.service";
import { UserStore } from "./user.store";

@Module({})
export class AppModule {
  static forConfig(config: AuthConfig): DynamicModule {
    return {
      module: AppModule,
      controllers: [AuthController, HealthController],
      providers: [
        { provide: AUTH_CONFIG, useValue: config },
        AuthService,
        TokenService,
        JwtAuthGuard,
        RefreshTokenStore,
        UserStore,
      ],
    };
  }
}

export function configureApp(app: INestApplication): void {
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true }));
}
