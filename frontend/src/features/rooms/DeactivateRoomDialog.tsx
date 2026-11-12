import { LoaderCircle } from "lucide-react";
import { type MouseEvent, useState } from "react";
import type { Room } from "@/api/generated";
import { explain } from "@/api/problem";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { FailureAlert } from "./FailureAlert";

export type DeactivateRoomDialogProps = {
  room: Room;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Deactivates the Room on the server. The dialog closes when it resolves and shows why when it rejects. */
  onDeactivate: () => Promise<unknown>;
};

/** Asks the Administrator to confirm, naming the Room, before taking it out of service. Cancelling does nothing. */
export function DeactivateRoomDialog({ room, open, onOpenChange, onDeactivate }: DeactivateRoomDialogProps) {
  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent className="max-w-md">
        {/* Mounted only while open, so it starts afresh every time. */}
        <Confirmation room={room} onDeactivate={onDeactivate} onDeactivated={() => onOpenChange(false)} />
      </AlertDialogContent>
    </AlertDialog>
  );
}

function Confirmation({
  room,
  onDeactivate,
  onDeactivated,
}: Pick<DeactivateRoomDialogProps, "room" | "onDeactivate"> & { onDeactivated: () => void }) {
  const [deactivating, setDeactivating] = useState(false);
  const [error, setError] = useState<string>();

  async function confirm(event: MouseEvent) {
    // The dialog stays open until the server answers, so a failure can be shown in it.
    event.preventDefault();
    setDeactivating(true);
    setError(undefined);
    try {
      await onDeactivate();
      onDeactivated();
    } catch (failure) {
      setDeactivating(false);
      setError(failure instanceof Error ? explain(failure) : "Something went wrong.");
    }
  }

  return (
    <>
      <AlertDialogHeader>
        <AlertDialogTitle className="[overflow-wrap:anywhere]">Deactivate {room.name}?</AlertDialogTitle>
        <AlertDialogDescription>
          It will accept no new bookings until it is reactivated. It stays in the list and keeps its name.
        </AlertDialogDescription>
      </AlertDialogHeader>
      {error && <FailureAlert title="The room could not be deactivated" reason={error} />}
      <AlertDialogFooter className="gap-2 sm:gap-0">
        <AlertDialogCancel>Cancel</AlertDialogCancel>
        <AlertDialogAction disabled={deactivating} onClick={confirm}>
          {deactivating && <LoaderCircle aria-hidden className="animate-spin" />}
          Deactivate
        </AlertDialogAction>
      </AlertDialogFooter>
    </>
  );
}
