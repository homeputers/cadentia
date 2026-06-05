package com.cadentia.serviceplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    void publishRejectsUnapprovedCatalogContentDespiteReadiness() {
        // Arrange
        UUID planId = UUID.randomUUID();
        ServicePlanRecord current = new ServicePlanRecord(
                planId,
                Instant.parse("2026-06-01T10:00:00Z"),
                "Sunday Service",
                "Faith",
                "Hebrews 11",
                "notes",
                ServicePlanStatus.DRAFT,
                null,
                null,
                List.of(),
                List.of());
        when(repository.findById(planId)).thenReturn(java.util.Optional.of(current));
        when(repository.hasUnapprovedPlannedCatalogContent(planId)).thenReturn(true);

        ServicePlanService service = new ServicePlanService(repository);

        // Act / Assert
        assertThatThrownBy(() -> service.publish(planId, "system", "readiness says ready"))
                .isInstanceOf(ServicePlanPublishConflictException.class)
                .hasMessageContaining("catalog approval gates");
    }

    @Test
    void publishRejectsWhenAttachmentHasNewerVersion() {
        UUID planId = UUID.randomUUID();
        UUID setlistId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        ServicePlanRecord current = new ServicePlanRecord(
                planId,
                Instant.parse("2026-06-01T10:00:00Z"),
                "Sunday Service",
                "Faith",
                "Hebrews 11",
                "notes",
                ServicePlanStatus.DRAFT,
                null,
                null,
                List.of(),
                List.of(new ServicePlanModels.SetlistAttachment(UUID.randomUUID(), setlistId, versionId, 0)));
        when(repository.findById(planId)).thenReturn(java.util.Optional.of(current));
        when(repository.setlistVersionExists(setlistId, versionId)).thenReturn(true);
        when(repository.hasNewerSetlistVersion(setlistId, versionId)).thenReturn(true);

        ServicePlanService service = new ServicePlanService(repository);

        assertThatThrownBy(() -> service.publish(planId, "system", "note"))
                .isInstanceOf(ServicePlanPublishConflictException.class)
                .hasMessageContaining("newer setlist version exists");
    }
}
