package com.roombooking.room.domain;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.UUID;

/** The catalogue of Rooms that Administrators manage. */
public final class RoomCatalogue {

    private final Rooms rooms;

    public RoomCatalogue(Rooms rooms) {
        this.rooms = requireNonNull(rooms);
    }

    /** Every Room, active and deactivated, sorted by name ignoring case. */
    public List<Room> list() {
        return rooms.allSortedByName();
    }

    /**
     * Creates an active Room, with its name trimmed at both ends.
     *
     * @throws InvalidRoomException if the name or the capacity breaks the Room's rules (see {@link Room}).
     * @throws RoomNameTakenException if another Room has the name, ignoring case.
     */
    public Room create(String name, int capacity) {
        Room room = Room.create(name, capacity);
        if (rooms.nameTakenByAnotherRoom(room)) throw new RoomNameTakenException(room.name());
        rooms.add(room);
        return room;
    }

    /**
     * Replaces the name, trimmed at both ends, and the capacity of a Room, active or deactivated. Its status stays as
     * it is. The Room's own name does not count as taken, so a case-only rename or an unchanged Room is accepted.
     *
     * @return the Room as it now is.
     * @throws RoomNotFoundException if no Room has the id.
     * @throws InvalidRoomException if the name or the capacity breaks the Room's rules (see {@link Room}).
     * @throws RoomNameTakenException if another Room has the name, ignoring case.
     */
    public Room update(UUID id, String name, int capacity) {
        Room room = rooms.withId(id)
                .orElseThrow(() -> new RoomNotFoundException(id))
                .withNameAndCapacity(name, capacity);
        if (rooms.nameTakenByAnotherRoom(room)) throw new RoomNameTakenException(room.name());
        return rooms.updateNameAndCapacity(room);
    }

    /**
     * Takes a Room out of service. Deactivating a deactivated Room changes nothing.
     *
     * @return the Room as it now is.
     * @throws RoomNotFoundException if no Room has the id.
     */
    public Room deactivate(UUID id) {
        return changeStatus(id, RoomStatus.DEACTIVATED);
    }

    /**
     * Brings a deactivated Room back into service. Reactivating an active Room changes nothing.
     *
     * @return the Room as it now is.
     * @throws RoomNotFoundException if no Room has the id.
     */
    public Room reactivate(UUID id) {
        return changeStatus(id, RoomStatus.ACTIVE);
    }

    private Room changeStatus(UUID id, RoomStatus status) {
        return rooms.updateStatus(id, status).orElseThrow(() -> new RoomNotFoundException(id));
    }
}
