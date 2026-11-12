import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { roomsApi } from "@/api/client";
import type { Room, RoomInput } from "@/api/generated";
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

/** Replaces a Room's name and capacity. It settles once the list has been refetched, so the list shows the edit. */
export function useUpdateRoom() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: RoomInput }) =>
      callApi(() => roomsApi.updateRoom({ roomId: id, roomInput: input })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: roomsQueryKey }),
  });
}

/** Takes a Room out of service. It settles once the list has been refetched, so the list shows it deactivated. */
export function useDeactivateRoom() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (room: Room) => callApi(() => roomsApi.deactivateRoom({ roomId: room.id })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: roomsQueryKey }),
  });
}

/** Brings a Room back into service. It settles once the list has been refetched, so the list shows it active. */
export function useReactivateRoom() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (room: Room) => callApi(() => roomsApi.reactivateRoom({ roomId: room.id })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: roomsQueryKey }),
  });
}
