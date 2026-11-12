package com.roombooking.room.domain;

import java.util.List;

/** Where Rooms are stored. Implemented outside the domain. */
public interface Rooms {

    /** Every Room, active and deactivated, sorted by name ignoring case. */
    List<Room> allSortedByName();
}
