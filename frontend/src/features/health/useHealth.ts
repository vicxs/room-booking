import { useQuery } from "@tanstack/react-query";
import { healthApi } from "@/api/client";
import { type Health, HealthFromJSON, ResponseError } from "@/api/generated";

/** The service answers 503 with a `DOWN` body when its database is unreachable. */
async function fetchHealth(): Promise<Health> {
  try {
    return await healthApi.getHealth();
  } catch (error) {
    if (error instanceof ResponseError && error.response.status === 503) {
      return HealthFromJSON(await error.response.json());
    }
    throw error;
  }
}

export function useHealth() {
  return useQuery({ queryKey: ["health"], queryFn: fetchHealth, retry: false });
}
