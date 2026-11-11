import { defineConfig, devices } from "@playwright/test";

const isCI = Boolean(process.env.CI);
const backendJar = "../backend/target/room-booking-backend-0.0.1-SNAPSHOT.jar";

/**
 * Runs against the production build: PostgreSQL from compose.yaml, the backend jar
 * and `vite preview` proxying /api to it. Locally, already-running servers are reused.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: isCI,
  retries: 0,
  reporter: isCI ? [["github"], ["html", { open: "never" }]] : [["list"]],
  use: {
    baseURL: "http://localhost:4173",
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: [
    {
      name: "backend",
      command: `docker compose -f ../compose.yaml up --detach --wait postgres && java -jar ${backendJar}`,
      url: "http://localhost:8080/api/health",
      reuseExistingServer: !isCI,
      timeout: 120_000,
      stdout: "pipe",
    },
    {
      name: "frontend",
      command: "pnpm preview",
      url: "http://localhost:4173",
      reuseExistingServer: !isCI,
    },
  ],
});
