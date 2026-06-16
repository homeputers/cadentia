package com.cadentia.asset;

import com.cadentia.api.security.AssetAuthorizationPolicy.AssetDenialReason;
import com.cadentia.asset.AssetModels.AssetAttachmentRecord;
import com.cadentia.asset.AssetModels.AssetProcessingStatusCode;
import com.cadentia.asset.AssetModels.AssetTypeCode;
import com.cadentia.asset.AssetModels.AssetVersionRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AssetWorkflowModels {

    private AssetWorkflowModels() {
    }

    public enum AssetDiagnosticCode {
        MISSING,
        INACCESSIBLE,
        EXPIRED_LICENSE,
        PENDING_SCAN,
        INCOMPATIBLE_VERSION
    }

    public record ServiceAssetContext(
            String instanceId,
            UUID servicePlanId,
            UUID rehearsalSessionId,
            UUID arrangementId,
            UUID serviceItemId,
            String requiredMimeTypePrefix,
            Instant effectiveAt) {
    }

    public record ResolvedWorkflowAsset(
            AssetTypeCode assetTypeCode,
            AssetAttachmentRecord attachment,
            AssetVersionRecord version,
            boolean accessible,
            List<AssetDiagnostic> diagnostics) {
    }

    public record AssetDiagnostic(
            AssetDiagnosticCode code,
            AssetTypeCode assetTypeCode,
            UUID attachmentId,
            UUID assetVersionId,
            AssetDenialReason denialReason,
            AssetProcessingStatusCode processingStatusCode,
            String message) {
    }

    public record PinnedAssetVersion(
            UUID servicePlanId,
            UUID setlistVersionId,
            UUID setlistItemId,
            UUID arrangementId,
            AssetTypeCode assetTypeCode,
            UUID assetVersionId,
            UUID attachmentId,
            Instant pinnedAt,
            String pinnedBy) {
    }
}
