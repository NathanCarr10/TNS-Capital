import { EnvironmentProviders } from '@angular/core';

import { provideApi as provideAuthApi } from '../../api/auth';
import { provideApi as provideStrategyApi } from '../../api/strategy';
import { provideApi as provideTradingApi } from '../../api/trading';

// The generated clients default to the specs' localhost server URLs. An empty
// base path keeps requests relative, so proxy.conf.json routes /auth and /api
// (and /api/v1/strategies to the strategy service).
export function provideApiClients(): EnvironmentProviders[] {
  return [provideAuthApi(''), provideTradingApi(''), provideStrategyApi('')];
}
