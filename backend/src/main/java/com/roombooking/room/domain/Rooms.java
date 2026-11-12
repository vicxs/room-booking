package com.roombooking.room.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Where Rooms are stored. Implemented outside the domain. */
public interface Rooms {

    /** Every Room, active and deactivated, sorted by name ignoring case. */
    List<Room> allSortedByName();

    /** The Room with this id, if there is one. */
    Optional<Room> withId(UUID id);

    /** Whether a Room other than this one, by id, has its name ignoring case, compared with {@code lower()}. */
    boolean nameTakenByAnotherRoom(Room room);

    /**
     * Stores a new Room.
     *
     * @throws RoomNameTakenException if another Room stored the same name, ignoring case, since it was checked.
     */
    void add(Room room);

    /**
     * Stores the name and capacity of an existing Room, by id, leaving its stored status as it is.
     *
     * @return the Room as stored now.
     * @throws RoomNameTakenException if another Room stored the same name, ignoring case, since it was checked.
     */
    Room updateNameAndCapacity(Room room);

    /**
     * Stores the status of an existing Room, by id, leaving its stored name and capacity as they are.
     *
     * @return the Room as stored now, or nothing if no Room has the id.
     */
    Optional<Room> updateStatus(UUID id, RoomStatus status);
}
