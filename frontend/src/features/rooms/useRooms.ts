import { useQuery } from "@tanstack/react-query";
import { roomsApi } from "@/api/client";
import { callApi } from "@/api/problem";

/** The key of the Rooms list; mutations invalidate it when they succeed. */
export const roomsQueryKey = ["rooms"] as const;

/** Every Room, in the order the server sends them (by name, ignoring case). */
export function useRooms() {
  return useQuery({ queryKey: roomsQueryKey, queryFn: () => callApi(() => roomsApi.listRooms()) });
}
