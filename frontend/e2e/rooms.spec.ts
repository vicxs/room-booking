import { expect, type Page, test } from "@playwright/test";

// The database is shared and the tests run in parallel: each test creates the
// Rooms it needs with unique names, and none asserts on the whole list or on
// the empty state.

function collectConsoleProblems(page: Page): string[] {
  const problems: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error" || message.type() === "warning") problems.push(message.text());
  });
  page.on("pageerror", (error) => problems.push(error.message));
  return problems;
}

test("opening the app lands on the Rooms page", async ({ page }) => {
  const consoleProblems = collectConsoleProblems(page);

  await page.goto("/");

  await expect(page).toHaveURL("/rooms");
  await expect(page.getByRole("heading", { name: "Rooms" })).toBeVisible();
  await expect(page.getByRole("status")).toBeHidden();
  await expect(page.getByRole("alert")).toBeHidden();
  expect(consoleProblems).toEqual([]);
});

function uniqueRoomName(): string {
  return `Room ${crypto.randomUUID().slice(0, 8)}`;
}

/** Creates a Room through the "New room" dialog and returns the dialog. */
async function createRoom(page: Page, name: string, capacity: string) {
  // The header's button; the empty state may offer a second one.
  await page.getByRole("button", { name: "New room" }).first().click();
  const dialog = page.getByRole("dialog", { name: "New room" });
  await dialog.getByLabel("Name").fill(name);
  await dialog.getByLabel("Capacity").fill(capacity);
  await dialog.getByRole("button", { name: "Create room" }).click();
  return dialog;
}

test("an Administrator creates a room and sees it in the table", async ({ page }) => {
  const consoleProblems = collectConsoleProblems(page);
  const name = uniqueRoomName();
  await page.goto("/rooms");

  const dialog = await createRoom(page, name, "6");

  await expect(dialog).toBeHidden();
  const row = page.getByRole("row").filter({ has: page.getByRole("cell", { name, exact: true }) });
  await expect(row.getByRole("cell")).toHaveText([name, "6", "Active"]);
  expect(consoleProblems).toEqual([]);
});

test("a taken name is shown under the name field", async ({ page }) => {
  const consoleProblems = collectConsoleProblems(page);
  const name = uniqueRoomName();
  const sameNameInCapitals = name.toUpperCase();
  await page.goto("/rooms");
  await expect(await createRoom(page, name, "4")).toBeHidden();

  const dialog = await createRoom(page, sameNameInCapitals, "8");

  await expect(dialog.getByLabel("Name")).toHaveAccessibleDescription(
    `The name "${sameNameInCapitals}" is already taken.`,
  );
  await expect(dialog.getByLabel("Name")).toHaveValue(sameNameInCapitals);
  await expect(dialog.getByLabel("Capacity")).toHaveValue("8");
  // Chromium itself reports every failed response; the app logs nothing.
  expect(consoleProblems).toEqual(["Failed to load resource: the server responded with a status of 409 (Conflict)"]);
});
