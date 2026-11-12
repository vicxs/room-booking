import { type Problem, ProblemFromJSON, ResponseError } from "@/api/generated";

/** A failed API call, with the Problem (RFC 9457, ADR-0003) the server answered, if it answered one. */
export class ApiError extends Error {
  readonly problem: Problem | undefined;

  constructor(problem: Problem | undefined, options?: ErrorOptions) {
    super(problem?.detail ?? problem?.title ?? "The API call failed", options);
    this.name = "ApiError";
    this.problem = problem;
  }
}

/**
 * Runs a call of the generated client and, when it fails, throws an {@link ApiError} carrying the Problem read
 * from the response. Every API call goes through it, so every error is read the same way.
 */
export async function callApi<T>(call: () => Promise<T>): Promise<T> {
  try {
    return await call();
  } catch (error) {
    throw new ApiError(await readProblem(error), { cause: error });
  }
}

/**
 * The `type` of a Problem for a business rule the client must tell apart, `urn:room-booking:problem:<slug>`
 * (ADR-0003). The generated client has no constant for it: the Contract names each one only in the description of
 * its response.
 */
export function problemType(slug: string): string {
  return `urn:room-booking:problem:${slug}`;
}

/** A sentence for the user saying why a call failed: the server's own words, or a hint when it was not reached. */
export function explain(error: Error): string {
  if (error instanceof ApiError && error.problem) return error.problem.detail ?? error.problem.title;
  return "Check your connection and try again.";
}

async function readProblem(error: unknown): Promise<Problem | undefined> {
  if (!(error instanceof ResponseError)) return undefined;
  if (!error.response.headers.get("Content-Type")?.startsWith("application/problem+json")) return undefined;
  try {
    return ProblemFromJSON(await error.response.json());
  } catch {
    return undefined;
  }
}
