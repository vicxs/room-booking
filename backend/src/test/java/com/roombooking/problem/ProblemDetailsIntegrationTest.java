package com.roombooking.problem;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roombooking.TestcontainersConfiguration;
import java.util.List;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, ProblemDetailsIntegrationTest.FailingController.class})
class ProblemDetailsIntegrationTest {

    private static final String SECRET = "connection to db-internal-7 refused";

    @Autowired
    TestRestTemplate http;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void answersAnUnexpectedExceptionWithA500ProblemWithoutInternalDetails() throws JsonProcessingException {
        ResponseEntity<String> response = get("/api/failing");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody())
                .doesNotContain(SECRET)
                .doesNotContain(IllegalStateException.class.getSimpleName());
        JsonNode problem = parse(response.getBody());
        assertThat(problem.path("type").asText()).isEqualTo("about:blank");
        assertThat(problem.path("title").asText()).isEqualTo("Internal Server Error");
        assertThat(problem.path("status").asInt()).isEqualTo(500);
        assertThat(problem.path("detail").asText()).isEqualTo("An unexpected error occurred.");
    }

    @Test
    void answersAnUnknownPathWithA404Problem() throws JsonProcessingException {
        ResponseEntity<String> response = get("/api/no-such-thing");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(parse(response.getBody()).path("type").asText()).isEqualTo("about:blank");
    }

    private ResponseEntity<String> get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private JsonNode parse(String json) throws JsonProcessingException {
        return objectMapper.readTree(json);
    }

    @RestController
    static class FailingController {

        @GetMapping("/failing")
        String fail() {
            throw new IllegalStateException(SECRET);
        }
    }
}
