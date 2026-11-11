package com.roombooking.health.persistence;

import com.roombooking.health.domain.DatabaseProbe;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcDatabaseProbe implements DatabaseProbe {

    private final JdbcClient jdbc;

    JdbcDatabaseProbe(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isReachable() {
        try {
            return jdbc.sql("select 1").query(Integer.class).optional().isPresent();
        } catch (DataAccessException e) {
            return false;
        }
    }
}
