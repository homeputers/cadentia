package com.cadentia.serviceplan;

import com.cadentia.serviceplan.ServicePlanModels.ServicePlanRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServicePlanRepository {
    ServicePlanRecord create(Instant serviceDateTime, String title, String theme, String scripture, String notes);
    List<ServicePlanRecord> list();
    Optional<ServicePlanRecord> findById(UUID servicePlanId);
    ServicePlanRecord updateMetadata(UUID servicePlanId, Instant serviceDateTime, String title, String theme, String scripture, String notes);
    ServicePlanRecord reorderBlocks(UUID servicePlanId, List<UUID> blockIds);
    ServicePlanRecord attachSetlistVersion(UUID servicePlanId, UUID setlistId, UUID setlistVersionId);
    ServicePlanRecord publish(UUID servicePlanId, String publishedBy, String publishNote);
}
