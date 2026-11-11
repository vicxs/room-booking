package com.roombooking.health.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HealthCheckTest {

    @Test
    void isUpWhenTheDatabaseIsReachable() {
        assertThat(new HealthCheck(() -> true).check()).isEqualTo(HealthStatus.UP);
    }

    @Test
    void isDownWhenTheDatabaseIsNotReachable() {
        assertThat(new HealthCheck(() -> false).check()).isEqualTo(HealthStatus.DOWN);
    }
}
