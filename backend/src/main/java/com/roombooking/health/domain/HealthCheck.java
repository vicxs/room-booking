package com.roombooking.health.domain;

import static java.util.Objects.requireNonNull;

/** The service is up only when its database is reachable. */
public final class HealthCheck {

    private final DatabaseProbe databaseProbe;

    public HealthCheck(DatabaseProbe databaseProbe) {
        this.databaseProbe = requireNonNull(databaseProbe);
    }

    public HealthStatus check() {
        return databaseProbe.isReachable() ? HealthStatus.UP : HealthStatus.DOWN;
    }
}
