import { CircleAlert, DoorOpen, LoaderCircle, RotateCw } from "lucide-react";
import type { ReactNode } from "react";
import { type Room, RoomStatus } from "@/api/generated";
import { ApiError } from "@/api/problem";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { cn } from "@/lib/utils";
import { useRooms } from "./useRooms";

export function RoomsPage() {
  return (
    <main className="mx-auto w-full max-w-4xl px-4 py-10 sm:px-6">
      <header className="mb-6 space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">Rooms</h1>
        <p className="text-sm text-muted-foreground">Every room Employees can book, active and deactivated.</p>
      </header>
      <section className="rounded-lg border bg-card text-card-foreground shadow-sm">
        <RoomsContent />
      </section>
    </main>
  );
}

function RoomsContent() {
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
            <p className="text-sm text-muted-foreground">Rooms appear here once they are created.</p>
          </div>
        </div>
      </Message>
    );
  }

  return <RoomsTable rooms={rooms.data} />;
}

function explain(error: Error): string {
  if (error instanceof ApiError && error.problem) return error.problem.detail ?? error.problem.title;
  return "Check your connection and try again.";
}

function Message({ children }: { children: ReactNode }) {
  return <div className="flex min-h-48 items-center justify-center px-6 py-12">{children}</div>;
}

function RoomsTable({ rooms }: { rooms: Room[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow className="hover:bg-transparent">
          <TableHead className="pl-4">Name</TableHead>
          <TableHead className="w-px whitespace-nowrap pl-6 text-right">Capacity</TableHead>
          <TableHead className="w-px whitespace-nowrap pl-6 pr-4">Status</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rooms.map((room) => (
          <RoomRow key={room.id} room={room} />
        ))}
      </TableBody>
    </Table>
  );
}

function RoomRow({ room }: { room: Room }) {
  const deactivated = room.status === RoomStatus.Deactivated;

  return (
    <TableRow className={cn(deactivated && "text-muted-foreground")}>
      <TableCell className="py-3 pl-4 font-medium">{room.name}</TableCell>
      <TableCell className="py-3 pl-6 text-right tabular-nums">{room.capacity}</TableCell>
      <TableCell className="py-3 pl-6 pr-4">
        {deactivated ? <Badge variant="secondary">Deactivated</Badge> : <Badge variant="outline">Active</Badge>}
      </TableCell>
    </TableRow>
  );
}
