package com.cadentia.asset;

import java.time.Duration;
import java.util.Optional;

public class UnsupportedConfiguredAssetStorageAdapter implements AssetStorageAdapter {

    private final AssetStorageProperties properties;

    public UnsupportedConfiguredAssetStorageAdapter(AssetStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public String providerCode() {
        return properties.getProvider().trim().toUpperCase().replace('-', '_');
    }

    @Override
    public boolean exists(String storageKey) {
        throw unsupported();
    }

    @Override
    public Optional<StoredObjectMetadata> metadata(String storageKey) {
        throw unsupported();
    }

    @Override
    public SignedStorageUrl signedUploadUrl(String storageKey, String expectedMimeType, long expectedByteSize, Duration expiresIn) {
        throw unsupported();
    }

    @Override
    public SignedStorageUrl signedDownloadUrl(String storageKey, Duration expiresIn) {
        throw unsupported();
    }

    @Override
    public void copy(String sourceStorageKey, String destinationStorageKey) {
        throw unsupported();
    }

    @Override
    public void delete(String storageKey) {
        throw unsupported();
    }

    @Override
    public String quarantine(String storageKey, String reasonCode) {
        throw unsupported();
    }

    private AssetStorageException unsupported() {
        return new AssetStorageException("Asset storage provider '" + properties.getProvider()
                + "' is configured but no provider-specific AssetStorageAdapter bean is installed.");
    }
}
