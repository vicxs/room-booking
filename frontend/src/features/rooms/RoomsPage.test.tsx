import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RoomsPage } from "./RoomsPage";

type Reply = () => Promise<Response>;

function json(status: number, body: unknown, contentType = "application/json"): Reply {
  return async () => new Response(JSON.stringify(body), { status, headers: { "Content-Type": contentType } });
}

/** Stubs `fetch` to answer with each reply in turn, repeating the last one. */
function serverReplies(...replies: Reply[]) {
  const fetch = vi.fn(async () => {
    const reply = replies.length > 1 ? replies.shift() : replies[0];
    if (!reply) throw new Error("No reply stubbed");
    return reply();
  });
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <RoomsPage />
    </QueryClientProvider>,
  );
}

function rowTexts() {
  const [, ...bodyRows] = screen.getAllByRole("row");
  return bodyRows.map((row) =>
    within(row)
      .getAllByRole("cell")
      .map((cell) => cell.textContent),
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

test("shows that the rooms are loading", async () => {
  serverReplies(() => new Promise<Response>(() => {}));
  renderPage();

  expect(screen.getByRole("heading", { name: "Rooms" })).toBeVisible();
  expect(await screen.findByRole("status")).toHaveTextContent("Loading rooms…");
});

test("says there are no rooms yet when the catalogue is empty", async () => {
  serverReplies(json(200, []));
  renderPage();

  expect(await screen.findByText("No rooms yet")).toBeVisible();
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
});

test("lists the rooms in the order the server sends them", async () => {
  serverReplies(
    json(200, [
      { id: "6f1c0a3e-1111-4c1a-9a40-000000000001", name: "Zeta", capacity: 2, status: "ACTIVE" },
      { id: "6f1c0a3e-1111-4c1a-9a40-000000000002", name: "alpha", capacity: 12, status: "ACTIVE" },
    ]),
  );
  renderPage();

  await screen.findByRole("table");
  expect(screen.getAllByRole("columnheader").map((header) => header.textContent)).toEqual([
    "Name",
    "Capacity",
    "Status",
  ]);
  expect(rowTexts()).toEqual([
    ["Zeta", "2", "Active"],
    ["alpha", "12", "Active"],
  ]);
});

test("dims a deactivated room and marks it as deactivated", async () => {
  serverReplies(
    json(200, [
      { id: "6f1c0a3e-1111-4c1a-9a40-000000000001", name: "Sala A", capacity: 4, status: "ACTIVE" },
      { id: "6f1c0a3e-1111-4c1a-9a40-000000000002", name: "Sala B", capacity: 6, status: "DEACTIVATED" },
    ]),
  );
  renderPage();

  const active = (await screen.findByRole("cell", { name: "Sala A" })).closest("tr");
  const deactivated = screen.getByRole("cell", { name: "Sala B" }).closest("tr");
  expect(deactivated).toHaveTextContent("Deactivated");
  expect(deactivated).toHaveClass("text-muted-foreground");
  expect(active).not.toHaveClass("text-muted-foreground");
});

test("shows the server's explanation when the rooms cannot be loaded, and retries", async () => {
  const fetch = serverReplies(
    json(
      500,
      { type: "about:blank", title: "Internal Server Error", status: 500, detail: "An unexpected error occurred." },
      "application/problem+json",
    ),
    json(200, [{ id: "6f1c0a3e-1111-4c1a-9a40-000000000001", name: "Sala A", capacity: 4, status: "ACTIVE" }]),
  );
  renderPage();

  const alert = await screen.findByRole("alert");
  expect(alert).toHaveTextContent("The rooms could not be loaded");
  expect(alert).toHaveTextContent("An unexpected error occurred.");

  await userEvent.click(within(alert).getByRole("button", { name: "Try again" }));

  expect(await screen.findByRole("cell", { name: "Sala A" })).toBeVisible();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  expect(fetch).toHaveBeenCalledTimes(2);
});

test("suggests checking the connection when the server cannot be reached", async () => {
  serverReplies(async () => {
    throw new TypeError("Failed to fetch");
  });
  renderPage();

  const alert = await screen.findByRole("alert");
  expect(alert).toHaveTextContent("The rooms could not be loaded");
  expect(alert).toHaveTextContent("Check your connection and try again.");
});
