package com.cadentia.asset;

import com.cadentia.api.security.AssetAuthorizationPolicy;
import com.cadentia.api.security.AssetAuthorizationPolicy.AssetAction;
import com.cadentia.api.security.AssetAuthorizationPolicy.AssetAuthorizationDecision;
import com.cadentia.api.security.AssetAuthorizationPolicy.AssetAuthorizationRequest;
import com.cadentia.asset.AssetStorageAdapter.SignedStorageUrl;
import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class AssetAccessService {

    private final AssetAuthorizationPolicy authorizationPolicy;
    private final AssetStorageAdapter storageAdapter;
    private final AssetStorageProperties storageProperties;

    public AssetAccessService(
            AssetAuthorizationPolicy authorizationPolicy,
            AssetStorageAdapter storageAdapter,
            AssetStorageProperties storageProperties) {
        this.authorizationPolicy = authorizationPolicy;
        this.storageAdapter = storageAdapter;
        this.storageProperties = storageProperties;
    }

    public SignedAssetAccess authorizeSignedDownload(AssetAuthorizationRequest request) {
        return signedAccess(request.withAction(AssetAction.GENERATE_SIGNED_DOWNLOAD_URL));
    }

    public SignedAssetAccess authorizeStreaming(AssetAuthorizationRequest request) {
        return signedAccess(request.withAction(AssetAction.STREAM));
    }

    private SignedAssetAccess signedAccess(AssetAuthorizationRequest request) {
        AssetAuthorizationDecision decision = authorizationPolicy.authorize(request);
        if (!decision.permitted()) {
            return new SignedAssetAccess(decision, null);
        }
        SignedStorageUrl signedUrl = storageAdapter.signedDownloadUrl(
                request.version().storageKey(), signedUrlTtl());
        return new SignedAssetAccess(decision, signedUrl);
    }

    private Duration signedUrlTtl() {
        return storageProperties.getSignedDownloadUrlTtl();
    }

    public record SignedAssetAccess(AssetAuthorizationDecision decision, SignedStorageUrl signedUrl) {
    }
}
