package com.roombooking.room.persistence;

import static com.roombooking.jooq.Tables.ROOM;
import static org.jooq.impl.DSL.lower;

import com.roombooking.jooq.tables.records.RoomRecord;
import com.roombooking.room.domain.Room;
import com.roombooking.room.domain.RoomNameTakenException;
import com.roombooking.room.domain.RoomStatus;
import com.roombooking.room.domain.Rooms;
import java.util.List;
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
    public boolean nameTakenByAnotherRoom(Room room) {
        return db.fetchExists(ROOM, lower(ROOM.NAME).eq(lower(room.name())).and(ROOM.ID.ne(room.id())));
    }

    @Override
    public void add(Room room) {
        try {
            db.insertInto(ROOM)
                    .set(ROOM.ID, room.id())
                    .set(ROOM.NAME, room.name())
                    .set(ROOM.CAPACITY, room.capacity())
                    .set(ROOM.STATUS, room.status().name())
                    .execute();
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
