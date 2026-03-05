import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  webServer: { command: 'npm run dev -- --host 0.0.0.0 --port 4173', port: 4173, reuseExistingServer: true },
  use: { baseURL: 'http://127.0.0.1:4173', headless: true }
});
