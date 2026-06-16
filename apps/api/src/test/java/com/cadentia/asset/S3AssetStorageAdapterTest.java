package com.cadentia.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3AssetStorageAdapterTest {

    private static final String STORAGE_KEY = "church-a/processing/upload-1";
    private static final String DIGEST_HEX = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    void readsObjectMetadataAndConvertsS3ChecksumToSha256HexDigest() {
        // Arrange
        RecordingS3Client recordingClient = new RecordingS3Client();
        recordingClient.headObjectResponse = HeadObjectResponse.builder()
                .contentLength(317L)
                .contentType("application/pdf")
                .lastModified(Instant.parse("2026-06-16T12:00:00Z"))
                .checksumSHA256(Base64.getEncoder().encodeToString(hexBytes(DIGEST_HEX)))
                .eTag("\"etag-value\"")
                .build();
        S3AssetStorageAdapter adapter = newAdapter(recordingClient);

        // Act
        Optional<StoredObjectMetadata> metadata = adapter.metadata(STORAGE_KEY);

        // Assert
        assertThat(metadata).isPresent();
        assertThat(metadata.orElseThrow().storageKey()).isEqualTo(STORAGE_KEY);
        assertThat(metadata.orElseThrow().byteSize()).isEqualTo(317L);
        assertThat(metadata.orElseThrow().mimeType()).isEqualTo("application/pdf");
        assertThat(metadata.orElseThrow().digest("SHA-256")).contains(DIGEST_HEX);
        assertThat(metadata.orElseThrow().digest("S3-ETAG")).contains("etag-value");
        assertThat(recordingClient.headObjectRequests).hasSize(1);
        assertThat(recordingClient.headObjectRequests.getFirst().bucket()).isEqualTo("cadentia-assets");
        assertThat(recordingClient.headObjectRequests.getFirst().key()).isEqualTo(STORAGE_KEY);
    }

    @Test
    void returnsEmptyMetadataWhenObjectDoesNotExist() {
        // Arrange
        RecordingS3Client recordingClient = new RecordingS3Client();
        recordingClient.headObjectException = NoSuchKeyException.builder().message("missing").build();
        S3AssetStorageAdapter adapter = newAdapter(recordingClient);

        // Act / Assert
        assertThat(adapter.metadata(STORAGE_KEY)).isEmpty();
        assertThat(adapter.exists(STORAGE_KEY)).isFalse();
        assertThat(recordingClient.headObjectRequests).hasSize(2);
    }

    @Test
    void copiesDeletesAndQuarantinesWithinConfiguredBucketAndNamespace() {
        // Arrange
        RecordingS3Client recordingClient = new RecordingS3Client();
        S3AssetStorageAdapter adapter = newAdapter(recordingClient);

        // Act
        adapter.copy(STORAGE_KEY, "church-a/assets/object-1");
        adapter.delete(STORAGE_KEY);
        String quarantineKey = adapter.quarantine(STORAGE_KEY, "scan failed");

        // Assert
        assertThat(recordingClient.copyObjectRequests).hasSize(2);
        assertThat(recordingClient.copyObjectRequests.getFirst().copySource())
                .isEqualTo("cadentia-assets/church-a/processing/upload-1");
        assertThat(recordingClient.copyObjectRequests.getFirst().destinationBucket()).isEqualTo("cadentia-assets");
        assertThat(recordingClient.copyObjectRequests.getFirst().destinationKey()).isEqualTo("church-a/assets/object-1");
        assertThat(recordingClient.copyObjectRequests.get(1).destinationKey())
                .isEqualTo("church-a/quarantine/scan_failed/processing/upload-1");
        assertThat(recordingClient.deleteObjectRequests).hasSize(2);
        assertThat(recordingClient.deleteObjectRequests)
                .extracting(DeleteObjectRequest::bucket)
                .containsOnly("cadentia-assets");
        assertThat(quarantineKey).isEqualTo("church-a/quarantine/scan_failed/processing/upload-1");
    }

    private S3AssetStorageAdapter newAdapter(RecordingS3Client recordingClient) {
        AssetStorageProperties properties = new AssetStorageProperties();
        properties.setProvider("s3");
        properties.setBucket("cadentia-assets");
        properties.setNamespace("church-a");
        properties.setEncryptionKeyRef("aws-kms:alias/cadentia-assets-prod");
        return new S3AssetStorageAdapter(properties, recordingClient.proxy(), presignerProxy());
    }

    private byte[] hexBytes(String value) {
        return java.util.HexFormat.of().parseHex(value);
    }

    private S3Presigner presignerProxy() {
        return (S3Presigner) Proxy.newProxyInstance(
                S3Presigner.class.getClassLoader(),
                new Class<?>[] {S3Presigner.class},
                (proxy, method, args) -> null);
    }

    private static final class RecordingS3Client implements InvocationHandler {

        private final List<HeadObjectRequest> headObjectRequests = new ArrayList<>();
        private final List<CopyObjectRequest> copyObjectRequests = new ArrayList<>();
        private final List<DeleteObjectRequest> deleteObjectRequests = new ArrayList<>();
        private HeadObjectResponse headObjectResponse = HeadObjectResponse.builder().build();
        private RuntimeException headObjectException;

        private S3Client proxy() {
            return (S3Client) Proxy.newProxyInstance(
                    S3Client.class.getClassLoader(),
                    new Class<?>[] {S3Client.class},
                    this);
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            return switch (method.getName()) {
                case "headObject" -> headObject((HeadObjectRequest) args[0]);
                case "copyObject" -> copyObject((CopyObjectRequest) args[0]);
                case "deleteObject" -> deleteObject((DeleteObjectRequest) args[0]);
                case "serviceName" -> "s3";
                case "close" -> null;
                default -> throw new UnsupportedOperationException("Unexpected S3Client call: " + method.getName());
            };
        }

        private HeadObjectResponse headObject(HeadObjectRequest request) {
            headObjectRequests.add(request);
            if (headObjectException != null) {
                throw headObjectException;
            }
            return headObjectResponse;
        }

        private Object copyObject(CopyObjectRequest request) {
            copyObjectRequests.add(request);
            return null;
        }

        private Object deleteObject(DeleteObjectRequest request) {
            deleteObjectRequests.add(request);
            return null;
        }
    }
}
