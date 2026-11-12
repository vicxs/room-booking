package com.roombooking.room.domain;

import static java.util.Objects.requireNonNull;

/** A Room property breaks a rule, such as a name that is blank once trimmed. The message says which rule. */
public final class InvalidRoomException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String property;

    InvalidRoomException(String property, String message) {
        super(message);
        this.property = requireNonNull(property);
    }

    /** The Room property that breaks the rule, e.g. {@code name}. */
    public String property() {
        return property;
    }
}
