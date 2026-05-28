package com.cadentia.serviceplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cadentia.serviceplan.ServicePlanModels.ServicePlanRecord;
import com.cadentia.serviceplan.ServicePlanModels.ServicePlanStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServicePlanServiceTest {

    @Mock
    private ServicePlanRepository repository;

    @Test
    void createDelegatesToRepository() {
        Instant serviceDateTime = Instant.parse("2026-06-01T10:00:00Z");
        ServicePlanRecord expected = new ServicePlanRecord(
                UUID.randomUUID(),
                serviceDateTime,
                "Sunday Service",
                "Faith",
                "Hebrews 11",
                "notes",
                ServicePlanStatus.DRAFT,
                null,
                null,
                List.of(),
                List.of());
        when(repository.create(serviceDateTime, "Sunday Service", "Faith", "Hebrews 11", "notes"))
                .thenReturn(expected);

        ServicePlanService service = new ServicePlanService(repository);

        ServicePlanRecord actual = service.create(serviceDateTime, "Sunday Service", "Faith", "Hebrews 11", "notes");

        assertThat(actual).isEqualTo(expected);
        verify(repository).create(serviceDateTime, "Sunday Service", "Faith", "Hebrews 11", "notes");
    }
}
