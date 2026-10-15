import { FullConfig, request } from '@playwright/test';

const READY_TIMEOUT_MS = 180_000;
const POLL_INTERVAL_MS = 2_000;

/**
 * Waits until every service the journeys use answers through the app's origin. Containers
 * report "started" long before Spring Boot or Streamlit can serve a request.
 */
export default async function globalSetup(config: FullConfig): Promise<void> {
  const baseURL = config.projects[0].use.baseURL;
  const api = await request.newContext({ baseURL });

  // [path, status that means "up"]: both APIs refuse a request without a token, and
  // /auth/me is not rate limited like /auth/login
  const checks: [string, number][] = [
    ['/', 200],
    ['/auth/me', 401],
    ['/api/v1/instruments', 401],
    ['/analytics/_stcore/health', 200],
  ];

  const deadline = Date.now() + READY_TIMEOUT_MS;
  let waitingFor = checks.map(([path]) => path);
  while (waitingFor.length > 0) {
    const results = await Promise.all(
      checks.map(async ([path, status]) => {
        const response = await api.get(path, { failOnStatusCode: false }).catch(() => null);
        return response?.status() === status ? null : path;
      })
    );
    waitingFor = results.filter((path): path is string => path !== null);
    if (waitingFor.length === 0) {
      break;
    }
    if (Date.now() > deadline) {
      throw new Error(`The stack at ${baseURL} is not ready; still waiting for ${waitingFor.join(', ')}`);
    }
    await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
  }

  await api.dispose();
}
