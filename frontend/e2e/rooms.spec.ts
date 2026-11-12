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
