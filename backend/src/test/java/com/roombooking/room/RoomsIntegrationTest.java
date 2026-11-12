package com.roombooking.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.roombooking.TestcontainersConfiguration;
import com.roombooking.contract.model.Room;
import com.roombooking.contract.model.RoomStatus;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.Nullable;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RoomsIntegrationTest {

    private static final String VALID_ROOM = "{\"name\": \"Sala A\", \"capacity\": 4}";

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

    @Test
    void answersTheOperationsNotImplementedYetWithA501Problem() {
        String room = "/api/rooms/" + UUID.randomUUID();

        assertProblem(send(HttpMethod.POST, "/api/rooms", VALID_ROOM), HttpStatus.NOT_IMPLEMENTED);
        assertProblem(send(HttpMethod.PUT, room, VALID_ROOM), HttpStatus.NOT_IMPLEMENTED);
        assertProblem(send(HttpMethod.POST, room + "/deactivation", null), HttpStatus.NOT_IMPLEMENTED);
        assertProblem(send(HttpMethod.POST, room + "/reactivation", null), HttpStatus.NOT_IMPLEMENTED);
    }

    @Test
    void rejectsARoomIdThatIsNotAUuid() {
        for (ResponseEntity<JsonNode> response : List.of(
                send(HttpMethod.PUT, "/api/rooms/not-a-uuid", VALID_ROOM),
                send(HttpMethod.POST, "/api/rooms/not-a-uuid/deactivation", null),
                send(HttpMethod.POST, "/api/rooms/not-a-uuid/reactivation", null))) {
            JsonNode problem = assertProblem(response, HttpStatus.BAD_REQUEST);
            assertThat(problem.has("errors")).isFalse();
        }
    }

    @Test
    void rejectsAMalformedOrMistypedBody() {
        for (ResponseEntity<JsonNode> response : List.of(
                send(HttpMethod.POST, "/api/rooms", "{\"name\": \"Sala A\","),
                send(HttpMethod.POST, "/api/rooms", "{\"name\": \"Sala A\", \"capacity\": \"four\"}"),
                send(HttpMethod.PUT, "/api/rooms/" + UUID.randomUUID(), "[]"))) {
            JsonNode problem = assertProblem(response, HttpStatus.BAD_REQUEST);
            assertThat(problem.has("errors")).isFalse();
        }
    }

    @Test
    void listsEachFieldThatFailsValidationInEnglish() {
        HttpHeaders spanish = new HttpHeaders();
        spanish.setAcceptLanguageAsLocales(List.of(Locale.forLanguageTag("es")));
        String tooLongName = "x".repeat(101);

        ResponseEntity<JsonNode> response =
                send(HttpMethod.POST, "/api/rooms", "{\"name\": \"" + tooLongName + "\", \"capacity\": 0}", spanish);

        JsonNode problem = assertProblem(response, HttpStatus.BAD_REQUEST);
        assertThat(problem.path("errors"))
                .containsExactly(
                        fieldError("capacity", "must be greater than or equal to 1"),
                        fieldError("name", "size must be between 1 and 100"));
    }

    @Test
    void reportsAMissingFieldAsAValidationError() {
        JsonNode problem =
                assertProblem(send(HttpMethod.POST, "/api/rooms", "{\"name\": \"Sala A\"}"), HttpStatus.BAD_REQUEST);

        assertThat(problem.path("errors")).containsExactly(fieldError("capacity", "must not be null"));
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

    /** Sends a JSON request accepting only JSON, as the generated TypeScript client does. */
    private ResponseEntity<JsonNode> send(HttpMethod method, String path, @Nullable String json) {
        return send(method, path, json, new HttpHeaders());
    }

    private ResponseEntity<JsonNode> send(
            HttpMethod method, String path, @Nullable String json, HttpHeaders extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(extraHeaders);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, method, new HttpEntity<>(json, headers), JsonNode.class);
    }

    private static JsonNode fieldError(String field, String message) {
        return JsonNodeFactory.instance.objectNode().put("field", field).put("message", message);
    }

    private static JsonNode assertProblem(ResponseEntity<JsonNode> response, HttpStatus status) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        JsonNode problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.path("type").asText()).isEqualTo("about:blank");
        assertThat(problem.path("title").asText()).isEqualTo(status.getReasonPhrase());
        assertThat(problem.path("status").asInt()).isEqualTo(status.value());
        return problem;
    }
}
