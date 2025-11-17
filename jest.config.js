module.exports = {
  testEnvironment: 'node',
  modulePathIgnorePatterns: [
    '<rootDir>/example/node_modules',
    '<rootDir>/lib/',
  ],
  setupFiles: ['<rootDir>/jest.setup.js'],
  testMatch: ['**/__tests__/**/*.test.ts'],
  transform: {
    '^.+\\.tsx?$': [
      'babel-jest',
      {
        presets: [
          '@babel/preset-typescript',
          ['@babel/preset-env', { targets: { node: 'current' } }],
        ],
      },
    ],
  },
};
