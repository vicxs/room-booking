import { ApiError, explain } from "./problem";

describe("explain", () => {
  test("gives the server's own words when it answered a Problem", () => {
    const error = new ApiError({
      type: "about:blank",
      title: "Not Found",
      status: 404,
      detail: "The room does not exist.",
    });

    expect(explain(error)).toBe("The room does not exist.");
  });

  test("suggests checking the connection when the server answered no Problem", () => {
    expect(explain(new ApiError(undefined))).toBe("Check your connection and try again.");
    expect(explain(new TypeError("Failed to fetch"))).toBe("Check your connection and try again.");
  });

  test("admits that something went wrong when what failed is not an error", () => {
    expect(explain("boom")).toBe("Something went wrong.");
    expect(explain(undefined)).toBe("Something went wrong.");
  });
});
