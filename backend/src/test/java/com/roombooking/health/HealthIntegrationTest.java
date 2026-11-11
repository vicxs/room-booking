package com.roombooking.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.roombooking.TestcontainersConfiguration;
import com.roombooking.contract.model.Health;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class HealthIntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Test
    void reportsUpWhenTheDatabaseIsReachable() {
        ResponseEntity<Health> response = http.getForEntity("/api/health", Health.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new Health(Health.StatusEnum.UP));
    }
}
