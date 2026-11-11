package com.roombooking.health.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roombooking.health.domain.HealthCheck;
import com.roombooking.health.domain.HealthStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    HealthCheck healthCheck;

    @Test
    void answersServiceUnavailableWhenDown() throws Exception {
        given(healthCheck.check()).willReturn(HealthStatus.DOWN);

        mvc.perform(get("/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"status\":\"DOWN\"}", JsonCompareMode.STRICT));
    }

    @Test
    void answersOkWhenUp() throws Exception {
        given(healthCheck.check()).willReturn(HealthStatus.UP);

        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT));
    }
}
