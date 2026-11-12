package com.roombooking.room.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The Room rules, which the domain owns whatever reaches it. Over HTTP, Bean Validation rejects most of these inputs
 * before the domain sees them, so they are checked here.
 */
class RoomCatalogueTest {

    private static final String HUNDRED_CHARACTERS = "x".repeat(100);

    private final InMemoryRooms rooms = new InMemoryRooms();
    private final RoomCatalogue catalogue = new RoomCatalogue(rooms);

    @Test
    void createsARoomWithANameOfAtMost100CharactersAndACapacityOfAtLeast1() {
        Room room = catalogue.create(HUNDRED_CHARACTERS, 1);

        assertThat(room.name()).isEqualTo(HUNDRED_CHARACTERS);
        assertThat(room.capacity()).isEqualTo(1);
        assertThat(catalogue.list()).containsExactly(room);
    }

    @Test
    void rejectsANameLongerThan100CharactersAsSentEvenIfItIsShorterOnceTrimmed() {
        assertInvalid(() -> catalogue.create(HUNDRED_CHARACTERS + " ", 4), "name", "size must be between 1 and 100");
        assertThat(catalogue.list()).isEmpty();
    }

    @Test
    void rejectsACapacityLessThan1() {
        assertInvalid(() -> catalogue.create("Sala A", 0), "capacity", "must be greater than or equal to 1");
        assertThat(catalogue.list()).isEmpty();
    }

    @Test
    void reportsEveryRuleTheRoomBreaksAtOnce() {
        assertThatThrownBy(() -> catalogue.create(" ", -1))
                .isInstanceOfSatisfying(
                        InvalidRoomException.class,
                        exception -> assertThat(exception.violations())
                                .containsExactly(
                                        new InvalidRoomException.Violation("name", "must not be blank"),
                                        new InvalidRoomException.Violation(
                                                "capacity", "must be greater than or equal to 1")));
    }

    @Test
    void appliesTheSameRulesWhenARoomIsEdited() {
        Room room = catalogue.create("Sala A", 4);

        assertInvalid(
                () -> catalogue.update(room.id(), HUNDRED_CHARACTERS + " ", 4),
                "name",
                "size must be between 1 and 100");
        assertInvalid(() -> catalogue.update(room.id(), "Sala A", 0), "capacity", "must be greater than or equal to 1");
        assertThat(catalogue.list()).containsExactly(room);
    }

    @Test
    void neverHoldsARoomThatBreaksTheRules() {
        UUID id = UUID.randomUUID();

        assertInvalid(
                () -> new Room(id, HUNDRED_CHARACTERS + "x", 4, RoomStatus.ACTIVE),
                "name",
                "size must be between 1 and 100");
        assertInvalid(
                () -> new Room(id, "Sala A", 0, RoomStatus.ACTIVE), "capacity", "must be greater than or equal to 1");
    }

    private static void assertInvalid(Runnable action, String property, String message) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        InvalidRoomException.class,
                        exception -> assertThat(exception.violations())
                                .containsExactly(new InvalidRoomException.Violation(property, message)));
    }

    /** Rooms kept in a list, for rules that do not depend on how Rooms are stored. */
    private static final class InMemoryRooms implements Rooms {

        private final List<Room> stored = new ArrayList<>();

        @Override
        public List<Room> allSortedByName() {
            return List.copyOf(stored);
        }

        @Override
        public Optional<Room> withId(UUID id) {
            return stored.stream().filter(room -> room.id().equals(id)).findFirst();
        }

        @Override
        public boolean nameTakenByAnotherRoom(Room room) {
            return stored.stream()
                    .anyMatch(other ->
                            !other.id().equals(room.id()) && other.name().equalsIgnoreCase(room.name()));
        }

        @Override
        public void add(Room room) {
            stored.add(room);
        }

        @Override
        public Room updateNameAndCapacity(Room room) {
            stored.replaceAll(other -> other.id().equals(room.id()) ? room : other);
            return room;
        }

        @Override
        public Optional<Room> updateStatus(UUID id, RoomStatus status) {
            return withId(id).map(room -> {
                Room changed = new Room(room.id(), room.name(), room.capacity(), status);
                stored.replaceAll(other -> other.id().equals(id) ? changed : other);
                return changed;
            });
        }
    }
}
