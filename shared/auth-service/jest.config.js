module.exports = {
  testEnvironment: 'node',
  preset: 'ts-jest',
  testMatch: ['**/tests/**/*.spec.ts'],
  setupFiles: ['<rootDir>/tests/setup-env.ts'],
  collectCoverageFrom: ['src/**/*.ts', '!src/main.ts'],
  // lcov paths from the repo root (shared/auth-service/src/...), where SonarQube resolves them
  coverageReporters: ['text-summary', 'html', ['lcov', { projectRoot: '../..' }]],
  coverageThreshold: {
    global: {
      branches: 80,
      functions: 80,
      lines: 80,
      statements: 80,
    },
  },
};
