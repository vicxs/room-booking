package com.roombooking.room.persistence;

import static com.roombooking.jooq.Tables.ROOM;
import static org.jooq.impl.DSL.lower;

import com.roombooking.jooq.tables.records.RoomRecord;
import com.roombooking.room.domain.Room;
import com.roombooking.room.domain.RoomNameTakenException;
import com.roombooking.room.domain.RoomStatus;
import com.roombooking.room.domain.Rooms;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.jooq.DSLContext;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
class JooqRooms implements Rooms {

    /** Written by hand: jOOQ generates no constant for a functional index (ADR-0004). See V1__create_room.sql. */
    private static final String NAME_UNIQUE_IGNORING_CASE = "room_name_unique_ignoring_case";

    private final DSLContext db;

    JooqRooms(DSLContext db) {
        this.db = db;
    }

    @Override
    public List<Room> allSortedByName() {
        return db.selectFrom(ROOM).orderBy(lower(ROOM.NAME)).fetch(JooqRooms::toRoom);
    }

    @Override
    public Optional<Room> withId(UUID id) {
        return db.selectFrom(ROOM).where(ROOM.ID.eq(id)).fetchOptional(JooqRooms::toRoom);
    }

    @Override
    public boolean nameTakenByAnotherRoom(Room room) {
        return db.fetchExists(ROOM, lower(ROOM.NAME).eq(lower(room.name())).and(ROOM.ID.ne(room.id())));
    }

    @Override
    public void add(Room room) {
        rejectingATakenName(
                room,
                () -> db.insertInto(ROOM)
                        .set(ROOM.ID, room.id())
                        .set(ROOM.NAME, room.name())
                        .set(ROOM.CAPACITY, room.capacity())
                        .set(ROOM.STATUS, room.status().name())
                        .execute());
    }

    @Override
    public Room updateNameAndCapacity(Room room) {
        return rejectingATakenName(
                room,
                () -> db.update(ROOM)
                        .set(ROOM.NAME, room.name())
                        .set(ROOM.CAPACITY, room.capacity())
                        .where(ROOM.ID.eq(room.id()))
                        .returning()
                        .fetchSingle(JooqRooms::toRoom));
    }

    @Override
    public Optional<Room> updateStatus(UUID id, RoomStatus status) {
        return db.update(ROOM)
                .set(ROOM.STATUS, status.name())
                .where(ROOM.ID.eq(id))
                .returning()
                .fetchOptional(JooqRooms::toRoom);
    }

    /**
     * Runs a write of the Room's name, turning a violation of the unique index on the name, from a Room stored since
     * the domain checked it, into the domain's taken-name error.
     */
    private static <T> T rejectingATakenName(Room room, Supplier<T> write) {
        try {
            return write.get();
        } catch (DuplicateKeyException exception) {
            if (violates(exception, NAME_UNIQUE_IGNORING_CASE)) throw new RoomNameTakenException(room.name());
            throw exception;
        }
    }

    private static boolean violates(DuplicateKeyException exception, String constraint) {
        return exception.getMostSpecificCause() instanceof PSQLException cause
                && cause.getServerErrorMessage() != null
                && constraint.equals(cause.getServerErrorMessage().getConstraint());
    }

    private static Room toRoom(RoomRecord record) {
        return new Room(record.getId(), record.getName(), record.getCapacity(), RoomStatus.valueOf(record.getStatus()));
    }
}
