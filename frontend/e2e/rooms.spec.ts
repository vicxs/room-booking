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

/** The table row of the Room with exactly this name. */
function roomRow(page: Page, name: string) {
  return page.getByRole("row").filter({ has: page.getByRole("cell", { name, exact: true }) });
}

test("an Administrator creates a room and sees it in the table", async ({ page }) => {
  const consoleProblems = collectConsoleProblems(page);
  const name = uniqueRoomName();
  await page.goto("/rooms");

  const dialog = await createRoom(page, name, "6");

  await expect(dialog).toBeHidden();
  await expect(roomRow(page, name).getByRole("cell")).toHaveText([name, "6", "Active", "Edit", "Deactivate"]);
  expect(consoleProblems).toEqual([]);
});

test("an Administrator edits a room and sees the new values in the table", async ({ page }) => {
  const consoleProblems = collectConsoleProblems(page);
  const name = uniqueRoomName();
  const newName = uniqueRoomName();
  await page.goto("/rooms");
  await expect(await createRoom(page, name, "4")).toBeHidden();

  await page.getByRole("button", { name: `Edit ${name}` }).click();
  const dialog = page.getByRole("dialog", { name: "Edit room" });
  await expect(dialog.getByLabel("Name")).toHaveValue(name);
  await expect(dialog.getByLabel("Capacity")).toHaveValue("4");
  await dialog.getByLabel("Name").fill(newName);
  await dialog.getByLabel("Capacity").fill("12");
  await dialog.getByRole("button", { name: "Save changes" }).click();

  await expect(dialog).toBeHidden();
  await expect(roomRow(page, newName).getByRole("cell")).toHaveText([newName, "12", "Active", "Edit", "Deactivate"]);
  await expect(roomRow(page, name)).toHaveCount(0);
  expect(consoleProblems).toEqual([]);
});

test("an Administrator deactivates a room after confirming, then reactivates it", async ({ page }) => {
  const consoleProblems = collectConsoleProblems(page);
  const name = uniqueRoomName();
  await page.goto("/rooms");
  await expect(await createRoom(page, name, "8")).toBeHidden();
  const row = roomRow(page, name);
  const nameCell = row.getByRole("cell", { name, exact: true });
  const activeColor = await nameCell.evaluate((cell) => getComputedStyle(cell).color);

  await page.getByRole("button", { name: `Deactivate ${name}` }).click();
  const confirmation = page.getByRole("alertdialog", { name: `Deactivate ${name}?` });
  await confirmation.getByRole("button", { name: "Deactivate" }).click();

  await expect(confirmation).toBeHidden();
  await expect(row.getByRole("cell")).toHaveText([name, "8", "Deactivated", "Edit", "Reactivate"]);
  await expect(nameCell).not.toHaveCSS("color", activeColor);

  await page.getByRole("button", { name: `Reactivate ${name}` }).click();

  await expect(row.getByRole("cell")).toHaveText([name, "8", "Active", "Edit", "Deactivate"]);
  await expect(nameCell).toHaveCSS("color", activeColor);
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
