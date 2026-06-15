package com.cadentia.asset;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "cadentia.asset-storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalFilesystemAssetStorageAdapter implements AssetStorageAdapter {

    private static final String SHA_256 = "SHA-256";

    private final AssetStorageProperties properties;

    public LocalFilesystemAssetStorageAdapter(AssetStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public String providerCode() {
        return "LOCAL_FILESYSTEM";
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.isRegularFile(resolve(storageKey));
    }

    @Override
    public Optional<StoredObjectMetadata> metadata(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            String mimeType = Files.probeContentType(path);
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = "application/octet-stream";
            }
            return Optional.of(new StoredObjectMetadata(
                    storageKey,
                    Files.size(path),
                    mimeType,
                    Files.getLastModifiedTime(path).toInstant(),
                    Map.of(SHA_256, sha256(path))));
        } catch (IOException ex) {
            throw new AssetStorageException("Unable to read local asset object metadata for key " + storageKey, ex);
        }
    }

    @Override
    public SignedStorageUrl signedUploadUrl(String storageKey, String expectedMimeType, long expectedByteSize, Duration expiresIn) {
        Instant expiresAt = Instant.now().plus(properties.cappedUploadUrlTtl(expiresIn));
        return new SignedStorageUrl(localUri(storageKey, expiresAt), expiresAt, "PUT", storageKey);
    }

    @Override
    public SignedStorageUrl signedDownloadUrl(String storageKey, Duration expiresIn) {
        Instant expiresAt = Instant.now().plus(properties.cappedDownloadUrlTtl(expiresIn));
        return new SignedStorageUrl(localUri(storageKey, expiresAt), expiresAt, "GET", storageKey);
    }

    @Override
    public void copy(String sourceStorageKey, String destinationStorageKey) {
        Path source = resolve(sourceStorageKey);
        Path destination = resolve(destinationStorageKey);
        try {
            if (!Files.isRegularFile(source)) {
                throw new AssetStorageException("Source object does not exist for key " + sourceStorageKey);
            }
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new AssetStorageException("Unable to copy local asset object " + sourceStorageKey, ex);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException ex) {
            throw new AssetStorageException("Unable to delete local asset object for key " + storageKey, ex);
        }
    }

    @Override
    public String quarantine(String storageKey, String reasonCode) {
        String destinationKey = properties.getNamespace() + "/" + properties.getQuarantinePrefix()
                + "/" + sanitize(reasonCode) + "/" + stripNamespace(storageKey);
        move(storageKey, destinationKey);
        return destinationKey;
    }

    public Path resolve(String storageKey) {
        Path root = properties.getLocalRoot().toAbsolutePath().normalize();
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new AssetStorageException("Storage key escapes configured local asset root");
        }
        return resolved;
    }

    private URI localUri(String storageKey, Instant expiresAt) {
        String query = "expires=" + expiresAt.getEpochSecond() + "&provider=local&bucketAlias=" + properties.bucketAlias();
        return URI.create("local-asset://cadentia/" + storageKey + "?" + query);
    }

    private String stripNamespace(String storageKey) {
        String namespace = properties.getNamespace() + "/";
        if (storageKey.startsWith(namespace)) {
            return storageKey.substring(namespace.length());
        }
        return storageKey;
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            try (InputStream inputStream = Files.newInputStream(path);
                    DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                digestInputStream.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new AssetStorageException("SHA-256 digest is unavailable in this JVM", ex);
        }
    }

    private static String sanitize(String reasonCode) {
        return reasonCode == null ? "unspecified" : reasonCode.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
