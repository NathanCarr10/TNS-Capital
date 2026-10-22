// AppModule validates the environment when it is imported, so the secret must
// be set before any test file loads it. A real JWT_SECRET from the environment wins.
process.env.JWT_SECRET ??= 'unit-test-shared-secret-at-least-32-bytes-long';

// The contract tests log in many times from one IP; rate-limit.spec.ts tests the limit itself
process.env.AUTH_RATE_LIMIT ??= '1000';

// The strategy service's client secret, so POST /auth/token has a client to test against
process.env.STRATEGY_CLIENT_SECRET ??= 'unit-test-strategy-client-secret-32-bytes';
