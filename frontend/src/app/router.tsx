import { createBrowserRouter, Navigate, type RouteObject } from "react-router-dom";
import { RoomsPage } from "@/features/rooms/RoomsPage";

export const routes: RouteObject[] = [
  { path: "/", element: <Navigate to="/rooms" replace /> },
  { path: "/rooms", element: <RoomsPage /> },
];

export const router = createBrowserRouter(routes, {
  // Opt into React Router 7 behaviour now, so the planned migration stays small.
  future: {
    v7_fetcherPersist: true,
    v7_normalizeFormMethod: true,
    v7_partialHydration: true,
    v7_relativeSplatPath: true,
    v7_skipActionErrorRevalidation: true,
  },
});
