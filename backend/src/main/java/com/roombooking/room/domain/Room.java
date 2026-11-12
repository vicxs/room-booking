package com.roombooking.room.domain;

import static java.util.Objects.requireNonNull;

import java.util.UUID;

/** A bookable space, identified by a name unique among all Rooms ignoring case. A Room is never deleted. */
public record Room(UUID id, String name, int capacity, RoomStatus status) {

    public Room {
        requireNonNull(id);
        requireNonNull(name);
        requireNonNull(status);
    }
}
