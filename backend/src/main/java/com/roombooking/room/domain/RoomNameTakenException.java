package com.roombooking.room.domain;

/** Another Room already has the name, ignoring case. */
public final class RoomNameTakenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** @param name the name as the Room would have stored it, trimmed. */
    public RoomNameTakenException(String name) {
        super("The name \"" + name + "\" is already taken.");
    }
}
