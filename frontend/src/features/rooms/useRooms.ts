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

/** Creates an active Room. */
export function useCreateRoom() {
  return useRoomsMutation((roomInput: RoomInput) => roomsApi.createRoom({ roomInput }));
}

/** Replaces a Room's name and capacity. */
export function useUpdateRoom() {
  return useRoomsMutation(({ id, input }: { id: string; input: RoomInput }) =>
    roomsApi.updateRoom({ roomId: id, roomInput: input }),
  );
}

/** Takes a Room out of service. */
export function useDeactivateRoom() {
  return useRoomsMutation((room: Room) => roomsApi.deactivateRoom({ roomId: room.id }));
}

/** Brings a Room back into service. */
export function useReactivateRoom() {
  return useRoomsMutation((room: Room) => roomsApi.reactivateRoom({ roomId: room.id }));
}

/**
 * A change to the Rooms through the API. It settles once the list has been refetched, so whoever awaits it, such as a
 * dialog that closes on success, finds the change already in the list.
 */
function useRoomsMutation<TVariables, TData>(call: (variables: TVariables) => Promise<TData>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (variables: TVariables) => callApi(() => call(variables)),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: roomsQueryKey }),
  });
}
