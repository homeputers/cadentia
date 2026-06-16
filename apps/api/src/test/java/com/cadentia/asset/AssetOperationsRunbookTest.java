package com.cadentia.asset;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.asset.AssetModels.AssetAccessPolicyCode;
import com.cadentia.asset.AssetModels.AssetLicenseStatusCode;
import com.cadentia.asset.AssetModels.AssetTypeCode;
import com.cadentia.asset.AssetModels.LicenseMetadata;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class AssetOperationsRunbookTest {

    private static final String RUNBOOK = "docs/runbooks/adr-025-media-and-asset-management-operations.md";
    private static final String ADR = "docs/adr/ADR-025-media-and-asset-management.md";
    private static final String FIXTURE_MANIFEST = "apps/api/src/test/resources/assets/fixtures/README.md";
    private static final String FIXTURE_SQL = "apps/api/src/test/resources/db/fixtures/asset_domain_fixture.sql";

    @Test
    void runbookDocumentsRequiredOperationalWorkflowsAndTelemetryNames() throws IOException {
        // Arrange
        String runbook = readFile(RUNBOOK);

        // Act / Assert
        assertThat(runbook)
                .contains("## Configuring S3-compatible storage")
                .contains("CADENTIA_ASSET_STORAGE_PROVIDER=s3")
                .contains("generic S3-compatible `AssetStorageAdapter`")
                .contains("CADENTIA_ASSET_STORAGE_REGION")
                .contains("CADENTIA_ASSET_STORAGE_ENDPOINT")
                .contains("CADENTIA_ASSET_STORAGE_PATH_STYLE_ACCESS_ENABLED")
                .contains("Block public access")
                .contains("workload identity credentials")
                .contains("## Upload lifecycle")
                .contains("## Signed access troubleshooting and incident response")
                .contains("## Quarantine workflow")
                .contains("## Processing retry and dead-letter triage")
                .contains("## Lifecycle cleanup and retention")
                .contains("## Backup and restore")
                .contains("## Instance deletion handling")
                .contains("## Licensing expiration review")
                .contains("## Emergency access revocation")
                .contains("cadentia_asset_upload_attempts_total")
                .contains("cadentia_asset_upload_failures_total")
                .contains("cadentia_asset_versions_finalized_total")
                .contains("cadentia_asset_signed_access_decisions_total")
                .contains("cadentia_asset_signed_access_denied_total")
                .contains("cadentia_asset_processing_latency_seconds")
                .contains("cadentia_asset_processor_failures_total")
                .contains("cadentia_asset_quarantine_events_total")
                .contains("cadentia_asset_cleanup_deletions_total")
                .contains("cadentia_asset_license_expiration_warnings_total")
                .contains("asset.restore.manifest_verified")
                .contains("ASSET_EMERGENCY_ACCESS_REVOKED");
    }

    @Test
    void productionDefaultsResolveAdrProviderRetentionAndLicensingQuestions() throws IOException {
        // Arrange
        String adr = readFile(ADR);
        String runbook = readFile(RUNBOOK);

        // Act / Assert
        assertThat(adr)
                .doesNotContain("## Open Questions")
                .contains("Production baseline storage is S3-compatible private object storage")
                .contains("7 years")
                .contains("30-day deletion hold")
                .contains("Mandatory licensing metadata");
        assertThat(runbook)
                .contains("**Production storage baseline:** S3-compatible object storage")
                .contains("**Service-completion retention:** assets pinned to service or rehearsal")
                .contains("**Instance deletion retention:** instance deletion first revokes access")
                .contains("**Mandatory licensing fields:** every downloadable or streamable asset version");
    }

    @Test
    void fixturePayloadManifestContainsCopyrightSafeProvenanceAndVerifiedChecksums() throws Exception {
        // Arrange
        String manifest = readFile(FIXTURE_MANIFEST);

        // Act / Assert
        assertThat(manifest)
                .contains("Binary fixture payloads are intentionally **not** committed")
                .contains("no copyrighted worship charts")
                .contains("placeholder-chart.pdf")
                .contains("placeholder-click.wav")
                .contains("placeholder-cue.mid")
                .contains("real church media");
        assertFixtureChecksum(generatedPlaceholderChart(), "3acc349a16e909272af46360ce001585efbab65f8351bb27fc02551a9f8259a8");
        assertFixtureChecksum(generatedPlaceholderClick(), "30f3af8781bbd968fc9a7acb387b0fcfa06751e23229c988417f835423505ffb");
        assertFixtureChecksum(generatedPlaceholderCue(), "5112aeab8d22a7c3cc42f998cbffe1c6a497ca30cfa300a49fa1e37c1e046682");
    }

    @Test
    void expiredLicenseWarningSelectionUsesLowCardinalityMetadata() {
        // Arrange
        Instant now = Instant.parse("2026-06-16T00:00:00Z");
        LicenseMetadata expiringLicense = new LicenseMetadata(
                AssetLicenseStatusCode.EXPIRES,
                "Fixture source",
                "FIXTURE-42",
                "Do not emit this field as a metric label",
                "Cadentia Test Church",
                now.minusSeconds(86_400),
                now.plusSeconds(7 * 86_400),
                AssetAccessPolicyCode.WORSHIP_TEAM);

        // Act
        LicenseExpirationWarning warning = warningFor(AssetTypeCode.BACKING_TRACK, expiringLicense, now);

        // Assert
        assertThat(warning).isNotNull();
        assertThat(warning.windowDays()).isEqualTo(7);
        assertThat(warning.assetTypeCode()).isEqualTo(AssetTypeCode.BACKING_TRACK);
        assertThat(warning.metricName()).isEqualTo("cadentia_asset_license_expiration_warnings_total");
        assertThat(warning.labelValues()).containsExactly("7", "BACKING_TRACK");
        assertThat(warning.labelValues()).doesNotContain(expiringLicense.usageRestrictions());
    }

    @Test
    void backupRestoreMetadataFixtureMatchesObjectManifestChecksumAndSize() throws IOException {
        // Arrange
        String fixtureSql = readFile(FIXTURE_SQL);
        String manifest = readFile(FIXTURE_MANIFEST);

        // Act / Assert
        assertThat(fixtureSql)
                .contains("fixtures/assets/chord-chart-rev-a.pdf")
                .contains("3acc349a16e909272af46360ce001585efbab65f8351bb27fc02551a9f8259a8")
                .contains("317")
                .contains("application/pdf");
        assertThat(manifest)
                .contains("placeholder-chart.pdf")
                .contains("3acc349a16e909272af46360ce001585efbab65f8351bb27fc02551a9f8259a8");
    }

    @Test
    void runbookCommandExamplesReferenceExecutableRegressionTests() throws IOException {
        // Arrange
        String runbook = readFile(RUNBOOK);

        // Act / Assert
        assertThat(runbook)
                .contains("mvn -pl apps/api test -Dtest=AssetOperationsRunbookTest")
                .contains("mvn -pl apps/api test -Dtest=AssetUploadServiceTest,AssetAccessServiceTest");
    }

    private LicenseExpirationWarning warningFor(
            AssetTypeCode assetTypeCode,
            LicenseMetadata licenseMetadata,
            Instant now) {
        long secondsUntilExpiration = licenseMetadata.expiresAt().getEpochSecond() - now.getEpochSecond();
        long daysUntilExpiration = secondsUntilExpiration / 86_400;
        if (daysUntilExpiration == 60 || daysUntilExpiration == 30
                || daysUntilExpiration == 14 || daysUntilExpiration == 7) {
            return new LicenseExpirationWarning(
                    "cadentia_asset_license_expiration_warnings_total",
                    daysUntilExpiration,
                    assetTypeCode);
        }
        return null;
    }

    private void assertFixtureChecksum(byte[] fixtureBytes, String expectedChecksum)
            throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        assertThat(HexFormat.of().formatHex(digest.digest(fixtureBytes)))
                .isEqualTo(expectedChecksum);
    }

    private byte[] generatedPlaceholderChart() {
        return ("%PDF-1.4\n"
                + "1 0 obj<< /Type /Catalog /Pages 2 0 R>>endobj\n"
                + "2 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1>>endobj\n"
                + "3 0 obj<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 80] /Contents 4 0 R>>endobj\n"
                + "4 0 obj<< /Length 44 >>stream\n"
                + "BT /F1 12 Tf 10 40 Td (Cadentia fixture) Tj ET\n"
                + "endstream\n"
                + "endobj\n"
                + "trailer<< /Root 1 0 R >>\n"
                + "%%EOF\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private byte[] generatedPlaceholderClick() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int sampleRate = 8_000;
        int sampleCount = 800;
        int dataLength = sampleCount * Short.BYTES;
        writeAscii(output, "RIFF");
        writeLittleEndianInt(output, 36 + dataLength);
        writeAscii(output, "WAVEfmt ");
        writeLittleEndianInt(output, 16);
        writeLittleEndianShort(output, 1);
        writeLittleEndianShort(output, 1);
        writeLittleEndianInt(output, sampleRate);
        writeLittleEndianInt(output, sampleRate * Short.BYTES);
        writeLittleEndianShort(output, Short.BYTES);
        writeLittleEndianShort(output, 16);
        writeAscii(output, "data");
        writeLittleEndianInt(output, dataLength);
        for (int i = 0; i < sampleCount; i++) {
            short value = (short) (12_000 * Math.sin(2 * Math.PI * 880 * i / sampleRate));
            writeLittleEndianShort(output, value);
        }
        return output.toByteArray();
    }

    private byte[] generatedPlaceholderCue() {
        return HexFormat.of().parseHex("4d546864000000060000000100604d54726b0000000c00903c400060803c4000ff2f00");
    }

    private void writeAscii(ByteArrayOutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private void writeLittleEndianInt(ByteArrayOutputStream output, int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }

    private void writeLittleEndianShort(ByteArrayOutputStream output, int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
    }

    private String readFile(String path) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            Path resolved = candidate.resolve(path);
            if (Files.exists(resolved)) {
                return Files.readString(resolved, StandardCharsets.UTF_8);
            }
        }
        throw new NoSuchFileException(path);
    }

    private record LicenseExpirationWarning(
            String metricName,
            long windowDays,
            AssetTypeCode assetTypeCode) {

        String[] labelValues() {
            return new String[] {String.valueOf(windowDays), assetTypeCode.name()};
        }
    }
}
