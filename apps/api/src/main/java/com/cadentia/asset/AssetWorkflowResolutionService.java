package com.cadentia.asset;

import static com.cadentia.api.security.AssetAuthorizationPolicy.AssetAction.GENERATE_SIGNED_DOWNLOAD_URL;
import static com.cadentia.api.security.AssetAuthorizationPolicy.AssetDenialReason.ACCESS_POLICY_DENIED;
import static com.cadentia.api.security.AssetAuthorizationPolicy.AssetDenialReason.LICENSE_EXPIRED;
import static com.cadentia.api.security.AssetAuthorizationPolicy.AssetDenialReason.PROCESSING_NOT_READY;
import static com.cadentia.asset.AssetModels.AssetAttachmentTargetTypeCode.ARRANGEMENT;
import static com.cadentia.asset.AssetModels.AssetAttachmentTargetTypeCode.SERVICE_ITEM;

import com.cadentia.api.security.AssetAuthorizationPolicy;
import com.cadentia.api.security.AssetAuthorizationPolicy.AssetAccessContext;
import com.cadentia.api.security.AssetAuthorizationPolicy.AssetActor;
import com.cadentia.api.security.AssetAuthorizationPolicy.AssetAuthorizationDecision;
import com.cadentia.api.security.AssetAuthorizationPolicy.AssetAuthorizationRequest;
import com.cadentia.asset.AssetModels.AssetAttachmentRecord;
import com.cadentia.asset.AssetModels.AssetLifecycleStatusCode;
import com.cadentia.asset.AssetModels.AssetLicenseStatusCode;
import com.cadentia.asset.AssetModels.AssetProcessingStatusCode;
import com.cadentia.asset.AssetModels.AssetTypeCode;
import com.cadentia.asset.AssetModels.AssetVersionRecord;
import com.cadentia.asset.AssetWorkflowModels.AssetDiagnostic;
import com.cadentia.asset.AssetWorkflowModels.AssetDiagnosticCode;
import com.cadentia.asset.AssetWorkflowModels.PinnedAssetVersion;
import com.cadentia.asset.AssetWorkflowModels.ResolvedWorkflowAsset;
import com.cadentia.asset.AssetWorkflowModels.ServiceAssetContext;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AssetWorkflowResolutionService {

    private final AssetAttachmentRepository attachmentRepository;
    private final AssetRepository assetRepository;
    private final AssetAuthorizationPolicy authorizationPolicy;
    private final Clock clock;

    public AssetWorkflowResolutionService(
            AssetAttachmentRepository attachmentRepository,
            AssetRepository assetRepository,
            AssetAuthorizationPolicy authorizationPolicy) {
        this(attachmentRepository, assetRepository, authorizationPolicy, Clock.systemUTC());
    }

    public AssetWorkflowResolutionService(
            AssetAttachmentRepository attachmentRepository,
            AssetRepository assetRepository,
            AssetAuthorizationPolicy authorizationPolicy,
            Clock clock) {
        this.attachmentRepository = attachmentRepository;
        this.assetRepository = assetRepository;
        this.authorizationPolicy = authorizationPolicy;
        this.clock = clock;
    }

    public Optional<ResolvedWorkflowAsset> preferredAsset(
            AssetTypeCode assetTypeCode,
            ServiceAssetContext context,
            AssetActor actor) {
        return resolveAssets(List.of(assetTypeCode), context, actor).stream().findFirst();
    }

    public List<AssetDiagnostic> diagnosticsForContext(
            List<AssetTypeCode> requestedTypes,
            ServiceAssetContext context,
            AssetActor actor) {
        List<AssetDiagnostic> diagnostics = candidateAttachments(context).stream()
                .filter(attachment -> requestedTypes.contains(attachment.attachmentTypeCode()))
                .filter(attachment -> activeForContext(attachment, context))
                .map(attachment -> resolveAttachment(attachment, context, actor))
                .flatMap(asset -> asset.diagnostics().stream())
                .toList();
        List<AssetDiagnostic> missingDiagnostics = requestedTypes.stream()
                .filter(type -> candidateAttachments(context).stream()
                        .noneMatch(attachment -> attachment.attachmentTypeCode() == type
                                && activeForContext(attachment, context)))
                .map(type -> missing(type).diagnostics().get(0))
                .toList();
        List<AssetDiagnostic> allDiagnostics = new ArrayList<>(diagnostics);
        allDiagnostics.addAll(missingDiagnostics);
        return List.copyOf(allDiagnostics);
    }

    public List<ResolvedWorkflowAsset> resolveAssets(
            List<AssetTypeCode> requestedTypes,
            ServiceAssetContext context,
            AssetActor actor) {
        List<AssetAttachmentRecord> attachments = candidateAttachments(context).stream()
                .filter(attachment -> requestedTypes.contains(attachment.attachmentTypeCode()))
                .filter(attachment -> activeForContext(attachment, context))
                .toList();
        List<ResolvedWorkflowAsset> resolved = attachments.stream()
                .map(attachment -> resolveAttachment(attachment, context, actor))
                .sorted(Comparator
                        .comparing(ResolvedWorkflowAsset::accessible).reversed()
                        .thenComparing(asset -> asset.attachment().attachmentTypeCode().name())
                        .thenComparing(asset -> !asset.attachment().requiredForUse())
                        .thenComparing(asset -> asset.attachment().sortOrder())
                        .thenComparing(asset -> asset.version().versionNumber(), Comparator.reverseOrder())
                        .thenComparing(asset -> asset.version().id()))
                .toList();

        List<ResolvedWorkflowAsset> preferred = new ArrayList<>();
        for (AssetTypeCode requestedType : requestedTypes) {
            resolved.stream()
                    .filter(asset -> asset.assetTypeCode() == requestedType)
                    .findFirst()
                    .ifPresentOrElse(preferred::add, () -> preferred.add(missing(requestedType)));
        }
        return List.copyOf(preferred);
    }

    public List<PinnedAssetVersion> pinResolvedVersions(
            UUID servicePlanId,
            UUID setlistVersionId,
            UUID setlistItemId,
            UUID arrangementId,
            List<ResolvedWorkflowAsset> resolvedAssets,
            String pinnedBy) {
        Instant pinnedAt = clock.instant();
        return resolvedAssets.stream()
                .filter(ResolvedWorkflowAsset::accessible)
                .map(asset -> new PinnedAssetVersion(
                        servicePlanId,
                        setlistVersionId,
                        setlistItemId,
                        arrangementId,
                        asset.assetTypeCode(),
                        asset.version().id(),
                        asset.attachment().id(),
                        pinnedAt,
                        pinnedBy))
                .toList();
    }

    private List<AssetAttachmentRecord> candidateAttachments(ServiceAssetContext context) {
        List<AssetAttachmentRecord> attachments = new ArrayList<>();
        if (context.arrangementId() != null) {
            attachments.addAll(attachmentRepository.listAttachments(ARRANGEMENT, context.arrangementId()));
        }
        if (context.serviceItemId() != null) {
            attachments.addAll(attachmentRepository.listAttachments(SERVICE_ITEM, context.serviceItemId()));
        }
        return attachments;
    }

    private ResolvedWorkflowAsset resolveAttachment(
            AssetAttachmentRecord attachment,
            ServiceAssetContext context,
            AssetActor actor) {
        AssetVersionRecord version = assetRepository.findVersion(attachment.assetVersionId()).orElseThrow();
        AssetAuthorizationDecision decision = authorizationPolicy.authorize(new AssetAuthorizationRequest(
                GENERATE_SIGNED_DOWNLOAD_URL,
                actor,
                context.instanceId(),
                null,
                version,
                attachment,
                new AssetAccessContext(
                        context.servicePlanId(),
                        context.rehearsalSessionId(),
                        attachment.targetId(),
                        true,
                        context.rehearsalSessionId() != null,
                        true)));
        List<AssetDiagnostic> diagnostics = diagnostics(attachment, version, decision, context);
        return new ResolvedWorkflowAsset(attachment.attachmentTypeCode(), attachment, version, decision.permitted(), diagnostics);
    }

    private List<AssetDiagnostic> diagnostics(
            AssetAttachmentRecord attachment,
            AssetVersionRecord version,
            AssetAuthorizationDecision decision,
            ServiceAssetContext context) {
        List<AssetDiagnostic> diagnostics = new ArrayList<>();
        if (!decision.permitted()) {
            diagnostics.add(new AssetDiagnostic(
                    decision.reasonCode() == LICENSE_EXPIRED
                            ? AssetDiagnosticCode.EXPIRED_LICENSE
                            : AssetDiagnosticCode.INACCESSIBLE,
                    attachment.attachmentTypeCode(),
                    attachment.id(),
                    version.id(),
                    decision.reasonCode(),
                    version.processingStatusCode(),
                    redactedMessage(decision.reasonCode())));
        }
        if (version.processingStatusCode() != AssetProcessingStatusCode.READY) {
            diagnostics.add(new AssetDiagnostic(
                    version.processingStatusCode() == AssetProcessingStatusCode.PENDING_SCAN
                            ? AssetDiagnosticCode.PENDING_SCAN
                            : AssetDiagnosticCode.INACCESSIBLE,
                    attachment.attachmentTypeCode(),
                    attachment.id(),
                    version.id(),
                    version.processingStatusCode() == AssetProcessingStatusCode.PENDING_SCAN ? PROCESSING_NOT_READY : decision.reasonCode(),
                    version.processingStatusCode(),
                    "Asset processing is not ready."));
        }
        if (version.lifecycleStatusCode() != AssetLifecycleStatusCode.AVAILABLE
                || incompatibleMime(version, context.requiredMimeTypePrefix())) {
            diagnostics.add(new AssetDiagnostic(
                    AssetDiagnosticCode.INCOMPATIBLE_VERSION,
                    attachment.attachmentTypeCode(),
                    attachment.id(),
                    version.id(),
                    decision.reasonCode(),
                    version.processingStatusCode(),
                    "Asset version is not compatible with this service context."));
        }
        if (version.licenseMetadata().licenseStatusCode() == AssetLicenseStatusCode.EXPIRED) {
            diagnostics.add(new AssetDiagnostic(
                    AssetDiagnosticCode.EXPIRED_LICENSE,
                    attachment.attachmentTypeCode(),
                    attachment.id(),
                    version.id(),
                    LICENSE_EXPIRED,
                    version.processingStatusCode(),
                    "Asset license is not valid for use."));
        }
        return List.copyOf(diagnostics);
    }

    private static boolean incompatibleMime(AssetVersionRecord version, String requiredMimeTypePrefix) {
        return requiredMimeTypePrefix != null
                && !requiredMimeTypePrefix.isBlank()
                && (version.mimeType() == null || !version.mimeType().startsWith(requiredMimeTypePrefix));
    }

    private static String redactedMessage(AssetAuthorizationPolicy.AssetDenialReason reasonCode) {
        if (reasonCode == ACCESS_POLICY_DENIED) {
            return "Asset is not accessible to this actor.";
        }
        if (reasonCode == LICENSE_EXPIRED) {
            return "Asset license is not valid for use.";
        }
        return "Asset version is not available for this context.";
    }

    private boolean activeForContext(AssetAttachmentRecord attachment, ServiceAssetContext context) {
        Instant at = Objects.requireNonNullElse(context.effectiveAt(), clock.instant());
        return attachment.archivedAt() == null
                && (attachment.effectiveFrom() == null || !attachment.effectiveFrom().isAfter(at))
                && (attachment.effectiveUntil() == null || attachment.effectiveUntil().isAfter(at));
    }

    private static ResolvedWorkflowAsset missing(AssetTypeCode assetTypeCode) {
        return new ResolvedWorkflowAsset(
                assetTypeCode,
                null,
                null,
                false,
                List.of(new AssetDiagnostic(
                        AssetDiagnosticCode.MISSING,
                        assetTypeCode,
                        null,
                        null,
                        null,
                        null,
                        "No matching asset version is attached for this context.")));
    }
}
