package com.roombooking.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.roombooking.TestcontainersConfiguration;
import com.roombooking.contract.model.Room;
import com.roombooking.contract.model.RoomStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RoomsIntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void startWithNoRooms() {
        jdbc.sql("delete from room").update();
    }

    @Test
    void listsEveryRoomActiveAndDeactivatedSortedByNameIgnoringCase() {
        UUID delta = givenRoom("Delta", 12, "ACTIVE");
        UUID alpha = givenRoom("alpha", 4, "ACTIVE");
        UUID charlie = givenRoom("Charlie", 8, "DEACTIVATED");
        UUID beta = givenRoom("beta room", 6, "ACTIVE");

        ResponseEntity<Room[]> response = http.getForEntity("/api/rooms", Room[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsExactly(
                        new Room(alpha, "alpha", 4, RoomStatus.ACTIVE),
                        new Room(beta, "beta room", 6, RoomStatus.ACTIVE),
                        new Room(charlie, "Charlie", 8, RoomStatus.DEACTIVATED),
                        new Room(delta, "Delta", 12, RoomStatus.ACTIVE));
    }

    @Test
    void listsNoRoomsAsAnEmptyArray() {
        ResponseEntity<String> response = http.getForEntity("/api/rooms", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("[]");
    }

    /**
     * Rooms cannot be created over HTTP until {@code POST /rooms} is implemented, so they are inserted directly.
     */
    private UUID givenRoom(String name, int capacity, String status) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into room (id, name, capacity, status) values (?, ?, ?, ?)")
                .params(id, name, capacity, status)
                .update();
        return id;
    }
}
