package com.cadentia.serviceplan;

import com.cadentia.serviceplan.ServicePlanModels.ServicePlanRecord;
import com.cadentia.serviceplan.ServicePlanModels.ServicePlanStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ServicePlanService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServicePlanService.class);

    private final ServicePlanRepository repository;
    private final MeterRegistry meterRegistry;

    public ServicePlanService(ServicePlanRepository repository) {
        this(repository, Metrics.globalRegistry);
    }

    public ServicePlanService(ServicePlanRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
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
        ServicePlanRecord reordered = repository.reorderBlocks(id, blockIds);
        counter("cadentia_service_plan_block_reorder_total", "result", "success").increment();
        return reordered;
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
        String beforeSequence = sequenceBlockIds(current);
        for (var attachment : current.attachments()) {
            if (!repository.setlistVersionExists(attachment.setlistId(), attachment.setlistVersionId())) {
                counter("cadentia_service_plan_publish_failures_total", "reason", "missing_setlist_version").increment();
                emitPublishAudit("SERVICE_PLAN_PUBLISH_CONFLICT_MISSING_REFERENCE", id, publishedBy, beforeSequence, beforeSequence, "conflict");
                throw new ServicePlanPublishConflictException(
                        "Cannot publish: missing setlist version attachment setlistId="
                                + attachment.setlistId() + ", setlistVersionId=" + attachment.setlistVersionId());
            }
            if (repository.hasNewerSetlistVersion(attachment.setlistId(), attachment.setlistVersionId())) {
                counter("cadentia_service_plan_publish_failures_total", "reason", "stale_setlist_version").increment();
                emitPublishAudit("SERVICE_PLAN_PUBLISH_CONFLICT_STALE_REFERENCE", id, publishedBy, beforeSequence, beforeSequence, "conflict");
                throw new ServicePlanPublishConflictException(
                        "Cannot publish: newer setlist version exists for setlistId="
                                + attachment.setlistId() + ". Reattach explicitly to refresh.");
            }
        }
        ServicePlanRecord published = repository.publish(id, publishedBy, publishNote);
        counter("cadentia_service_plan_publish_total", "result", "success").increment();
        counter("cadentia_service_plan_draft_to_publish_total", "result", "success").increment();
        emitPublishAudit(
                "SERVICE_PLAN_PUBLISH_SUCCESS",
                id,
                publishedBy,
                beforeSequence,
                sequenceBlockIds(published),
                "success");
        return published;
    }

    private void ensureDraftForMutation(ServicePlanRecord plan, String operation) {
        if (plan.status() != ServicePlanStatus.DRAFT) {
            throw new ServicePlanPublishConflictException(
                    operation + " require a draft service plan. Current status=" + plan.status().name().toLowerCase());
        }
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name)
                .tags(tags)
                .register(meterRegistry);
    }

    private String sequenceBlockIds(ServicePlanRecord plan) {
        return plan.blocks().stream()
                .map(block -> block.blockId().toString())
                .collect(Collectors.joining(","));
    }

    private void emitPublishAudit(
            String actionCode,
            UUID servicePlanId,
            String actor,
            String beforeSequence,
            String afterSequence,
            String result) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("action_code", actionCode);
        fields.put("service_plan_id", servicePlanId);
        fields.put("actor", actor);
        fields.put("before_sequence", beforeSequence);
        fields.put("after_sequence", afterSequence);
        fields.put("result", result);
        LOGGER.info("service_plan_audit={}", fields);
    }
}
