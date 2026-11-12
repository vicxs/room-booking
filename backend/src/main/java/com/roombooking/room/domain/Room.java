package com.roombooking.room.domain;

import static java.util.Objects.requireNonNull;

import com.roombooking.room.domain.InvalidRoomException.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A bookable space, identified by a name unique among all Rooms ignoring case. A Room is never deleted.
 *
 * <p>Its name is not blank and has at most {@value #NAME_MAX_LENGTH} characters, and its capacity is at least 1. A
 * name given for a new or edited Room must keep to that before it is trimmed, so the limit applies to the name as
 * sent.
 */
public record Room(UUID id, String name, int capacity, RoomStatus status) {

    private static final int NAME_MAX_LENGTH = 100;

    /** @throws InvalidRoomException if the name or the capacity breaks the Room's rules. */
    public Room {
        requireNonNull(id);
        requireNonNull(name);
        requireNonNull(status);
        requireValid(name, capacity);
    }

    /**
     * A new active Room with a random id, its name trimmed at both ends and its inner whitespace kept as typed.
     *
     * @throws InvalidRoomException if the name, as given, or the capacity breaks the Room's rules.
     */
    static Room create(String name, int capacity) {
        requireValid(name, capacity);
        return new Room(UUID.randomUUID(), name.strip(), capacity, RoomStatus.ACTIVE);
    }

    /**
     * This Room with another name, trimmed like a new Room's, and capacity; its id and status stay as they are.
     *
     * @throws InvalidRoomException if the name, as given, or the capacity breaks the Room's rules.
     */
    Room withNameAndCapacity(String name, int capacity) {
        requireValid(name, capacity);
        return new Room(id, name.strip(), capacity, status);
    }

    /** Reports every rule broken at once, worded as Bean Validation words the same rules at the HTTP edge. */
    private static void requireValid(String name, int capacity) {
        List<Violation> violations = new ArrayList<>();
        if (name.isBlank()) violations.add(new Violation("name", "must not be blank"));
        else if (name.length() > NAME_MAX_LENGTH)
            violations.add(new Violation("name", "size must be between 1 and " + NAME_MAX_LENGTH));
        if (capacity < 1) violations.add(new Violation("capacity", "must be greater than or equal to 1"));
        if (!violations.isEmpty()) throw new InvalidRoomException(violations);
    }
}
