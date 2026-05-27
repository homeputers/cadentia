package com.cadentia.serviceplan;

import com.cadentia.serviceplan.ServicePlanModels.ServicePlanRecord;
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

    public ServicePlanRecord create(Instant serviceDateTime, String title, String theme, String scripture, String notes) {
        return repository.create(serviceDateTime, title, theme, scripture, notes);
    }

    public List<ServicePlanRecord> list() { return repository.list(); }

    public ServicePlanRecord get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Service plan not found: " + id));
    }

    public ServicePlanRecord update(UUID id, Instant serviceDateTime, String title, String theme, String scripture, String notes) {
        return repository.updateMetadata(id, serviceDateTime, title, theme, scripture, notes);
    }

    public ServicePlanRecord reorder(UUID id, List<UUID> blockIds) { return repository.reorderBlocks(id, blockIds); }

    public ServicePlanRecord attach(UUID id, UUID setlistId, UUID setlistVersionId) {
        return repository.attachSetlistVersion(id, setlistId, setlistVersionId);
    }

    public ServicePlanRecord publish(UUID id, String publishedBy, String publishNote) {
        return repository.publish(id, publishedBy, publishNote);
    }
}
