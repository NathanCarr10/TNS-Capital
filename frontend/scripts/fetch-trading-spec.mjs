// Downloads the trading API's OpenAPI spec from a running Spring Boot app
// (springdoc serves it at /v3/api-docs) into contracts/trading-api.json.
// Run `npm run generate:api` afterwards to regenerate the Angular clients.
import { writeFile } from 'node:fs/promises';

const baseUrl = process.env.TRADING_API_URL ?? 'http://localhost:3000';
const target = new URL('../../contracts/trading-api.json', import.meta.url);

const response = await fetch(`${baseUrl}/v3/api-docs`);
if (!response.ok) {
  console.error(`GET ${baseUrl}/v3/api-docs returned ${response.status}`);
  process.exit(1);
}
const spec = await response.json();
await writeFile(target, JSON.stringify(spec, null, 2) + '\n');
console.log(`Wrote ${target.pathname}`);
