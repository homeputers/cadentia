package com.cadentia.serviceplan;

import com.cadentia.serviceplan.ServicePlanModels.ServicePlanRecord;
import com.cadentia.serviceplan.ServicePlanModels.ServicePlanStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ServicePlanService {

    private final ServicePlanRepository repository;

    public ServicePlanService(ServicePlanRepository repository) {
        this.repository = repository;
    }

    public ServicePlanRecord create(
            Instant serviceDateTime,
            String title,
            String theme,
            String scripture,
            String notes) {
        return repository.create(serviceDateTime, title, theme, scripture, notes);
    }

    public List<ServicePlanRecord> list() {
        return repository.list();
    }

    public ServicePlanRecord get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Service plan not found: " + id));
    }

    public ServicePlanRecord update(
            UUID id,
            Instant serviceDateTime,
            String title,
            String theme,
            String scripture,
            String notes) {
        ServicePlanRecord current = get(id);
        ensureDraftForMutation(current, "Metadata updates");
        return repository.updateMetadata(id, serviceDateTime, title, theme, scripture, notes);
    }

    public ServicePlanRecord reorder(UUID id, List<UUID> blockIds) {
        ensureDraftForMutation(get(id), "Block reordering");
        return repository.reorderBlocks(id, blockIds);
    }

    public ServicePlanRecord attach(UUID id, UUID setlistId, UUID setlistVersionId) {
        ensureDraftForMutation(get(id), "Setlist attachments");
        if (!repository.setlistVersionExists(setlistId, setlistVersionId)) {
            throw new ServicePlanPublishConflictException(
                    "Referenced setlist version does not exist or is inaccessible for setlistId="
                            + setlistId + ", setlistVersionId=" + setlistVersionId);
        }
        return repository.attachSetlistVersion(id, setlistId, setlistVersionId);
    }

    public ServicePlanRecord publish(UUID id, String publishedBy, String publishNote) {
        ServicePlanRecord current = get(id);
        for (var attachment : current.attachments()) {
            if (!repository.setlistVersionExists(attachment.setlistId(), attachment.setlistVersionId())) {
                throw new ServicePlanPublishConflictException(
                        "Cannot publish: missing setlist version attachment setlistId="
                                + attachment.setlistId() + ", setlistVersionId=" + attachment.setlistVersionId());
            }
            if (repository.hasNewerSetlistVersion(attachment.setlistId(), attachment.setlistVersionId())) {
                throw new ServicePlanPublishConflictException(
                        "Cannot publish: newer setlist version exists for setlistId="
                                + attachment.setlistId() + ". Reattach explicitly to refresh.");
            }
        }
        return repository.publish(id, publishedBy, publishNote);
    }

    private void ensureDraftForMutation(ServicePlanRecord plan, String operation) {
        if (plan.status() != ServicePlanStatus.DRAFT) {
            throw new ServicePlanPublishConflictException(
                    operation + " require a draft service plan. Current status=" + plan.status().name().toLowerCase());
        }
    }
}
