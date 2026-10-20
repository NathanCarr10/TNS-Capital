/**
 * The Prometheus counters for the monitoring stack, and the server that
 * exposes them on METRICS_PORT.
 */
import { AddressInfo } from 'node:net';
import { metrics, renderMetrics, resetMetrics, startMetricsServer } from '../src/common/metrics';

describe('metrics', () => {
  beforeEach(() => resetMetrics());

  it('starts every alerting counter at zero', () => {
    const text = renderMetrics();
    expect(text).toContain('auth_logins_total{outcome="failure"} 0');
    expect(text).toContain('auth_refresh_token_reuse_total 0');
    expect(text).toContain('auth_http_responses_total{status="429"} 0');
  });

  it('counts logins, token reuse and responses by status', () => {
    metrics.loginFailed();
    metrics.loginFailed();
    metrics.loginSucceeded();
    metrics.refreshTokenReused();
    metrics.httpResponse(401);
    metrics.httpResponse(403);

    const text = renderMetrics();
    expect(text).toContain('auth_logins_total{outcome="failure"} 2');
    expect(text).toContain('auth_logins_total{outcome="success"} 1');
    expect(text).toContain('auth_refresh_token_reuse_total 1');
    expect(text).toContain('auth_http_responses_total{status="401"} 1');
    expect(text).toContain('auth_http_responses_total{status="403"} 1');
    expect(text).toMatch(/^process_resident_memory_bytes \d+$/m);
  });

  it('serves GET /metrics and nothing else', async () => {
    const server = startMetricsServer(0);
    await new Promise((resolve) => server.once('listening', resolve));
    const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
    try {
      const response = await fetch(`${base}/metrics`);
      expect(response.status).toBe(200);
      expect(await response.text()).toContain('# TYPE auth_logins_total counter');
      expect((await fetch(`${base}/`)).status).toBe(404);
    } finally {
      server.close();
    }
  });
});
