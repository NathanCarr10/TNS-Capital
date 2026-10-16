// Angular's default Karma setup, plus an lcov coverage report for SonarQube
// (`npm test -- --code-coverage` writes coverage/frontend/lcov.info).
module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
    client: {
      jasmine: {},
      clearContext: false, // keep the Jasmine Spec Runner output visible in the browser
    },
    jasmineHtmlReporter: {
      suppressAll: true, // removes the duplicated traces
    },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/frontend'),
      subdir: '.',
      reporters: [
        { type: 'html' },
        { type: 'text-summary' },
        // Paths from the repo root (frontend/src/app/...), where SonarQube resolves them
        { type: 'lcovonly', file: 'lcov.info', projectRoot: require('path').join(__dirname, '..') },
      ],
    },
    reporters: ['progress', 'kjhtml'],
    browsers: ['Chrome'],
    // Containers and CI agents run Chrome as root or without user namespaces
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox'],
      },
    },
    restartOnFileChange: true,
  });
};
