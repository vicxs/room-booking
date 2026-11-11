import { Configuration, HealthApi } from "@/api/generated";

// The base path comes from the Contract's `servers` entry (`/api`); the dev
// and preview servers proxy it to the backend.
const configuration = new Configuration();

export const healthApi = new HealthApi(configuration);
