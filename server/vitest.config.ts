import { defineConfig } from 'vitest/config';
export default defineConfig({
  test: {
    globalSetup: './tests/globalSetup.ts',
    fileParallelism: false,
    testTimeout: 30_000,
    env: {
      NODE_ENV: 'test',
      DATABASE_URL: process.env.TEST_DATABASE_URL ?? 'postgres://almacen:almacen_dev@localhost:5432/almacen_test',
      BCRYPT_ROUNDS: '4',
    },
  },
});
