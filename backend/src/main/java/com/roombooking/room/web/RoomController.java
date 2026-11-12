package com.roombooking.room.web;

import static java.util.Objects.requireNonNull;

import com.roombooking.contract.api.RoomsApi;
import com.roombooking.contract.model.FieldError;
import com.roombooking.contract.model.Room;
import com.roombooking.contract.model.RoomInput;
import com.roombooking.contract.model.RoomStatus;
import com.roombooking.problem.web.Problems;
import com.roombooking.room.domain.InvalidRoomException;
import com.roombooking.room.domain.RoomCatalogue;
import com.roombooking.room.domain.RoomNameTakenException;
import com.roombooking.room.domain.RoomNotFoundException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
class RoomController implements RoomsApi {

    private static final URI ROOM_NAME_TAKEN = URI.create("urn:room-booking:problem:room-name-taken");

    private final RoomCatalogue catalogue;

    RoomController(RoomCatalogue catalogue) {
        this.catalogue = catalogue;
    }

    @Override
    public ResponseEntity<List<Room>> listRooms() {
        return ResponseEntity.ok(
                catalogue.list().stream().map(RoomController::toContract).toList());
    }

    @Override
    public ResponseEntity<Room> createRoom(RoomInput roomInput) {
        com.roombooking.room.domain.Room room = catalogue.create(roomInput.getName(), roomInput.getCapacity());
        return ResponseEntity.created(locationOf(room)).body(toContract(room));
    }

    @Override
    public ResponseEntity<Room> updateRoom(UUID roomId, RoomInput roomInput) {
        return ResponseEntity.ok(toContract(catalogue.update(roomId, roomInput.getName(), roomInput.getCapacity())));
    }

    @Override
    public ResponseEntity<Room> deactivateRoom(UUID roomId) {
        return ResponseEntity.ok(toContract(catalogue.deactivate(roomId)));
    }

    @Override
    public ResponseEntity<Room> reactivateRoom(UUID roomId) {
        return ResponseEntity.ok(toContract(catalogue.reactivate(roomId)));
    }

    /** A Room rule the request breaks, answered in the same shape as a Bean Validation failure. */
    @ExceptionHandler
    ResponseEntity<ProblemDetail> handleInvalidRoom(InvalidRoomException exception) {
        return Problems.invalid(List.of(new FieldError(exception.property(), exception.getMessage())));
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> handleRoomNameTaken(RoomNameTakenException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setType(ROOM_NAME_TAKEN);
        problem.setTitle("Room name taken");
        return Problems.respond(problem);
    }

    /** An {@code about:blank} 404 (the Contract's {@code NotFound}), worded for people; the URL holds the id. */
    @ExceptionHandler
    ResponseEntity<ProblemDetail> handleRoomNotFound(RoomNotFoundException exception) {
        return Problems.respond(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "The room does not exist."));
    }

    private static Room toContract(com.roombooking.room.domain.Room room) {
        return new Room(
                room.id(),
                room.name(),
                room.capacity(),
                RoomStatus.fromValue(room.status().name()));
    }

    /** The Room's path under the current request's, {@code /api/rooms/{id}}, without the scheme and host. */
    private static URI locationOf(com.roombooking.room.domain.Room room) {
        String path = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(room.id())
                .getPath();
        return URI.create(requireNonNull(path));
    }
}
