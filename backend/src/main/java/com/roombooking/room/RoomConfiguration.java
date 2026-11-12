package com.roombooking.room;

import com.roombooking.room.domain.RoomCatalogue;
import com.roombooking.room.domain.Rooms;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RoomConfiguration {

    @Bean
    RoomCatalogue roomCatalogue(Rooms rooms) {
        return new RoomCatalogue(rooms);
    }
}
