package com.cadentia.asset;

import com.cadentia.asset.AssetModels.AssetRecord;
import com.cadentia.asset.AssetModels.AssetVersionRecord;
import com.cadentia.asset.AssetModels.ControlledVocabularyRecord;
import com.cadentia.asset.AssetModels.CreateAssetCommand;
import com.cadentia.asset.AssetModels.CreateAssetVersionCommand;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository {

    AssetRecord createAsset(CreateAssetCommand command);

    AssetVersionRecord createVersion(CreateAssetVersionCommand command);

    Optional<AssetRecord> findAsset(UUID assetId);

    List<AssetRecord> listAssets();

    Optional<AssetVersionRecord> findVersion(UUID assetVersionId);

    AssetVersionRecord transitionProcessingStatus(
            UUID assetVersionId,
            AssetModels.AssetProcessingStatusCode processingStatusCode,
            String changedBy,
            String reasonCode);

    AssetVersionRecord quarantineVersion(UUID assetVersionId, String changedBy, String reasonCode);

    AssetRecord archiveAsset(UUID assetId, String archivedBy, String reason);

    AssetVersionRecord archiveVersion(UUID assetVersionId, String archivedBy, String reason);

    List<ControlledVocabularyRecord> listAssetTypes();

    List<ControlledVocabularyRecord> listLifecycleStatuses();

    List<ControlledVocabularyRecord> listProcessingStatuses();

    List<ControlledVocabularyRecord> listLicenseStatuses();

    List<ControlledVocabularyRecord> listAccessPolicies();
}
