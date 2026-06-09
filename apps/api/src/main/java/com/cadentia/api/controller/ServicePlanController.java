package com.cadentia.api.controller;

import com.cadentia.api.security.RbacAuthorities;
import com.cadentia.generated.api.ServicePlansApi;
import com.cadentia.generated.model.AttachSetlistVersionRequest;
import com.cadentia.generated.model.CreateServicePlanRequest;
import com.cadentia.generated.model.PublishServicePlanRequest;
import com.cadentia.generated.model.ReorderServicePlanBlocksRequest;
import com.cadentia.generated.model.ServicePlanBlockProvenance;
import com.cadentia.generated.model.ServicePlanPlanningBlock;
import com.cadentia.generated.model.ServicePlanBlockSourceType;
import com.cadentia.generated.model.ServicePlanBlockType;
import com.cadentia.generated.model.ServicePlanPublishResponse;
import com.cadentia.generated.model.ServicePlanResponse;
import com.cadentia.generated.model.ServicePlanSetlistAttachmentResponse;
import com.cadentia.generated.model.ServicePlanStatus;
import com.cadentia.generated.model.ServicePlanSummary;
import com.cadentia.generated.model.UpdateServicePlanRequest;
import com.cadentia.serviceplan.ServicePlanModels.ServicePlanRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalWorkflowSummaryAudience;
import com.cadentia.rehearsal.RehearsalWorkflowSummaryService;
import com.cadentia.serviceplan.ServicePlanModels.SetlistAttachment;
import com.cadentia.serviceplan.ServicePlanService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServicePlanController implements ServicePlansApi {

    private final ServicePlanService service;
    private final RehearsalWorkflowSummaryService workflowSummaryService;

    public ServicePlanController(
            ServicePlanService service,
            RehearsalWorkflowSummaryService workflowSummaryService) {
        this.service = service;
        this.workflowSummaryService = workflowSummaryService;
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<ServicePlanResponse> createServicePlan(CreateServicePlanRequest req) {
        ServicePlanRecord created = service.create(
                req.getServiceDateTime().toInstant(),
                req.getTitle(),
                req.getTheme(),
                req.getScripture(),
                req.getNotes());
        return ResponseEntity.status(201).body(toResponse(created));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<List<ServicePlanSummary>> listServicePlans() {
        List<ServicePlanSummary> summaries = service.list().stream()
                .map(r -> new ServicePlanSummary(
                        r.servicePlanId(),
                        r.title(),
                        OffsetDateTime.ofInstant(r.serviceDateTime(), ZoneOffset.UTC),
                        toStatus(r)))
                .toList();
        return ResponseEntity.ok(summaries);
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<ServicePlanResponse> getServicePlan(UUID servicePlanId) {
        return ResponseEntity.ok(toResponse(service.get(servicePlanId)));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<ServicePlanResponse> updateServicePlan(UUID servicePlanId, UpdateServicePlanRequest req) {
        ServicePlanRecord updated = service.update(
                servicePlanId,
                req.getServiceDateTime().toInstant(),
                req.getTitle(),
                req.getTheme(),
                req.getScripture(),
                req.getNotes());
        return ResponseEntity.ok(toResponse(updated));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<ServicePlanResponse> reorderServicePlanBlocks(
            UUID servicePlanId,
            ReorderServicePlanBlocksRequest req) {
        return ResponseEntity.ok(toResponse(service.reorder(servicePlanId, req.getOrderedBlockIds())));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<ServicePlanSetlistAttachmentResponse> attachSetlistVersionToServicePlan(
            UUID servicePlanId,
            AttachSetlistVersionRequest req) {
        ServicePlanRecord record = service.attach(servicePlanId, req.getSetlistId(), req.getSetlistVersionId());
        ServicePlanSetlistAttachmentResponse response = new ServicePlanSetlistAttachmentResponse();
        var attachment = record.attachments().get(record.attachments().size() - 1);
        response.setAttachmentId(attachment.attachmentId());
        response.setServicePlanId(servicePlanId);
        response.setSetlistId(attachment.setlistId());
        response.setSetlistVersionId(attachment.setlistVersionId());
        response.setAttachedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return ResponseEntity.status(201).body(response);
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<ServicePlanPublishResponse> publishServicePlan(UUID servicePlanId, PublishServicePlanRequest req) {
        ServicePlanRecord published = service.publish(
                servicePlanId,
                "system",
                req == null ? null : req.getPublishNote());
        ServicePlanPublishResponse response = new ServicePlanPublishResponse();
        response.setPublishedAt(OffsetDateTime.ofInstant(published.publishedAt(), ZoneOffset.UTC));
        response.setPublishedBy(published.publishedBy());
        return ResponseEntity.ok(response);
    }

    private ServicePlanResponse toResponse(ServicePlanRecord r) {
        ServicePlanResponse response = new ServicePlanResponse();
        response.setServicePlanId(r.servicePlanId());
        response.setServiceDateTime(OffsetDateTime.ofInstant(r.serviceDateTime(), ZoneOffset.UTC));
        response.setTitle(r.title());
        response.setTheme(r.theme());
        response.setScripture(r.scripture());
        response.setNotes(r.notes());
        response.setStatus(toStatus(r));
        response.setBlocks(r.blocks().stream().map(block -> toBlock(r.servicePlanId(), block)).toList());
        response.setSetlistAttachments(r.attachments().stream()
                .map(attachment -> toAttachment(r.servicePlanId(), attachment))
                .toList());
        if (r.readinessSummary() != null) {
            response.setReadinessSummary(r.readinessSummary().toReadinessSummary());
        }
        response.setWorkflowSummary(ServicePlanWorkflowSummaryResponseMapper.toResponse(
                workflowSummaryService.summarize(r.servicePlanId(), RehearsalWorkflowSummaryAudience.WORSHIP_LEADER)));
        return response;
    }

    private ServicePlanPlanningBlock toBlock(
            UUID servicePlanId,
            com.cadentia.serviceplan.ServicePlanModels.ServicePlanBlock block) {
        ServicePlanBlockProvenance provenance = new ServicePlanBlockProvenance()
                .sourceType(block.sourceSetlistVersionId() == null
                        ? ServicePlanBlockSourceType.MANUAL
                        : ServicePlanBlockSourceType.SETLIST_VERSION)
                .setlistVersionId(block.sourceSetlistVersionId())
                .setlistEntryId(block.sourceSetlistItemId());
        ServicePlanPlanningBlock response = new ServicePlanPlanningBlock(
                block.blockId(),
                block.positionIndex(),
                ServicePlanBlockType.fromValue(block.blockType()),
                block.blockType(),
                provenance);
        response.setNotes(block.serviceNotes());
        response.setServiceKeyOverride(block.overrideKey());
        response.setWorkflowIssues(ServicePlanWorkflowSummaryResponseMapper.toIssueResponses(
                workflowSummaryService.songIssues(servicePlanId, block.blockId(), RehearsalWorkflowSummaryAudience.WORSHIP_LEADER)));
        return response;
    }

    private ServicePlanSetlistAttachmentResponse toAttachment(UUID servicePlanId, SetlistAttachment attachment) {
        ServicePlanSetlistAttachmentResponse response = new ServicePlanSetlistAttachmentResponse();
        response.setAttachmentId(attachment.attachmentId());
        response.setServicePlanId(servicePlanId);
        response.setSetlistId(attachment.setlistId());
        response.setSetlistVersionId(attachment.setlistVersionId());
        return response;
    }

    private ServicePlanStatus toStatus(ServicePlanRecord r) {
        return ServicePlanStatus.fromValue(r.status().name().toLowerCase());
    }
}
