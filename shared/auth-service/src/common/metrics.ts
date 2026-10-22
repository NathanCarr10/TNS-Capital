import { createServer, Server } from 'node:http';

/**
 * The security events the monitoring stack alerts on (OWASP A09-5), as
 * Prometheus counters, served at GET /metrics on METRICS_PORT:
 *
 * - auth_logins_total{outcome="success"|"failure"} (A09-2)
 * - auth_refresh_token_reuse_total (A09-1)
 * - auth_http_responses_total{status}: 401s and 429s show guessing and rate limiting
 * - process_resident_memory_bytes, nodejs_heap_used_bytes
 *
 * A handful of counters, so they are written out here rather than pulling in
 * prom-client. The port is not published and nginx does not proxy it, so
 * only containers on the Compose network can read it.
 */
type Labels = Record<string, string>;

interface Counter {
  help: string;
  values: Map<string, number>;
}

const counters = new Map<string, Counter>();

function counter(name: string, help: string, initial: Labels[] = [{}]): void {
  // Starting at 0 lets increase() see the very first event
  counters.set(name, { help, values: new Map(initial.map((labels) => [labelText(labels), 0])) });
}

function labelText(labels: Labels): string {
  const pairs = Object.entries(labels).map(([key, value]) => `${key}=${JSON.stringify(value)}`);
  return pairs.length ? `{${pairs.join(',')}}` : '';
}

function increment(name: string, labels: Labels = {}): void {
  const values = counters.get(name)!.values;
  const key = labelText(labels);
  values.set(key, (values.get(key) ?? 0) + 1);
}

export function resetMetrics(): void {
  counters.clear();
  counter('auth_logins_total', 'Login attempts by outcome', [{ outcome: 'success' }, { outcome: 'failure' }]);
  counter('auth_refresh_token_reuse_total', 'Spent refresh tokens presented again; the session was revoked');
  counter('auth_http_responses_total', 'HTTP responses by status code', [
    { status: '200' },
    { status: '401' },
    { status: '429' },
    { status: '500' },
  ]);
}
resetMetrics();

export const metrics = {
  loginSucceeded: () => increment('auth_logins_total', { outcome: 'success' }),
  loginFailed: () => increment('auth_logins_total', { outcome: 'failure' }),
  refreshTokenReused: () => increment('auth_refresh_token_reuse_total'),
  httpResponse: (status: number) => increment('auth_http_responses_total', { status: String(status) }),
};

/** All metrics in the Prometheus text format. */
export function renderMetrics(): string {
  const lines: string[] = [];
  for (const [name, { help, values }] of counters) {
    lines.push(`# HELP ${name} ${help}`, `# TYPE ${name} counter`);
    for (const [labels, value] of values) {
      lines.push(`${name}${labels} ${value}`);
    }
  }
  const memory = process.memoryUsage();
  lines.push(
    '# HELP process_resident_memory_bytes Resident memory size in bytes',
    '# TYPE process_resident_memory_bytes gauge',
    `process_resident_memory_bytes ${memory.rss}`,
    '# HELP nodejs_heap_used_bytes V8 heap in use in bytes',
    '# TYPE nodejs_heap_used_bytes gauge',
    `nodejs_heap_used_bytes ${memory.heapUsed}`,
  );
  return lines.join('\n') + '\n';
}

/** Serves GET /metrics on its own port; everything else is a 404. */
export function startMetricsServer(port: number): Server {
  return createServer((request, response) => {
    if (request.method === 'GET' && request.url === '/metrics') {
      response.writeHead(200, { 'Content-Type': 'text/plain; version=0.0.4; charset=utf-8' });
      response.end(renderMetrics());
    } else {
      response.writeHead(404).end();
    }
  }).listen(port);
}
