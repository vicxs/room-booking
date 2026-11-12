package com.roombooking.room.web;

import com.roombooking.contract.api.RoomsApi;
import com.roombooking.contract.model.Room;
import com.roombooking.contract.model.RoomInput;
import com.roombooking.contract.model.RoomStatus;
import com.roombooking.room.domain.RoomCatalogue;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class RoomController implements RoomsApi {

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
        throw notImplementedYet();
    }

    @Override
    public ResponseEntity<Room> updateRoom(UUID roomId, RoomInput roomInput) {
        throw notImplementedYet();
    }

    @Override
    public ResponseEntity<Room> deactivateRoom(UUID roomId) {
        throw notImplementedYet();
    }

    @Override
    public ResponseEntity<Room> reactivateRoom(UUID roomId) {
        throw notImplementedYet();
    }

    private static Room toContract(com.roombooking.room.domain.Room room) {
        return new Room(
                room.id(),
                room.name(),
                room.capacity(),
                RoomStatus.fromValue(room.status().name()));
    }

    /** Answers the Contract operations whose Work item has not landed yet (#18). */
    private static ResponseStatusException notImplementedYet() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "This operation is not implemented yet.");
    }
}
