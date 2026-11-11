package com.roombooking.health.web;

import com.roombooking.contract.api.HealthApi;
import com.roombooking.contract.model.Health;
import com.roombooking.health.domain.HealthCheck;
import com.roombooking.health.domain.HealthStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthController implements HealthApi {

    private final HealthCheck healthCheck;

    HealthController(HealthCheck healthCheck) {
        this.healthCheck = healthCheck;
    }

    @Override
    public ResponseEntity<Health> getHealth() {
        HealthStatus status = healthCheck.check();
        Health body = new Health(Health.StatusEnum.fromValue(status.name()));
        HttpStatus httpStatus = status == HealthStatus.UP ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(body);
    }
}
