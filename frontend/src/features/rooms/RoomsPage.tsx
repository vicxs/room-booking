import { CircleAlert, DoorOpen, LoaderCircle, Pencil, Plus, Power, PowerOff, RotateCw, Users } from "lucide-react";
import { type ReactNode, useState } from "react";
import { type Room, RoomStatus } from "@/api/generated";
import { explain } from "@/api/problem";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { cn } from "@/lib/utils";
import { DeactivateRoomDialog } from "./DeactivateRoomDialog";
import { FailureAlert } from "./FailureAlert";
import { RoomDialog } from "./RoomDialog";
import { useCreateRoom, useDeactivateRoom, useReactivateRoom, useRooms, useUpdateRoom } from "./useRooms";

export function RoomsPage() {
  const [creating, setCreating] = useState(false);
  // Each Room stays set while its dialog animates closed, so the content does not change as it fades out.
  const [editing, setEditing] = useState<{ room: Room; open: boolean }>();
  const [deactivating, setDeactivating] = useState<{ room: Room; open: boolean }>();
  const createRoom = useCreateRoom();
  const updateRoom = useUpdateRoom();
  const deactivateRoom = useDeactivateRoom();
  const reactivateRoom = useReactivateRoom();
  const startCreating = () => setCreating(true);
  const startEditing = (room: Room) => setEditing({ room, open: true });
  const startDeactivating = (room: Room) => setDeactivating({ room, open: true });

  return (
    <main className="mx-auto w-full max-w-4xl px-4 py-10 sm:px-6">
      <header className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">Rooms</h1>
          <p className="text-sm text-muted-foreground">Every room Employees can book, active and deactivated.</p>
        </div>
        <NewRoomButton onClick={startCreating} />
      </header>
      {reactivateRoom.isError && (
        <FailureAlert
          className="mb-4"
          title={`${reactivateRoom.variables.name} could not be reactivated`}
          reason={explain(reactivateRoom.error)}
          onDismiss={reactivateRoom.reset}
        />
      )}
      <section className="rounded-lg border bg-card text-card-foreground shadow-sm">
        <RoomsContent
          onCreate={startCreating}
          onEdit={startEditing}
          onDeactivate={startDeactivating}
          onReactivate={(room) => reactivateRoom.mutate(room)}
          reactivating={reactivateRoom.isPending ? reactivateRoom.variables : undefined}
        />
      </section>
      <RoomDialog
        open={creating}
        onOpenChange={setCreating}
        title="New room"
        description="Rooms are told apart by name, so it must be unique."
        submitLabel="Create room"
        onSave={createRoom.mutateAsync}
      />
      {editing && (
        <RoomDialog
          open={editing.open}
          onOpenChange={(open) => setEditing({ ...editing, open })}
          title="Edit room"
          description="Change the room's name or capacity."
          submitLabel="Save changes"
          initialValues={editing.room}
          onSave={(input) => updateRoom.mutateAsync({ id: editing.room.id, input })}
        />
      )}
      {deactivating && (
        <DeactivateRoomDialog
          room={deactivating.room}
          open={deactivating.open}
          onOpenChange={(open) => setDeactivating({ ...deactivating, open })}
          onDeactivate={() => deactivateRoom.mutateAsync(deactivating.room)}
        />
      )}
    </main>
  );
}

function NewRoomButton({ onClick }: { onClick: () => void }) {
  return (
    <Button onClick={onClick}>
      <Plus aria-hidden />
      New room
    </Button>
  );
}

type RoomActions = {
  onEdit: (room: Room) => void;
  onDeactivate: (room: Room) => void;
  onReactivate: (room: Room) => void;
  /** The Room whose reactivation is on its way to the server, if any. */
  reactivating: Room | undefined;
};

function RoomsContent({ onCreate, ...actions }: { onCreate: () => void } & RoomActions) {
  const rooms = useRooms();

  if (rooms.isPending) {
    return (
      <Message>
        <div role="status" className="flex items-center gap-2 text-sm text-muted-foreground">
          <LoaderCircle aria-hidden className="size-4 animate-spin" />
          Loading rooms…
        </div>
      </Message>
    );
  }

  if (rooms.isError) {
    return (
      <Message>
        <div role="alert" className="flex flex-col items-center gap-3 text-center">
          <CircleAlert aria-hidden className="size-6 text-destructive" />
          <div className="space-y-1">
            <p className="font-medium">The rooms could not be loaded</p>
            <p className="text-sm text-muted-foreground">{explain(rooms.error)}</p>
          </div>
          <Button variant="outline" size="sm" disabled={rooms.isFetching} onClick={() => rooms.refetch()}>
            <RotateCw aria-hidden className={cn(rooms.isFetching && "animate-spin")} />
            Try again
          </Button>
        </div>
      </Message>
    );
  }

  if (rooms.data.length === 0) {
    return (
      <Message>
        <div className="flex flex-col items-center gap-3 text-center">
          <DoorOpen aria-hidden className="size-6 text-muted-foreground" />
          <div className="space-y-1">
            <p className="font-medium">No rooms yet</p>
            <p className="text-sm text-muted-foreground">Create the first room Employees can book.</p>
          </div>
          <NewRoomButton onClick={onCreate} />
        </div>
      </Message>
    );
  }

  return <RoomsTable rooms={rooms.data} {...actions} />;
}

function Message({ children }: { children: ReactNode }) {
  return <div className="flex min-h-48 items-center justify-center px-6 py-12">{children}</div>;
}

function RoomsTable({ rooms, ...actions }: { rooms: Room[] } & RoomActions) {
  return (
    <Table>
      <TableHeader>
        <TableRow className="hover:bg-transparent">
          <TableHead className="pl-4">Name</TableHead>
          {/* On phones an icon stands for the label, leaving the room names space to wrap between words. */}
          <TableHead className="w-px whitespace-nowrap pl-3 text-right sm:pl-6">
            <Users aria-hidden className="ml-auto size-4 sm:hidden" />
            <span className="sr-only sm:not-sr-only">Capacity</span>
          </TableHead>
          <TableHead className="w-px whitespace-nowrap pl-3 sm:pl-6">Status</TableHead>
          <TableHead colSpan={2} className="w-px pl-1 pr-2 sm:pl-6">
            <span className="sr-only">Actions</span>
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rooms.map((room) => (
          <RoomRow key={room.id} room={room} {...actions} />
        ))}
      </TableBody>
    </Table>
  );
}

function RoomRow({ room, onEdit, onDeactivate, onReactivate, reactivating }: { room: Room } & RoomActions) {
  const deactivated = room.status === RoomStatus.Deactivated;

  return (
    <TableRow className={cn(deactivated && "text-muted-foreground")}>
      <TableCell className="py-3 pl-4 font-medium [overflow-wrap:anywhere]">{room.name}</TableCell>
      <TableCell className="py-3 pl-3 text-right tabular-nums sm:pl-6">{room.capacity}</TableCell>
      <TableCell className="py-3 pl-3 sm:pl-6">
        {deactivated ? <Badge variant="secondary">Deactivated</Badge> : <Badge variant="outline">Active</Badge>}
      </TableCell>
      {/*
       * The actions keep full contrast on a dimmed row: a deactivated Room can still be acted on. Each has its own
       * cell, so the table lines them up across rows although "Deactivate" and "Reactivate" differ in width.
       */}
      <TableCell className="w-px py-2 pl-1 pr-0 text-foreground sm:pl-6">
        <RowAction label="Edit" room={room} icon={<Pencil aria-hidden />} onClick={() => onEdit(room)} />
      </TableCell>
      <TableCell className="w-px py-2 pl-1 pr-2 text-foreground">
        {deactivated ? (
          <RowAction
            label="Reactivate"
            room={room}
            icon={<Power aria-hidden />}
            pending={reactivating?.id === room.id}
            onClick={() => onReactivate(room)}
          />
        ) : (
          <RowAction
            label="Deactivate"
            room={room}
            icon={<PowerOff aria-hidden />}
            onClick={() => onDeactivate(room)}
          />
        )}
      </TableCell>
    </TableRow>
  );
}

/**
 * A row's action, named after its Room for assistive technology; on phones it shows only its icon. While pending, it
 * is disabled and its icon spins.
 */
function RowAction({
  label,
  room,
  icon,
  pending = false,
  onClick,
}: {
  label: string;
  room: Room;
  icon: ReactNode;
  pending?: boolean;
  onClick: () => void;
}) {
  return (
    <Button
      variant="ghost"
      size="sm"
      className="px-2 sm:px-3"
      aria-label={`${label} ${room.name}`}
      disabled={pending}
      onClick={onClick}
    >
      {pending ? <LoaderCircle aria-hidden className="animate-spin" /> : icon}
      <span className="sr-only sm:not-sr-only">{label}</span>
    </Button>
  );
}
