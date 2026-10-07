import { All, Controller, Get } from '@nestjs/common';
import { methodNotAllowed } from '../common/method-not-allowed';

@Controller('health')
export class HealthController {
  @Get()
  health(): { status: 'up' } {
    return { status: 'up' };
  }

  @All()
  methodNotAllowed(): never {
    return methodNotAllowed();
  }
}
