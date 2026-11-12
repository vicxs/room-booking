import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { routes } from "./router";

afterEach(() => {
  vi.unstubAllGlobals();
});

test("opening the app at / lands on the Rooms page", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => new Response("[]", { status: 200, headers: { "Content-Type": "application/json" } })),
  );
  const router = createMemoryRouter(routes, { initialEntries: ["/"] });

  render(
    <QueryClientProvider client={new QueryClient()}>
      <RouterProvider router={router} future={{ v7_startTransition: true }} />
    </QueryClientProvider>,
  );

  expect(await screen.findByRole("heading", { name: "Rooms" })).toBeVisible();
  expect(router.state.location.pathname).toBe("/rooms");
});
