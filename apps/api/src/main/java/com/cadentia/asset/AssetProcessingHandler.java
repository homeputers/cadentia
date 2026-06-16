package com.cadentia.asset;

import com.cadentia.asset.AssetModels.AssetVersionRecord;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingJobRecord;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingJobType;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingOutcome;

public interface AssetProcessingHandler {

    AssetProcessingJobType jobType();

    String processorType();

    String processorVersion();

    AssetProcessingOutcome process(AssetVersionRecord version, AssetProcessingJobRecord job);
}
