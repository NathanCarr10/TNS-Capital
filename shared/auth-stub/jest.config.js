module.exports = {
  testEnvironment: 'node',
  testMatch: ['**/tests/**/*.spec.js'],
  collectCoverageFrom: ['server.js'],
  coverageThreshold: {
    global: {
      branches: 80,
      functions: 80,
      lines: 80,
      statements: 80,
    },
  },
};
