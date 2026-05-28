package com.cadentia.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cadentia.serviceplan.ServicePlanModels.ServicePlanRecord;
import com.cadentia.serviceplan.ServicePlanModels.ServicePlanStatus;
import com.cadentia.serviceplan.ServicePlanRepository;
import com.cadentia.serviceplan.ServicePlanService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ServicePlanController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ServicePlanControllerIntegrationTest.TestConfig.class)
class ServicePlanControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createServicePlanReturnsCreatedResponse() throws Exception {
        String body = """
                {
                  \"serviceDateTime\": \"2026-06-01T10:00:00Z\",
                  \"title\": \"Sunday Service\",
                  \"theme\": \"Faithfulness\",
                  \"scripture\": \"Psalm 100\",
                  \"notes\": \"Opening service\"
                }
                """;

        mockMvc.perform(post("/service-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.servicePlanId").value("11111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.title").value("Sunday Service"))
                .andExpect(jsonPath("$.status").value("draft"));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        ServicePlanService servicePlanService() {
            return new ServicePlanService(new StubRepository());
        }
    }

    static class StubRepository implements ServicePlanRepository {

        @Override
        public ServicePlanRecord create(
                Instant serviceDateTime,
                String title,
                String theme,
                String scripture,
                String notes) {
            return new ServicePlanRecord(
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    serviceDateTime,
                    title,
                    theme,
                    scripture,
                    notes,
                    ServicePlanStatus.DRAFT,
                    null,
                    null,
                    List.of(),
                    List.of());
        }

        @Override
        public List<ServicePlanRecord> list() {
            return List.of();
        }

        @Override
        public Optional<ServicePlanRecord> findById(UUID servicePlanId) {
            return Optional.empty();
        }

        @Override
        public ServicePlanRecord updateMetadata(UUID servicePlanId, Instant serviceDateTime, String title, String theme, String scripture, String notes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServicePlanRecord reorderBlocks(UUID servicePlanId, List<UUID> blockIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServicePlanRecord attachSetlistVersion(
                UUID servicePlanId,
                UUID setlistId,
                UUID setlistVersionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServicePlanRecord publish(UUID servicePlanId, String publishedBy, String publishNote) {
            throw new UnsupportedOperationException();
        }
    }
}
