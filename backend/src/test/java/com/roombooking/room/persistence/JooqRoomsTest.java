package com.roombooking.room.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roombooking.TestcontainersConfiguration;
import com.roombooking.room.domain.Room;
import com.roombooking.room.domain.RoomNameTakenException;
import com.roombooking.room.domain.RoomStatus;
import com.roombooking.room.domain.Rooms;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;

/**
 * What the HTTP tests cannot reach: two requests that both pass the domain's name check before either stores its
 * Room. The second {@code add} then hits the unique index on {@code lower(name)}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class JooqRoomsTest {

    @Autowired
    Rooms rooms;

    @Test
    void reportsANameStoredConcurrentlyIgnoringCaseAsTaken() {
        String unique = UUID.randomUUID().toString();
        rooms.add(activeRoom(UUID.randomUUID(), "Sala " + unique));

        assertThatThrownBy(() -> rooms.add(activeRoom(UUID.randomUUID(), "SALA " + unique)))
                .isInstanceOf(RoomNameTakenException.class)
                .hasMessage("The name \"SALA " + unique + "\" is already taken.");
    }

    @Test
    void leavesOtherConstraintViolationsAsTheyAre() {
        UUID id = UUID.randomUUID();
        rooms.add(activeRoom(id, "Sala " + UUID.randomUUID()));

        assertThatThrownBy(() -> rooms.add(activeRoom(id, "Sala " + UUID.randomUUID())))
                .isInstanceOf(DataAccessException.class)
                .isNotInstanceOf(RoomNameTakenException.class);
    }

    private static Room activeRoom(UUID id, String name) {
        return new Room(id, name, 4, RoomStatus.ACTIVE);
    }
}
