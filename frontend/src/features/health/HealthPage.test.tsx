import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { HealthPage } from "./HealthPage";

function respondWith(status: number, body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } })),
  );
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <HealthPage />
    </QueryClientProvider>,
  );
}

async function expectServiceStatus(label: string) {
  await waitFor(() => expect(screen.getByRole("status", { name: "Service status" })).toHaveTextContent(label));
}

afterEach(() => {
  vi.unstubAllGlobals();
});

test("shows the service as up when the API reports UP", async () => {
  respondWith(200, { status: "UP" });
  renderPage();

  await expectServiceStatus("Up");
});

test("shows the service as down when the API reports DOWN", async () => {
  respondWith(503, { status: "DOWN" });
  renderPage();

  await expectServiceStatus("Down");
});

test("shows the service as unreachable when the API cannot be reached", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => {
      throw new TypeError("Failed to fetch");
    }),
  );
  renderPage();

  await expectServiceStatus("Unreachable");
});
