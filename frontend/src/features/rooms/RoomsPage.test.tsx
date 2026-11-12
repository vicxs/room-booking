import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
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

/** The JSON body of the nth request the page sent, counting from 0. */
function requestBody(fetch: ReturnType<typeof serverReplies>, n: number): unknown {
  const [, init] = fetch.mock.calls[n] as unknown as [RequestInfo, RequestInit];
  return JSON.parse(String(init.body));
}

/** The method and URL of the nth request the page sent, counting from 0. */
function request(fetch: ReturnType<typeof serverReplies>, n: number): { method: string | undefined; url: string } {
  const [input, init] = fetch.mock.calls[n] as unknown as [RequestInfo, RequestInit];
  return { method: init.method, url: String(input) };
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
      { id: "6f1c0a3e-1111-4c1a-9a40-000000000002", name: "alpha", capacity: 12, status: "DEACTIVATED" },
    ]),
  );
  renderPage();

  await screen.findByRole("table");
  expect(screen.getAllByRole("columnheader").map((header) => header.textContent)).toEqual([
    "Name",
    "Capacity",
    "Status",
    "Actions",
  ]);
  expect(rowTexts()).toEqual([
    ["Zeta", "2", "Active", "Edit", "Deactivate"],
    ["alpha", "12", "Deactivated", "Edit", "Reactivate"],
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

describe("creating a room", () => {
  const salaA = { id: "6f1c0a3e-1111-4c1a-9a40-000000000001", name: "Sala A", capacity: 4, status: "ACTIVE" };

  test("creates the room, closes the dialog and refetches the list", async () => {
    const fetch = serverReplies(json(200, []), json(201, salaA), json(200, [salaA]));
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "New room" }));
    const dialog = screen.getByRole("dialog", { name: "New room" });
    await userEvent.type(within(dialog).getByLabelText("Name"), "Sala A");
    await userEvent.type(within(dialog).getByLabelText("Capacity"), "4");
    await userEvent.click(within(dialog).getByRole("button", { name: "Create room" }));

    expect(await screen.findByRole("cell", { name: "Sala A" })).toBeVisible();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(fetch).toHaveBeenCalledTimes(3);
    expect(requestBody(fetch, 1)).toEqual({ name: "Sala A", capacity: 4 });
  });

  test("offers to create the first room when there are none", async () => {
    serverReplies(json(200, []));
    renderPage();

    const emptyState = (await screen.findByText("No rooms yet")).closest("div")?.parentElement as HTMLElement;
    await userEvent.click(within(emptyState).getByRole("button", { name: "New room" }));

    expect(screen.getByRole("dialog", { name: "New room" })).toBeVisible();
  });

  test("flags a missing name and capacity without sending anything", async () => {
    const fetch = serverReplies(json(200, []));
    const dialog = await openNewRoomDialog();

    await userEvent.type(within(dialog).getByLabelText("Name"), "   ");
    await userEvent.click(within(dialog).getByRole("button", { name: "Create room" }));

    expect(within(dialog).getByLabelText("Name")).toHaveAccessibleDescription("Enter a name.");
    expect(within(dialog).getByLabelText("Name")).toHaveFocus();
    expect(within(dialog).getByLabelText("Capacity")).toHaveAccessibleDescription("Enter a capacity.");
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  test("flags a name over 100 characters and a capacity that is not a whole number of at least 1", async () => {
    const fetch = serverReplies(json(200, []));
    const dialog = await openNewRoomDialog();
    const capacity = within(dialog).getByLabelText("Capacity");

    await userEvent.type(within(dialog).getByLabelText("Name"), "x".repeat(101));
    await userEvent.type(capacity, "0");
    await userEvent.click(within(dialog).getByRole("button", { name: "Create room" }));

    expect(within(dialog).getByLabelText("Name")).toHaveAccessibleDescription("Use at most 100 characters.");
    expect(capacity).toHaveAccessibleDescription("Enter a whole number of at least 1.");

    await userEvent.clear(capacity);
    await userEvent.type(capacity, "2.5");
    await userEvent.click(within(dialog).getByRole("button", { name: "Create room" }));

    expect(capacity).toHaveAccessibleDescription("Enter a whole number of at least 1.");
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  test("clears a field's error as soon as it is edited", async () => {
    serverReplies(json(200, []));
    const dialog = await openNewRoomDialog();
    const name = within(dialog).getByLabelText("Name");

    await userEvent.click(within(dialog).getByRole("button", { name: "Create room" }));
    await userEvent.type(name, "S");

    expect(name).not.toHaveAccessibleDescription("Enter a name.");
    expect(name).toBeValid();
  });

  test("shows each field the server rejects under that field, keeping the input", async () => {
    serverReplies(
      json(200, []),
      json(
        400,
        {
          type: "about:blank",
          title: "Bad Request",
          status: 400,
          detail: "Invalid request content.",
          errors: [
            { field: "capacity", message: "must be greater than or equal to 1" },
            { field: "name", message: "must not be blank" },
          ],
        },
        "application/problem+json",
      ),
    );
    const dialog = await fillNewRoomDialog("Sala A", "4");

    await userEvent.click(within(dialog).getByRole("button", { name: "Create room" }));

    const name = within(dialog).getByLabelText("Name");
    await waitFor(() => expect(name).toHaveAccessibleDescription("must not be blank"));
    expect(within(dialog).getByLabelText("Capacity")).toHaveAccessibleDescription("must be greater than or equal to 1");
    expect(within(dialog).queryByRole("alert")).not.toBeInTheDocument();
    expect(name).toHaveValue("Sala A");
    expect(within(dialog).getByLabelText("Capacity")).toHaveValue(4);
  });

  test("shows a taken name under the name field, keeping the input", async () => {
    serverReplies(
      json(200, []),
      json(
        409,
        {
          type: "urn:room-booking:problem:room-name-taken",
          title: "Room name taken",
          status: 409,
          detail: 'The name "Sala A" is already taken.',
        },
        "application/problem+json",
      ),
    );
    const dialog = await fillNewRoomDialog("Sala A", "4");

    await userEvent.click(within(dialog).getByRole("button", { name: "Create room" }));

    const name = within(dialog).getByLabelText("Name");
    await waitFor(() => expect(name).toHaveAccessibleDescription('The name "Sala A" is already taken.'));
    expect(name).toHaveFocus();
    expect(name).toHaveValue("Sala A");
    expect(within(dialog).getByLabelText("Capacity")).toHaveValue(4);
  });

  test("shows any other failure as a general message, keeping the input", async () => {
    serverReplies(
      json(200, []),
      json(
        500,
        { type: "about:blank", title: "Internal Server Error", status: 500, detail: "An unexpected error occurred." },
        "application/problem+json",
      ),
    );
    const dialog = await fillNewRoomDialog("Sala A", "4");

    await userEvent.click(within(dialog).getByRole("button", { name: "Create room" }));

    const alert = await within(dialog).findByRole("alert");
    expect(alert).toHaveTextContent("The room could not be saved");
    expect(alert).toHaveTextContent("An unexpected error occurred.");
    expect(within(dialog).getByLabelText("Name")).toHaveValue("Sala A");
    expect(within(dialog).getByLabelText("Capacity")).toHaveValue(4);
    expect(within(dialog).getByRole("button", { name: "Create room" })).toBeEnabled();
  });

  test("starts afresh when opened again", async () => {
    serverReplies(json(200, []));
    const dialog = await fillNewRoomDialog("Sala A", "4");

    await userEvent.click(within(dialog).getByRole("button", { name: "Close" }));
    await userEvent.click(screen.getAllByRole("button", { name: "New room" })[0] as HTMLElement);

    const reopened = screen.getByRole("dialog", { name: "New room" });
    expect(within(reopened).getByLabelText("Name")).toHaveValue("");
    expect(within(reopened).getByLabelText("Capacity")).toHaveValue(null);
  });
});

describe("editing a room", () => {
  const salaA = { id: "6f1c0a3e-1111-4c1a-9a40-000000000001", name: "Sala A", capacity: 4, status: "ACTIVE" };
  const salaB = { id: "6f1c0a3e-1111-4c1a-9a40-000000000002", name: "Sala B", capacity: 6, status: "DEACTIVATED" };

  test("opens the Room dialog with the room's current values", async () => {
    serverReplies(json(200, [salaA, salaB]));
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "Edit Sala B" }));

    const dialog = screen.getByRole("dialog", { name: "Edit room" });
    expect(within(dialog).getByLabelText("Name")).toHaveValue("Sala B");
    expect(within(dialog).getByLabelText("Capacity")).toHaveValue(6);
    expect(within(dialog).getByRole("button", { name: "Save changes" })).toBeEnabled();
  });

  test("saves the edit, closes the dialog and refetches the list", async () => {
    const renamed = { ...salaB, name: "Sala Grande", capacity: 10 };
    const fetch = serverReplies(json(200, [salaA, salaB]), json(200, renamed), json(200, [salaA, renamed]));
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: "Edit Sala B" }));
    const dialog = screen.getByRole("dialog", { name: "Edit room" });

    await userEvent.clear(within(dialog).getByLabelText("Name"));
    await userEvent.type(within(dialog).getByLabelText("Name"), "Sala Grande");
    await userEvent.clear(within(dialog).getByLabelText("Capacity"));
    await userEvent.type(within(dialog).getByLabelText("Capacity"), "10");
    await userEvent.click(within(dialog).getByRole("button", { name: "Save changes" }));

    expect(await screen.findByRole("cell", { name: "Sala Grande" })).toBeVisible();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(fetch).toHaveBeenCalledTimes(3);
    expect(request(fetch, 1)).toEqual({ method: "PUT", url: `/api/rooms/${salaB.id}` });
    expect(requestBody(fetch, 1)).toEqual({ name: "Sala Grande", capacity: 10 });
  });

  test("shows a room that no longer exists as a general message, keeping the input", async () => {
    serverReplies(
      json(200, [salaA, salaB]),
      json(
        404,
        { type: "about:blank", title: "Not Found", status: 404, detail: "The room does not exist." },
        "application/problem+json",
      ),
    );
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: "Edit Sala B" }));
    const dialog = screen.getByRole("dialog", { name: "Edit room" });

    await userEvent.type(within(dialog).getByLabelText("Name"), " 2");
    await userEvent.click(within(dialog).getByRole("button", { name: "Save changes" }));

    const alert = await within(dialog).findByRole("alert");
    expect(alert).toHaveTextContent("The room could not be saved");
    expect(alert).toHaveTextContent("The room does not exist.");
    expect(within(dialog).getByLabelText("Name")).toHaveValue("Sala B 2");
    expect(within(dialog).getByLabelText("Name")).toBeValid();
    expect(within(dialog).getByLabelText("Capacity")).toHaveValue(6);
  });

  test("starts from the values of the room being edited each time it opens", async () => {
    serverReplies(json(200, [salaA, salaB]));
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: "Edit Sala A" }));
    await userEvent.type(within(screen.getByRole("dialog")).getByLabelText("Name"), " changed");
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Cancel" }));

    await userEvent.click(screen.getByRole("button", { name: "Edit Sala B" }));

    const dialog = screen.getByRole("dialog", { name: "Edit room" });
    expect(within(dialog).getByLabelText("Name")).toHaveValue("Sala B");
    expect(within(dialog).getByLabelText("Capacity")).toHaveValue(6);
  });
});

describe("deactivating and reactivating a room", () => {
  const salaA = { id: "6f1c0a3e-1111-4c1a-9a40-000000000001", name: "Sala A", capacity: 4, status: "ACTIVE" };
  const salaB = { id: "6f1c0a3e-1111-4c1a-9a40-000000000002", name: "Sala B", capacity: 6, status: "DEACTIVATED" };

  test("asks to confirm naming the room, then deactivates it and refetches the list", async () => {
    const deactivated = { ...salaA, status: "DEACTIVATED" };
    const fetch = serverReplies(json(200, [salaA, salaB]), json(200, deactivated), json(200, [deactivated, salaB]));
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "Deactivate Sala A" }));

    const confirmation = screen.getByRole("alertdialog", { name: "Deactivate Sala A?" });
    expect(fetch).toHaveBeenCalledTimes(1);

    await userEvent.click(within(confirmation).getByRole("button", { name: "Deactivate" }));

    await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
    expect(screen.getByRole("cell", { name: "Sala A" }).closest("tr")).toHaveTextContent("Deactivated");
    expect(fetch).toHaveBeenCalledTimes(3);
    expect(request(fetch, 1)).toEqual({ method: "POST", url: `/api/rooms/${salaA.id}/deactivation` });
    expect(request(fetch, 2)).toEqual({ method: "GET", url: "/api/rooms" });
  });

  test("does nothing when the confirmation is cancelled", async () => {
    const fetch = serverReplies(json(200, [salaA, salaB]));
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "Deactivate Sala A" }));
    await userEvent.click(within(screen.getByRole("alertdialog")).getByRole("button", { name: "Cancel" }));

    await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
    expect(screen.getByRole("cell", { name: "Sala A" }).closest("tr")).toHaveTextContent("Active");
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  test("reactivates a deactivated room without asking, and refetches the list", async () => {
    const reactivated = { ...salaB, status: "ACTIVE" };
    const fetch = serverReplies(json(200, [salaA, salaB]), json(200, reactivated), json(200, [salaA, reactivated]));
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "Reactivate Sala B" }));

    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole("cell", { name: "Sala B" }).closest("tr")).toHaveTextContent("Active"));
    expect(fetch).toHaveBeenCalledTimes(3);
    expect(request(fetch, 1)).toEqual({ method: "POST", url: `/api/rooms/${salaB.id}/reactivation` });
    expect(request(fetch, 2)).toEqual({ method: "GET", url: "/api/rooms" });
  });

  const notFound = json(
    404,
    { type: "about:blank", title: "Not Found", status: 404, detail: "The room does not exist." },
    "application/problem+json",
  );

  test("keeps the confirmation open with the server's explanation when the deactivation fails", async () => {
    serverReplies(json(200, [salaA, salaB]), notFound);
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: "Deactivate Sala A" }));
    const confirmation = screen.getByRole("alertdialog", { name: "Deactivate Sala A?" });

    await userEvent.click(within(confirmation).getByRole("button", { name: "Deactivate" }));

    const alert = await within(confirmation).findByRole("alert");
    expect(alert).toHaveTextContent("The room could not be deactivated");
    expect(alert).toHaveTextContent("The room does not exist.");
    expect(within(confirmation).getByRole("button", { name: "Deactivate" })).toBeEnabled();
  });

  test("says which room could not be reactivated, and why", async () => {
    serverReplies(json(200, [salaA, salaB]), notFound);
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "Reactivate Sala B" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Sala B could not be reactivated");
    expect(alert).toHaveTextContent("The room does not exist.");
    expect(screen.getByRole("button", { name: "Reactivate Sala B" })).toBeEnabled();

    await userEvent.click(within(alert).getByRole("button", { name: "Dismiss" }));

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});

async function openNewRoomDialog() {
  renderPage();
  await screen.findByText("No rooms yet");
  await userEvent.click(screen.getAllByRole("button", { name: "New room" })[0] as HTMLElement);
  return screen.getByRole("dialog", { name: "New room" });
}

async function fillNewRoomDialog(name: string, capacity: string) {
  const dialog = await openNewRoomDialog();
  await userEvent.type(within(dialog).getByLabelText("Name"), name);
  await userEvent.type(within(dialog).getByLabelText("Capacity"), capacity);
  return dialog;
}

test("suggests checking the connection when the server cannot be reached", async () => {
  serverReplies(async () => {
    throw new TypeError("Failed to fetch");
  });
  renderPage();

  const alert = await screen.findByRole("alert");
  expect(alert).toHaveTextContent("The rooms could not be loaded");
  expect(alert).toHaveTextContent("Check your connection and try again.");
});
