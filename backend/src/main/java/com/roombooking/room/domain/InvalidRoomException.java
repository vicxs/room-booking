package com.roombooking.room.domain;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.stream.Collectors;

/** A Room would break one or more of its rules, such as a name that is blank once trimmed. */
public final class InvalidRoomException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial") // An immutable List, serializable in practice.
    private final List<Violation> violations;

    InvalidRoomException(List<Violation> violations) {
        super(violations.stream().map(Violation::toString).collect(Collectors.joining("; ")));
        if (violations.isEmpty()) throw new IllegalArgumentException("A Room is invalid only if it breaks a rule");
        this.violations = List.copyOf(violations);
    }

    /** Each rule the Room breaks, in the order the Room checks them. */
    public List<Violation> violations() {
        return violations;
    }

    /**
     * A rule a Room property breaks.
     *
     * @param property the Room property, e.g. {@code name}.
     * @param message which rule it breaks, worded as Bean Validation words the same rule at the HTTP edge.
     */
    public record Violation(String property, String message) {

        public Violation {
            requireNonNull(property);
            requireNonNull(message);
        }

        @Override
        public String toString() {
            return property + " " + message;
        }
    }
}
