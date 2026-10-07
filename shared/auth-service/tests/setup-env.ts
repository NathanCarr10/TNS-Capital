// AppModule validates the environment when it is imported, so the secret must
// be set before any test file loads it. A real JWT_SECRET from the environment wins.
process.env.JWT_SECRET ??= 'unit-test-shared-secret-at-least-32-bytes-long';
