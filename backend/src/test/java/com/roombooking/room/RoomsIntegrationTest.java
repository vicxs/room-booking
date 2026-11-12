package com.roombooking.room;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.roombooking.TestcontainersConfiguration;
import com.roombooking.contract.model.Room;
import com.roombooking.contract.model.RoomInput;
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
        UUID delta = createRoom("Delta", 12);
        UUID alpha = createRoom("alpha", 4);
        UUID charlie = createDeactivatedRoom("Charlie", 8);
        UUID beta = createRoom("beta room", 6);

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
    void createsAnActiveRoomAndAnswersItWithItsLocation() {
        ResponseEntity<Room> response = http.postForEntity("/api/rooms", new RoomInput("Sala A", 4), Room.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Room created = response.getBody();
        assertThat(created).isNotNull();
        assertThat(created.getName()).isEqualTo("Sala A");
        assertThat(created.getCapacity()).isEqualTo(4);
        assertThat(created.getStatus()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/rooms/" + created.getId());
        assertThat(http.getForEntity("/api/rooms", Room[].class).getBody()).containsExactly(created);
    }

    @Test
    void trimsTheNameAtBothEndsAndKeepsItsInnerWhitespaceAsTyped() {
        Room created = http.postForObject("/api/rooms", new RoomInput("  Sala   B \t", 4), Room.class);

        assertThat(created.getName()).isEqualTo("Sala   B");
        assertThat(http.getForEntity("/api/rooms", Room[].class).getBody())
                .extracting(Room::getName)
                .containsExactly("Sala   B");
    }

    @Test
    void rejectsANameThatIsBlankOnceTrimmedAsAValidationError() {
        ResponseEntity<JsonNode> response =
                send(HttpMethod.POST, "/api/rooms", "{\"name\": \" \\t \", \"capacity\": 4}");

        JsonNode problem = assertProblem(response, HttpStatus.BAD_REQUEST);
        assertThat(problem.path("errors")).containsExactly(fieldError("name", "must not be blank"));
        assertThat(http.getForEntity("/api/rooms", Room[].class).getBody()).isEmpty();
    }

    @Test
    void deactivatesAnActiveRoomAndAnswersIt() {
        UUID id = createRoom("Sala A", 4);
        Room deactivated = new Room(id, "Sala A", 4, RoomStatus.DEACTIVATED);

        ResponseEntity<Room> response = deactivate(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(deactivated);
        assertThat(http.getForEntity("/api/rooms", Room[].class).getBody()).containsExactly(deactivated);
    }

    @Test
    void deactivatesADeactivatedRoomAgainWithoutChangingIt() {
        UUID id = createDeactivatedRoom("Sala A", 4);
        Room deactivated = new Room(id, "Sala A", 4, RoomStatus.DEACTIVATED);

        ResponseEntity<Room> response = deactivate(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(deactivated);
        assertThat(http.getForEntity("/api/rooms", Room[].class).getBody()).containsExactly(deactivated);
    }

    @Test
    void reactivatesADeactivatedRoomAndAnswersIt() {
        UUID id = createDeactivatedRoom("Sala A", 4);
        Room active = new Room(id, "Sala A", 4, RoomStatus.ACTIVE);

        ResponseEntity<Room> response = reactivate(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(active);
        assertThat(http.getForEntity("/api/rooms", Room[].class).getBody()).containsExactly(active);
    }

    @Test
    void reactivatesAnActiveRoomAgainWithoutChangingIt() {
        UUID id = createRoom("Sala A", 4);
        Room active = new Room(id, "Sala A", 4, RoomStatus.ACTIVE);

        ResponseEntity<Room> response = reactivate(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(active);
        assertThat(http.getForEntity("/api/rooms", Room[].class).getBody()).containsExactly(active);
    }

    @Test
    void keepsTheNameOfADeactivatedRoomTaken() {
        createDeactivatedRoom("Sala A", 4);
        UUID other = createRoom("Sala B", 6);

        ResponseEntity<JsonNode> create =
                send(HttpMethod.POST, "/api/rooms", "{\"name\": \"sala a\", \"capacity\": 8}");
        ResponseEntity<JsonNode> rename =
                send(HttpMethod.PUT, "/api/rooms/" + other, "{\"name\": \"SALA A\", \"capacity\": 6}");

        for (ResponseEntity<JsonNode> response : List.of(create, rename)) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(requireNonNull(response.getBody()).path("type").asText())
                    .isEqualTo("urn:room-booking:problem:room-name-taken");
        }
        assertThat(http.getForEntity("/api/rooms", Room[].class).getBody())
                .extracting(Room::getName)
                .containsExactly("Sala A", "Sala B");
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

    @Test
    void reportsEveryMissingFieldAsAValidationError() {
        JsonNode problem = assertProblem(send(HttpMethod.POST, "/api/rooms", "{}"), HttpStatus.BAD_REQUEST);

        assertThat(problem.path("errors"))
                .containsExactly(fieldError("capacity", "must not be null"), fieldError("name", "must not be null"));
    }

    @Test
    void rejectsANameAnotherRoomHasIgnoringCase() {
        createRoom("Sala A", 4);

        ResponseEntity<JsonNode> response =
                send(HttpMethod.POST, "/api/rooms", "{\"name\": \" sala a \", \"capacity\": 8}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        JsonNode problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.path("type").asText()).isEqualTo("urn:room-booking:problem:room-name-taken");
        assertThat(problem.path("title").asText()).isEqualTo("Room name taken");
        assertThat(problem.path("status").asInt()).isEqualTo(409);
        assertThat(problem.path("detail").asText()).isEqualTo("The name \"sala a\" is already taken.");
        assertThat(http.getForEntity("/api/rooms", Room[].class).getBody())
                .extracting(Room::getName)
                .containsExactly("Sala A");
    }

    @Test
    void replacesTheNameAndCapacityOfARoomAndAnswersItAsItNowIs() {
        UUID id = createRoom("Sala A", 4);

        ResponseEntity<Room> response = update(id, new RoomInput("  Sala   Grande ", 10));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Room updated = new Room(id, "Sala   Grande", 10, RoomStatus.ACTIVE);
        assertThat(response.getBody()).isEqualTo(updated);
        assertThat(http.getForEntity("/api/rooms", Room[].class).getBody()).containsExactly(updated);
    }

    @Test
    void acceptsARenameThatOnlyChangesTheCase() {
        UUID id = createRoom("sala a", 4);

        ResponseEntity<Room> response = update(id, new RoomInput("Sala A", 4));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new Room(id, "Sala A", 4, RoomStatus.ACTIVE));
    }

    @Test
    void acceptsTheSameRoomAgainWithoutChangingIt() {
        UUID id = createRoom("Sala A", 4);
        Room unchanged = new Room(id, "Sala A", 4, RoomStatus.ACTIVE);

        assertThat(update(id, new RoomInput("Sala A", 4)).getBody()).isEqualTo(unchanged);
        assertThat(update(id, new RoomInput("Sala A", 4)).getBody()).isEqualTo(unchanged);
        assertThat(http.getForEntity("/api/rooms", Room[].class).getBody()).containsExactly(unchanged);
    }

    @Test
    void editsADeactivatedRoomAndKeepsItDeactivated() {
        UUID id = createDeactivatedRoom("Sala A", 4);

        ResponseEntity<Room> response = update(id, new RoomInput("Sala B", 6));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new Room(id, "Sala B", 6, RoomStatus.DEACTIVATED));
    }

    @Test
    void rejectsARenameToANameAnotherRoomHasIgnoringCase() {
        createRoom("Sala A", 4);
        UUID id = createRoom("Sala B", 6);

        ResponseEntity<JsonNode> response =
                send(HttpMethod.PUT, "/api/rooms/" + id, "{\"name\": \"SALA A\", \"capacity\": 6}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        JsonNode problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.path("type").asText()).isEqualTo("urn:room-booking:problem:room-name-taken");
        assertThat(problem.path("detail").asText()).isEqualTo("The name \"SALA A\" is already taken.");
        assertThat(http.getForEntity("/api/rooms", Room[].class).getBody())
                .extracting(Room::getName)
                .containsExactly("Sala A", "Sala B");
    }

    @Test
    void rejectsAnInvalidEditAsOnCreate() {
        UUID id = createRoom("Sala A", 4);
        String room = "/api/rooms/" + id;

        JsonNode blankName = assertProblem(
                send(HttpMethod.PUT, room, "{\"name\": \"  \", \"capacity\": 4}"), HttpStatus.BAD_REQUEST);
        JsonNode tooLongNameAndTooSmallCapacity = assertProblem(
                send(HttpMethod.PUT, room, "{\"name\": \"" + "x".repeat(101) + "\", \"capacity\": 0}"),
                HttpStatus.BAD_REQUEST);

        assertThat(blankName.path("errors")).containsExactly(fieldError("name", "must not be blank"));
        assertThat(tooLongNameAndTooSmallCapacity.path("errors"))
                .containsExactly(
                        fieldError("capacity", "must be greater than or equal to 1"),
                        fieldError("name", "size must be between 1 and 100"));
        assertThat(http.getForEntity("/api/rooms", Room[].class).getBody())
                .containsExactly(new Room(id, "Sala A", 4, RoomStatus.ACTIVE));
    }

    @Test
    void answersAnUnknownRoomIdWithA404Problem() {
        String unknown = "/api/rooms/" + UUID.randomUUID();

        for (ResponseEntity<JsonNode> response : List.of(
                send(HttpMethod.PUT, unknown, VALID_ROOM),
                send(HttpMethod.POST, unknown + "/deactivation", null),
                send(HttpMethod.POST, unknown + "/reactivation", null))) {
            JsonNode problem = assertProblem(response, HttpStatus.NOT_FOUND);
            assertThat(problem.path("detail").asText()).isEqualTo("The room does not exist.");
        }
        assertThat(http.getForEntity("/api/rooms", Room[].class).getBody()).isEmpty();
    }

    private UUID createRoom(String name, int capacity) {
        ResponseEntity<Room> response = http.postForEntity("/api/rooms", new RoomInput(name, capacity), Room.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return requireNonNull(response.getBody()).getId();
    }

    private UUID createDeactivatedRoom(String name, int capacity) {
        UUID id = createRoom(name, capacity);
        assertThat(deactivate(id).getStatusCode()).isEqualTo(HttpStatus.OK);
        return id;
    }

    private ResponseEntity<Room> deactivate(UUID id) {
        return http.postForEntity("/api/rooms/" + id + "/deactivation", null, Room.class);
    }

    private ResponseEntity<Room> reactivate(UUID id) {
        return http.postForEntity("/api/rooms/" + id + "/reactivation", null, Room.class);
    }

    private ResponseEntity<Room> update(UUID id, RoomInput input) {
        return http.exchange("/api/rooms/" + id, HttpMethod.PUT, new HttpEntity<>(input), Room.class);
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
