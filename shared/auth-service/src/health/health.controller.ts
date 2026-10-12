import { All, Controller, Get } from '@nestjs/common';
import { ApiExcludeEndpoint, ApiTags } from '@nestjs/swagger';
import { Public } from '../auth/decorators/public.decorator';
import { methodNotAllowed } from '../common/method-not-allowed';

@ApiTags('health')
@Public()
@Controller('health')
export class HealthController {
  @Get()
  health(): { status: 'up' } {
    return { status: 'up' };
  }

  @All()
  @ApiExcludeEndpoint()
  methodNotAllowed(): never {
    return methodNotAllowed();
  }
}
