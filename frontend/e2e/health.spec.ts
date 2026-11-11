import { expect, test } from "@playwright/test";

test("the home page shows the service as up", async ({ page }) => {
  const consoleErrors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error" || message.type() === "warning") consoleErrors.push(message.text());
  });

  await page.goto("/");

  await expect(page.getByRole("heading", { name: "Room Booking" })).toBeVisible();
  await expect(page.getByRole("status", { name: "Service status" })).toHaveText("Up");
  expect(consoleErrors).toEqual([]);
});
