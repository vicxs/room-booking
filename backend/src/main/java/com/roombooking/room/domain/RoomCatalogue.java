package com.roombooking.room.domain;

import static java.util.Objects.requireNonNull;

import java.util.List;

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
     * @throws InvalidRoomException if the name is blank once trimmed.
     * @throws RoomNameTakenException if another Room has the name, ignoring case.
     */
    public Room create(String name, int capacity) {
        Room room = Room.create(name, capacity);
        if (rooms.nameTakenByAnotherRoom(room)) throw new RoomNameTakenException(room.name());
        rooms.add(room);
        return room;
    }
}
