package com.roombooking.room.persistence;

import static com.roombooking.jooq.Tables.ROOM;
import static org.jooq.impl.DSL.lower;

import com.roombooking.jooq.tables.records.RoomRecord;
import com.roombooking.room.domain.Room;
import com.roombooking.room.domain.RoomStatus;
import com.roombooking.room.domain.Rooms;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

@Component
class JooqRooms implements Rooms {

    private final DSLContext db;

    JooqRooms(DSLContext db) {
        this.db = db;
    }

    @Override
    public List<Room> allSortedByName() {
        return db.selectFrom(ROOM).orderBy(lower(ROOM.NAME)).fetch(JooqRooms::toRoom);
    }

    private static Room toRoom(RoomRecord record) {
        return new Room(record.getId(), record.getName(), record.getCapacity(), RoomStatus.valueOf(record.getStatus()));
    }
}
