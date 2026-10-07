import { All, Controller, Get } from '@nestjs/common';
import { Public } from '../auth/decorators/public.decorator';
import { methodNotAllowed } from '../common/method-not-allowed';

@Public()
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
