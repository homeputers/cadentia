package com.cadentia.asset;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
@ConditionalOnProperty(prefix = "cadentia.asset-storage", name = "provider", havingValue = "s3")
public class S3AssetStorageAdapter implements AssetStorageAdapter {

    private static final String SHA_256 = "SHA-256";
    private static final String S3_ETAG = "S3-ETAG";

    private final AssetStorageProperties properties;
    private final S3Client s3Client;
    private final S3Presigner presigner;

    public S3AssetStorageAdapter(AssetStorageProperties properties) {
        this(properties, createClient(properties), createPresigner(properties));
    }

    S3AssetStorageAdapter(AssetStorageProperties properties, S3Client s3Client, S3Presigner presigner) {
        this.properties = properties;
        this.s3Client = s3Client;
        this.presigner = presigner;
    }

    @Override
    public String providerCode() {
        return "S3";
    }

    @Override
    public boolean exists(String storageKey) {
        return metadata(storageKey).isPresent();
    }

    @Override
    public Optional<StoredObjectMetadata> metadata(String storageKey) {
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .build());
            return Optional.of(toMetadata(storageKey, response));
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    @Override
    public SignedStorageUrl signedUploadUrl(
            String storageKey,
            String expectedMimeType,
            long expectedByteSize,
            Duration expiresIn) {
        Duration ttl = properties.cappedUploadUrlTtl(expiresIn);
        PutObjectRequest.Builder putObjectRequestBuilder = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(storageKey)
                .contentType(expectedMimeType)
                .contentLength(expectedByteSize);
        applyEncryption(putObjectRequestBuilder, properties.getEncryptionKeyRef());
        PutObjectRequest putObjectRequest = putObjectRequestBuilder.build();
        PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(putObjectRequest)
                .build());
        return new SignedStorageUrl(URI.create(presignedRequest.url().toString()), presignedRequest.expiration(), "PUT", storageKey);
    }

    @Override
    public SignedStorageUrl signedDownloadUrl(String storageKey, Duration expiresIn) {
        Duration ttl = properties.cappedDownloadUrlTtl(expiresIn);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(storageKey)
                .build();
        PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getObjectRequest)
                .build());
        return new SignedStorageUrl(URI.create(presignedRequest.url().toString()), presignedRequest.expiration(), "GET", storageKey);
    }

    @Override
    public void copy(String sourceStorageKey, String destinationStorageKey) {
        CopyObjectRequest.Builder copyObjectRequestBuilder = CopyObjectRequest.builder()
                .destinationBucket(properties.getBucket())
                .destinationKey(destinationStorageKey)
                .copySource(encodedCopySource(sourceStorageKey));
        applyEncryption(copyObjectRequestBuilder, properties.getEncryptionKeyRef());
        s3Client.copyObject(copyObjectRequestBuilder.build());
    }

    @Override
    public void delete(String storageKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(storageKey)
                .build());
    }

    @Override
    public String quarantine(String storageKey, String reasonCode) {
        String destinationKey = properties.getNamespace() + "/" + properties.getQuarantinePrefix()
                + "/" + sanitize(reasonCode) + "/" + stripNamespace(storageKey);
        move(storageKey, destinationKey);
        return destinationKey;
    }

    private StoredObjectMetadata toMetadata(String storageKey, HeadObjectResponse response) {
        Map<String, String> digests = new HashMap<>();
        if (response.checksumSHA256() != null && !response.checksumSHA256().isBlank()) {
            digests.put(SHA_256, hexDigest(response.checksumSHA256()));
        }
        if (response.eTag() != null && !response.eTag().isBlank()) {
            digests.put(S3_ETAG, response.eTag().replace("\"", ""));
        }
        return new StoredObjectMetadata(
                storageKey,
                response.contentLength(),
                response.contentType() == null ? "application/octet-stream" : response.contentType(),
                response.lastModified() == null ? Instant.EPOCH : response.lastModified(),
                digests);
    }

    private String stripNamespace(String storageKey) {
        String namespace = properties.getNamespace() + "/";
        if (storageKey.startsWith(namespace)) {
            return storageKey.substring(namespace.length());
        }
        return storageKey;
    }

    private String encodedCopySource(String sourceStorageKey) {
        return properties.getBucket() + "/" + URLEncoder.encode(sourceStorageKey, StandardCharsets.UTF_8)
                .replace("+", "%20").replace("%2F", "/");
    }

    private static S3Client createClient(AssetStorageProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccessEnabled())
                        .build());
        endpoint(properties).ifPresent(builder::endpointOverride);
        return builder.build();
    }

    private static S3Presigner createPresigner(AssetStorageProperties properties) {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccessEnabled())
                        .build());
        endpoint(properties).ifPresent(builder::endpointOverride);
        return builder.build();
    }

    private static Optional<URI> endpoint(AssetStorageProperties properties) {
        String endpoint = properties.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(URI.create(endpoint));
    }

    private static String hexDigest(String base64Digest) {
        return java.util.HexFormat.of().formatHex(Base64.getDecoder().decode(base64Digest));
    }

    private static void applyEncryption(PutObjectRequest.Builder builder, String encryptionKeyRef) {
        String normalizedKeyRef = normalizeKeyRef(encryptionKeyRef);
        if (normalizedKeyRef == null) {
            return;
        }
        if ("AES256".equals(normalizedKeyRef)) {
            builder.serverSideEncryption(ServerSideEncryption.AES256);
            return;
        }
        builder.serverSideEncryption(ServerSideEncryption.AWS_KMS).ssekmsKeyId(normalizedKeyRef);
    }

    private static void applyEncryption(CopyObjectRequest.Builder builder, String encryptionKeyRef) {
        String normalizedKeyRef = normalizeKeyRef(encryptionKeyRef);
        if (normalizedKeyRef == null) {
            return;
        }
        if ("AES256".equals(normalizedKeyRef)) {
            builder.serverSideEncryption(ServerSideEncryption.AES256);
            return;
        }
        builder.serverSideEncryption(ServerSideEncryption.AWS_KMS).ssekmsKeyId(normalizedKeyRef);
    }

    private static String normalizeKeyRef(String encryptionKeyRef) {
        if (encryptionKeyRef == null || encryptionKeyRef.isBlank() || encryptionKeyRef.startsWith("env:")) {
            return null;
        }
        if (encryptionKeyRef.startsWith("aws-kms:")) {
            return encryptionKeyRef.substring("aws-kms:".length());
        }
        if ("sse-s3".equalsIgnoreCase(encryptionKeyRef)) {
            return "AES256";
        }
        return encryptionKeyRef;
    }

    private static String sanitize(String reasonCode) {
        return reasonCode == null ? "unspecified" : reasonCode.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
