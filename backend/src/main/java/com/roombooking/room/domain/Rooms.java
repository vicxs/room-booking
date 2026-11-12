package com.roombooking.room.domain;

import java.util.List;

/** Where Rooms are stored. Implemented outside the domain. */
public interface Rooms {

    /** Every Room, active and deactivated, sorted by name ignoring case. */
    List<Room> allSortedByName();

    /** Whether a Room other than this one, by id, has its name ignoring case, compared with {@code lower()}. */
    boolean nameTakenByAnotherRoom(Room room);

    /**
     * Stores a new Room.
     *
     * @throws RoomNameTakenException if another Room stored the same name, ignoring case, since it was checked.
     */
    void add(Room room);
}
