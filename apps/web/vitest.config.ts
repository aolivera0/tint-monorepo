import { defineConfig, mergeConfig } from 'vitest/config';
import viteConfig from './vite.config';

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/setupTests.ts'],
      coverage: {
        provider: 'v8',
        reporter: ['text', 'lcov', 'json-summary'],
        include: ['src/**/*.{ts,tsx}'],
        exclude: ['src/main.tsx', 'src/setupTests.ts', 'src/**/*.test.{ts,tsx}', 'src/vite-env.d.ts'],
        // Mandato TDD de TINT: el pipeline falla si cualquier métrica cae bajo 85%.
        thresholds: { lines: 85, functions: 85, branches: 85, statements: 85 },
      },
    },
  }),
);
