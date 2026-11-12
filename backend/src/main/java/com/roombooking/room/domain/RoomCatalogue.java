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
}
