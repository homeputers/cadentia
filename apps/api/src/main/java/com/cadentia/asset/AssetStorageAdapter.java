package com.cadentia.asset;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface AssetStorageAdapter {

    String providerCode();

    boolean exists(String storageKey);

    Optional<StoredObjectMetadata> metadata(String storageKey);

    default boolean verifyDigest(String storageKey, String algorithm, String expectedDigest) {
        return metadata(storageKey)
                .flatMap(objectMetadata -> objectMetadata.digest(algorithm))
                .map(actualDigest -> actualDigest.equalsIgnoreCase(expectedDigest))
                .orElse(false);
    }

    SignedStorageUrl signedUploadUrl(String storageKey, String expectedMimeType, long expectedByteSize, Duration expiresIn);

    SignedStorageUrl signedDownloadUrl(String storageKey, Duration expiresIn);

    void copy(String sourceStorageKey, String destinationStorageKey);

    default void move(String sourceStorageKey, String destinationStorageKey) {
        copy(sourceStorageKey, destinationStorageKey);
        delete(sourceStorageKey);
    }

    void delete(String storageKey);

    String quarantine(String storageKey, String reasonCode);

    record SignedStorageUrl(URI url, Instant expiresAt, String method, String storageKey) {
    }
}
