package com.cadentia.api.controller;

import static com.cadentia.api.security.RbacAuthorities.ROLE_ADMIN;

import com.cadentia.api.security.AssetAuthorizationPolicy.AssetAccessContext;
import com.cadentia.api.security.AssetAuthorizationPolicy.AssetAction;
import com.cadentia.api.security.AssetAuthorizationPolicy.AssetActor;
import com.cadentia.api.security.AssetAuthorizationPolicy.AssetAuthorizationRequest;
import com.cadentia.asset.AssetAccessService;
import com.cadentia.asset.AssetAttachmentRepository;
import com.cadentia.asset.AssetModels;
import com.cadentia.asset.AssetModels.AssetRecord;
import com.cadentia.asset.AssetModels.AssetVersionRecord;
import com.cadentia.asset.AssetModels.CreateAssetCommand;
import com.cadentia.asset.AssetModels.CreateAssetAttachmentCommand;
import com.cadentia.asset.AssetModels.LicenseMetadata;
import com.cadentia.asset.AssetRepository;
import com.cadentia.asset.AssetUploadModels.CreatePendingUploadCommand;
import com.cadentia.asset.AssetUploadModels.FinalizeUploadCommand;
import com.cadentia.asset.AssetUploadModels.UploadInstructions;
import com.cadentia.asset.AssetUploadService;
import com.cadentia.generated.api.AssetsApi;
import com.cadentia.generated.model.Asset;
import com.cadentia.generated.model.AssetAccessPolicy;
import com.cadentia.generated.model.AssetAttachment;
import com.cadentia.generated.model.AssetAttachmentPurpose;
import com.cadentia.generated.model.AssetAttachmentTargetType;
import com.cadentia.generated.model.AssetDenialReason;
import com.cadentia.generated.model.AssetLicenseStatus;
import com.cadentia.generated.model.AssetLicensingMetadata;
import com.cadentia.generated.model.AssetLifecycleStatus;
import com.cadentia.generated.model.AssetProcessingStatus;
import com.cadentia.generated.model.AssetSignedAccessRequest;
import com.cadentia.generated.model.AssetSignedAccessResponse;
import com.cadentia.generated.model.AssetType;
import com.cadentia.generated.model.AssetUploadFinalizationRequest;
import com.cadentia.generated.model.AssetUploadInstructions;
import com.cadentia.generated.model.AssetUploadRequest;
import com.cadentia.generated.model.AssetVersion;
import com.cadentia.generated.model.CreateAssetAttachmentRequest;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AssetController implements AssetsApi {

    private static final String INSTANCE_ID = "local-instance";
    private static final String ACTOR = "system";

    private final AssetRepository assetRepository;
    private final AssetUploadService uploadService;
    private final AssetAttachmentRepository attachmentRepository;
    private final AssetAccessService accessService;

    public AssetController(
            AssetRepository assetRepository,
            AssetUploadService uploadService,
            AssetAttachmentRepository attachmentRepository,
            AssetAccessService accessService) {
        this.assetRepository = assetRepository;
        this.uploadService = uploadService;
        this.attachmentRepository = attachmentRepository;
        this.accessService = accessService;
    }

    @Override
    public ResponseEntity<AssetUploadInstructions> createAssetUpload(AssetUploadRequest request) {
        UUID assetId = request.getAssetId();
        if (assetId == null) {
            AssetRecord created = assetRepository.createAsset(new CreateAssetCommand(
                    modelType(request.getAssetType()),
                    request.getTitle(),
                    request.getDescription(),
                    ACTOR,
                    null,
                    modelPolicy(request.getAccessPolicy()),
                    AssetModels.AssetLifecycleStatusCode.DRAFT,
                    ACTOR));
            assetId = created.id();
        }
        UploadInstructions instructions = uploadService.createPendingUpload(new CreatePendingUploadCommand(
                INSTANCE_ID,
                assetId,
                modelType(request.getAssetType()),
                request.getVersionNumber() == null ? 1 : request.getVersionNumber(),
                request.getRevisionCode(),
                request.getChecksumAlgorithm(),
                request.getChecksumValue(),
                request.getExpectedMimeType(),
                request.getExpectedByteSize(),
                null,
                null,
                ACTOR,
                modelPolicy(request.getAccessPolicy()),
                license(request.getLicensing(), modelPolicy(request.getAccessPolicy()))));
        return ResponseEntity.status(201).body(new AssetUploadInstructions(
                instructions.uploadId(),
                instructions.storageKey(),
                instructions.method(),
                URI.create(instructions.uploadUrl()),
                OffsetDateTime.ofInstant(instructions.expiresAt(), ZoneOffset.UTC),
                instructions.mimeType(),
                instructions.byteSize(),
                instructions.checksumAlgorithm(),
                instructions.checksumValue()));
    }

    @Override
    public ResponseEntity<AssetUploadInstructions> createAssetVersionUpload(
            UUID assetId,
            AssetUploadRequest request) {
        request.setAssetId(assetId);
        return createAssetUpload(request);
    }

    @Override
    public ResponseEntity<AssetVersion> finalizeAssetUpload(UUID uploadId, AssetUploadFinalizationRequest request) {
        AssetVersionRecord version = uploadService.finalizeUpload(new FinalizeUploadCommand(
                uploadId,
                INSTANCE_ID,
                ACTOR,
                request.getStorageKey()));
        return ResponseEntity.ok(toVersion(version));
    }

    @Override
    public ResponseEntity<List<Asset>> listAssets() {
        return ResponseEntity.ok(assetRepository.listAssets().stream()
                .map(this::toAsset)
                .toList());
    }

    @Override
    public ResponseEntity<Asset> getAsset(UUID assetId) {
        return ResponseEntity.ok(toAsset(assetRepository.findAsset(assetId).orElseThrow()));
    }

    @Override
    public ResponseEntity<List<AssetVersion>> listAssetVersions(UUID assetId) {
        return ResponseEntity.ok(assetRepository.findAsset(assetId).orElseThrow().versions().stream()
                .map(this::toVersion)
                .toList());
    }

    @Override
    public ResponseEntity<AssetLicensingMetadata> getAssetVersionLicensing(UUID assetId, UUID assetVersionId) {
        AssetVersionRecord version = assetRepository.findVersion(assetVersionId).orElseThrow();
        return ResponseEntity.ok(toLicense(version.licenseMetadata(), true));
    }

    @Override
    public ResponseEntity<AssetSignedAccessResponse> requestAssetAccess(
            UUID assetId,
            UUID assetVersionId,
            AssetSignedAccessRequest request) {
        AssetRecord asset = assetRepository.findAsset(assetId).orElseThrow();
        AssetVersionRecord version = assetRepository.findVersion(assetVersionId).orElseThrow();
        AssetAuthorizationRequest authorizationRequest = new AssetAuthorizationRequest(
                AssetAction.GENERATE_SIGNED_DOWNLOAD_URL,
                new AssetActor(ACTOR, INSTANCE_ID, Set.of(ROLE_ADMIN), true),
                INSTANCE_ID,
                asset,
                version,
                null,
                new AssetAccessContext(
                        request.getServicePlanId(),
                        request.getRehearsalSessionId(),
                        null,
                        false,
                        false,
                        false));
        AssetAccessService.SignedAssetAccess access =
                request.getAccessMode() == AssetSignedAccessRequest.AccessModeEnum.STREAM
                        ? accessService.authorizeStreaming(authorizationRequest)
                        : accessService.authorizeSignedDownload(authorizationRequest);
        AssetSignedAccessResponse response = new AssetSignedAccessResponse(
                access.decision().permitted(),
                AssetDenialReason.valueOf(access.decision().reasonCode().name()));
        if (access.signedUrl() != null) {
            response.setAccessUrl(access.signedUrl().url());
            response.setMethod(access.signedUrl().method());
            response.setExpiresAt(OffsetDateTime.ofInstant(access.signedUrl().expiresAt(), ZoneOffset.UTC));
        }
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<AssetAttachment> createAssetAttachment(CreateAssetAttachmentRequest request) {
        var created = attachmentRepository.createAttachment(new CreateAssetAttachmentCommand(
                modelTarget(request.getTargetType()),
                request.getTargetId(),
                request.getServicePlanId(),
                request.getAssetVersionId(),
                modelType(request.getAttachmentType()),
                request.getDisplayLabel(),
                request.getSortOrder(),
                AssetModels.AssetAttachmentPurposeCode.valueOf(request.getPurpose().name()),
                request.getRequiredForUse(),
                null,
                null,
                modelPolicy(request.getVisibilityPolicy()),
                ACTOR));
        return ResponseEntity.status(201).body(toAttachment(created));
    }

    @Override
    public ResponseEntity<List<AssetAttachment>> listAssetAttachments(
            AssetAttachmentTargetType targetType,
            UUID targetId) {
        return ResponseEntity.ok(attachmentRepository.listAttachments(modelTarget(targetType), targetId).stream()
                .map(this::toAttachment)
                .toList());
    }

    @Override
    public ResponseEntity<Void> archiveAsset(UUID assetId) {
        assetRepository.archiveAsset(assetId, ACTOR, "API_ARCHIVE");
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> archiveAssetVersion(UUID assetId, UUID assetVersionId) {
        AssetVersionRecord version = assetRepository.findVersion(assetVersionId).orElseThrow();
        if (!version.assetId().equals(assetId)) {
            throw new IllegalArgumentException("Asset version does not belong to asset");
        }
        assetRepository.archiveVersion(assetVersionId, ACTOR, "API_ARCHIVE");
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> archiveAssetAttachment(UUID attachmentId) {
        attachmentRepository.archiveAttachment(
                new AssetModels.ArchiveAssetAttachmentCommand(attachmentId, ACTOR, "api archive"));
        return ResponseEntity.noContent().build();
    }

    private Asset toAsset(AssetRecord record) {
        Asset asset = new Asset(
                record.id(),
                record.stableIdentifier(),
                AssetType.valueOf(record.assetTypeCode().name()),
                record.title(),
                AssetAccessPolicy.valueOf(record.defaultAccessPolicyCode().name()),
                AssetLifecycleStatus.valueOf(record.lifecycleStatusCode().name()),
                OffsetDateTime.ofInstant(record.createdAt(), ZoneOffset.UTC));
        asset.setDescription(record.description());
        asset.setOwnerActor(record.ownerActor());
        asset.setOwningMinistry(record.owningMinistry());
        asset.setCurrentAssetVersionId(record.currentAssetVersionId());
        asset.setUpdatedAt(OffsetDateTime.ofInstant(record.updatedAt(), ZoneOffset.UTC));
        asset.setVersions(record.versions().stream().map(this::toVersion).toList());
        return asset;
    }

    private AssetVersion toVersion(AssetVersionRecord record) {
        return new AssetVersion(
                record.id(),
                record.stableIdentifier(),
                record.assetId(),
                record.versionNumber(),
                record.mimeType(),
                record.byteSize(),
                record.checksumAlgorithm(),
                record.checksumValue(),
                AssetLifecycleStatus.valueOf(record.lifecycleStatusCode().name()),
                AssetProcessingStatus.valueOf(record.processingStatusCode().name()),
                AssetAccessPolicy.valueOf(record.accessPolicyCode().name()),
                OffsetDateTime.ofInstant(record.createdAt(), ZoneOffset.UTC),
                toLicense(record.licenseMetadata(), false))
                .revisionCode(record.revisionCode())
                .sourceUri(record.sourceUri())
                .provenanceSummary(record.provenanceSummary())
                .createdBy(record.createdBy());
    }

    private AssetAttachment toAttachment(AssetModels.AssetAttachmentRecord record) {
        AssetAttachment attachment = new AssetAttachment(
                record.id(),
                AssetAttachmentTargetType.valueOf(record.targetTypeCode().name()),
                record.targetId(),
                record.assetVersionId(),
                AssetType.valueOf(record.attachmentTypeCode().name()),
                record.displayLabel(),
                record.sortOrder(),
                AssetAttachmentPurpose.valueOf(record.purposeCode().name()),
                record.requiredForUse(),
                AssetAccessPolicy.valueOf(record.visibilityPolicyCode().name()),
                OffsetDateTime.ofInstant(record.createdAt(), ZoneOffset.UTC));
        attachment.setServicePlanId(record.servicePlanId());
        if (record.archivedAt() != null) {
            attachment.setArchivedAt(OffsetDateTime.ofInstant(record.archivedAt(), ZoneOffset.UTC));
        }
        return attachment;
    }

    private AssetLicensingMetadata toLicense(LicenseMetadata metadata, boolean privateVisible) {
        AssetLicensingMetadata dto = new AssetLicensingMetadata(
                AssetLicenseStatus.valueOf(metadata.licenseStatusCode().name()),
                privateVisible);
        dto.setLicenseSource(metadata.licenseSource());
        dto.setUsageRestrictions(metadata.usageRestrictions());
        dto.setExpiresAt(metadata.expiresAt() == null
                ? null
                : OffsetDateTime.ofInstant(metadata.expiresAt(), ZoneOffset.UTC));
        dto.setLicenseReference(privateVisible ? metadata.licenseReference() : null);
        dto.setLicenseHolder(privateVisible ? metadata.licenseHolder() : null);
        dto.setVisibilityPolicy(AssetAccessPolicy.valueOf(metadata.visibilityPolicyCode().name()));
        return dto;
    }

    private LicenseMetadata license(AssetLicensingMetadata dto, AssetModels.AssetAccessPolicyCode fallbackPolicy) {
        if (dto == null) {
            return new LicenseMetadata(
                    AssetModels.AssetLicenseStatusCode.UNKNOWN,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    fallbackPolicy);
        }
        return new LicenseMetadata(
                AssetModels.AssetLicenseStatusCode.valueOf(dto.getLicenseStatus().name()),
                dto.getLicenseSource(),
                dto.getLicenseReference(),
                dto.getUsageRestrictions(),
                dto.getLicenseHolder(),
                null,
                dto.getExpiresAt() == null ? null : dto.getExpiresAt().toInstant(),
                dto.getVisibilityPolicy() == null ? fallbackPolicy : modelPolicy(dto.getVisibilityPolicy()));
    }

    private AssetModels.AssetTypeCode modelType(AssetType type) {
        return AssetModels.AssetTypeCode.valueOf(type.name());
    }

    private AssetModels.AssetAccessPolicyCode modelPolicy(AssetAccessPolicy policy) {
        return AssetModels.AssetAccessPolicyCode.valueOf(policy.name());
    }

    private AssetModels.AssetAttachmentTargetTypeCode modelTarget(AssetAttachmentTargetType targetType) {
        return AssetModels.AssetAttachmentTargetTypeCode.valueOf(targetType.name());
    }
}
