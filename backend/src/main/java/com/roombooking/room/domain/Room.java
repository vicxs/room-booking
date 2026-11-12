package com.roombooking.room.domain;

import static java.util.Objects.requireNonNull;

import java.util.UUID;

/** A bookable space, identified by a name unique among all Rooms ignoring case. A Room is never deleted. */
public record Room(UUID id, String name, int capacity, RoomStatus status) {

    public Room {
        requireNonNull(id);
        requireNonNull(name);
        requireNonNull(status);
        if (name.isBlank()) throw new InvalidRoomException("name", "must not be blank");
    }

    /** A new active Room with a random id, its name trimmed at both ends and its inner whitespace kept as typed. */
    static Room create(String name, int capacity) {
        return new Room(UUID.randomUUID(), name.strip(), capacity, RoomStatus.ACTIVE);
    }
}
