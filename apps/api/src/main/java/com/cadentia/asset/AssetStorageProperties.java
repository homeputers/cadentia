package com.cadentia.asset;

import com.cadentia.asset.AssetModels.AssetTypeCode;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cadentia.asset-storage")
public class AssetStorageProperties {

    private String provider = "local";
    private String bucket = "cadentia-local-assets";
    private String namespace = "local-development";
    private String encryptionKeyRef = "env:CADENTIA_LOCAL_ASSET_KEY_REF";
    private Path localRoot = Path.of(".cadentia", "asset-storage");
    private Duration signedUploadUrlTtl = Duration.ofMinutes(15);
    private Duration signedDownloadUrlTtl = Duration.ofMinutes(10);
    private Duration maximumSignedUrlTtl = Duration.ofHours(1);
    private Duration pendingUploadTtl = Duration.ofHours(2);
    private long maximumObjectSizeBytes = 250L * 1024L * 1024L;
    private String processingPrefix = "processing";
    private String quarantinePrefix = "quarantine";
    private String availablePrefix = "assets";
    private Map<AssetTypeCode, List<String>> allowedMimeTypes = defaultAllowedMimeTypes();

    public boolean isAllowedMimeType(AssetTypeCode assetTypeCode, String mimeType) {
        return allowedMimeTypes.getOrDefault(assetTypeCode, List.of()).contains(mimeType);
    }

    public String bucketAlias() {
        return provider + ":" + namespace;
    }

    public Duration cappedUploadUrlTtl(Duration requested) {
        return cap(requested == null ? signedUploadUrlTtl : requested);
    }

    public Duration cappedDownloadUrlTtl(Duration requested) {
        return cap(requested == null ? signedDownloadUrlTtl : requested);
    }

    private Duration cap(Duration requested) {
        if (requested.compareTo(maximumSignedUrlTtl) > 0) {
            return maximumSignedUrlTtl;
        }
        return requested;
    }

    private static Map<AssetTypeCode, List<String>> defaultAllowedMimeTypes() {
        EnumMap<AssetTypeCode, List<String>> defaults = new EnumMap<>(AssetTypeCode.class);
        defaults.put(AssetTypeCode.PDF, List.of("application/pdf"));
        defaults.put(AssetTypeCode.CHORD_CHART, List.of("application/pdf", "text/plain", "text/markdown"));
        defaults.put(AssetTypeCode.STEM, List.of("audio/mpeg", "audio/wav", "audio/x-wav", "audio/flac"));
        defaults.put(AssetTypeCode.BACKING_TRACK, List.of("audio/mpeg", "audio/wav", "audio/x-wav", "audio/flac"));
        defaults.put(AssetTypeCode.CLICK_TRACK, List.of("audio/mpeg", "audio/wav", "audio/x-wav", "audio/flac"));
        defaults.put(AssetTypeCode.MIDI_CUE, List.of("audio/midi", "audio/x-midi"));
        defaults.put(AssetTypeCode.REHEARSAL_RECORDING, List.of("audio/mpeg", "audio/wav", "audio/x-wav", "video/mp4"));
        defaults.put(AssetTypeCode.PREVIEW, List.of("image/png", "image/jpeg", "audio/mpeg", "application/pdf"));
        defaults.put(AssetTypeCode.LOCAL_EXTENSION, List.of("application/octet-stream"));
        return defaults;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getEncryptionKeyRef() {
        return encryptionKeyRef;
    }

    public void setEncryptionKeyRef(String encryptionKeyRef) {
        this.encryptionKeyRef = encryptionKeyRef;
    }

    public Path getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(Path localRoot) {
        this.localRoot = localRoot;
    }

    public Duration getSignedUploadUrlTtl() {
        return signedUploadUrlTtl;
    }

    public void setSignedUploadUrlTtl(Duration signedUploadUrlTtl) {
        this.signedUploadUrlTtl = signedUploadUrlTtl;
    }

    public Duration getSignedDownloadUrlTtl() {
        return signedDownloadUrlTtl;
    }

    public void setSignedDownloadUrlTtl(Duration signedDownloadUrlTtl) {
        this.signedDownloadUrlTtl = signedDownloadUrlTtl;
    }

    public Duration getMaximumSignedUrlTtl() {
        return maximumSignedUrlTtl;
    }

    public void setMaximumSignedUrlTtl(Duration maximumSignedUrlTtl) {
        this.maximumSignedUrlTtl = maximumSignedUrlTtl;
    }

    public Duration getPendingUploadTtl() {
        return pendingUploadTtl;
    }

    public void setPendingUploadTtl(Duration pendingUploadTtl) {
        this.pendingUploadTtl = pendingUploadTtl;
    }

    public long getMaximumObjectSizeBytes() {
        return maximumObjectSizeBytes;
    }

    public void setMaximumObjectSizeBytes(long maximumObjectSizeBytes) {
        this.maximumObjectSizeBytes = maximumObjectSizeBytes;
    }

    public String getProcessingPrefix() {
        return processingPrefix;
    }

    public void setProcessingPrefix(String processingPrefix) {
        this.processingPrefix = processingPrefix;
    }

    public String getQuarantinePrefix() {
        return quarantinePrefix;
    }

    public void setQuarantinePrefix(String quarantinePrefix) {
        this.quarantinePrefix = quarantinePrefix;
    }

    public String getAvailablePrefix() {
        return availablePrefix;
    }

    public void setAvailablePrefix(String availablePrefix) {
        this.availablePrefix = availablePrefix;
    }

    public Map<AssetTypeCode, List<String>> getAllowedMimeTypes() {
        return allowedMimeTypes;
    }

    public void setAllowedMimeTypes(Map<AssetTypeCode, List<String>> allowedMimeTypes) {
        this.allowedMimeTypes = new EnumMap<>(allowedMimeTypes);
    }
}
