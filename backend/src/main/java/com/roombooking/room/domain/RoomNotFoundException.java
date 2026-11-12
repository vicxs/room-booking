package com.roombooking.room.domain;

import java.util.UUID;

/** No Room has the id. */
public final class RoomNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    RoomNotFoundException(UUID id) {
        super("No Room has the id " + id + ".");
    }
}
