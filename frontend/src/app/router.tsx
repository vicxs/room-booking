import { createBrowserRouter } from "react-router-dom";
import { HealthPage } from "@/features/health/HealthPage";

export const router = createBrowserRouter([{ path: "/", element: <HealthPage /> }], {
  // Opt into React Router 7 behaviour now, so the planned migration stays small.
  future: {
    v7_fetcherPersist: true,
    v7_normalizeFormMethod: true,
    v7_partialHydration: true,
    v7_relativeSplatPath: true,
    v7_skipActionErrorRevalidation: true,
  },
});
