import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { roomsApi } from "@/api/client";
import type { RoomInput } from "@/api/generated";
import { callApi } from "@/api/problem";

/** The key of the Rooms list; mutations invalidate it when they succeed. */
export const roomsQueryKey = ["rooms"] as const;

/** Every Room, in the order the server sends them (by name, ignoring case). */
export function useRooms() {
  return useQuery({ queryKey: roomsQueryKey, queryFn: () => callApi(() => roomsApi.listRooms()) });
}

/** Creates an active Room. It settles once the list has been refetched, so the new Room is already in it. */
export function useCreateRoom() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (roomInput: RoomInput) => callApi(() => roomsApi.createRoom({ roomInput })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: roomsQueryKey }),
  });
}
