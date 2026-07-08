package com.cadentia.search;

import com.cadentia.search.ApprovedSearchModels.ApprovedSearchDocument;
import com.cadentia.search.ApprovedSearchModels.RankingFactor;
import com.cadentia.search.ApprovedSearchModels.SearchActor;
import com.cadentia.search.ApprovedSearchModels.SearchProjectionEventReason;
import com.cadentia.search.ApprovedSearchModels.SearchProjectionInvalidation;
import com.cadentia.search.ApprovedSearchModels.SearchQuery;
import com.cadentia.search.ApprovedSearchModels.SearchResult;
import com.cadentia.search.ApprovedSearchModels.SemanticDiscoveryMode;
import com.cadentia.search.ApprovedSearchModels.SemanticEmbeddingRecord;
import com.cadentia.search.ApprovedSearchModels.SemanticIndexDocument;
import com.cadentia.search.ApprovedSearchModels.TagFacet;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public class SemanticDiscoveryService {

    public static final String DEFAULT_PROVIDER = "local-deterministic";
    public static final String DEFAULT_MODEL_VERSION = "metadata-token-v1";
    public static final int DEFAULT_PROJECTION_VERSION = 1;
    private static final Set<SearchProjectionEventReason> INVALIDATING_REASONS = Set.of(
            SearchProjectionEventReason.APPROVAL_STATE_CHANGED,
            SearchProjectionEventReason.ACTIVE_STATUS_CHANGED,
            SearchProjectionEventReason.LICENSING_CHANGED,
            SearchProjectionEventReason.PACKAGE_VISIBILITY_CHANGED,
            SearchProjectionEventReason.INSTANCE_VISIBILITY_CHANGED,
            SearchProjectionEventReason.SOURCE_METADATA_CHANGED,
            SearchProjectionEventReason.CATALOG_GOVERNANCE_CHANGED,
            SearchProjectionEventReason.REBUILD_REQUESTED);

    private final SearchEligibilityPolicy eligibilityPolicy;
    private final Clock clock;
    private final String providerIdentifier;
    private final String modelVersion;
    private final int projectionVersion;
    private final SemanticDiscoveryMode mode;
    private final Set<UUID> disabledInstances;

    public SemanticDiscoveryService() {
        this(new SearchEligibilityPolicy(), Clock.systemUTC(), DEFAULT_PROVIDER, DEFAULT_MODEL_VERSION,
                DEFAULT_PROJECTION_VERSION, SemanticDiscoveryMode.ENABLED, Set.of());
    }

    public SemanticDiscoveryService(
            SearchEligibilityPolicy eligibilityPolicy,
            Clock clock,
            String providerIdentifier,
            String modelVersion,
            int projectionVersion,
            SemanticDiscoveryMode mode,
            Set<UUID> disabledInstances) {
        this.eligibilityPolicy = eligibilityPolicy == null ? new SearchEligibilityPolicy() : eligibilityPolicy;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.providerIdentifier = providerIdentifier == null || providerIdentifier.isBlank() ? DEFAULT_PROVIDER : providerIdentifier;
        this.modelVersion = modelVersion == null || modelVersion.isBlank() ? DEFAULT_MODEL_VERSION : modelVersion;
        this.projectionVersion = projectionVersion;
        this.mode = mode == null ? SemanticDiscoveryMode.ENABLED : mode;
        this.disabledInstances = disabledInstances == null ? Set.of() : Set.copyOf(disabledInstances);
    }

    public SemanticIndexDocument sourceDocument(SearchActor actor, ApprovedSearchDocument document) {
        if (!eligibilityPolicy.canReturn(actor, document)) {
            throw new IllegalArgumentException("semanticSourceRequiresEligibleApprovedDocument");
        }
        List<String> fields = new ArrayList<>();
        add(fields, "title", document.title());
        document.alternateTitles().forEach(value -> add(fields, "alternateTitle", value));
        document.tags().stream().flatMap(tag -> Stream.of(tag.code(), tag.label())).forEach(value -> add(fields, "tag", value));
        document.scriptureReferences().stream().map(NormalizedScriptureReference::display).forEach(value -> add(fields, "scripture", value));
        document.contributors().forEach(value -> add(fields, "contributor", value));
        document.lyricsMetadata().forEach(value -> add(fields, "approvedLyricsFeature", value));
        return new SemanticIndexDocument(document.songId(), document.arrangementId(), document.instanceId(), document.packageVisible(),
                document.approved(), document.active(), document.visible(), document.licensed(), fields, hash(String.join("\n", fields)));
    }

    public SemanticEmbeddingRecord embeddingRecord(SearchActor actor, ApprovedSearchDocument document) {
        SemanticIndexDocument source = sourceDocument(actor, document);
        return new SemanticEmbeddingRecord(UUID.randomUUID(), source.songId(), source.arrangementId(), source.instanceId(), source.packageVisible(),
                providerIdentifier, modelVersion, source.sourceHash(), clock.instant(), projectionVersion,
                source.approved(), source.active(), source.visible(), source.licensed(), embed(String.join(" ", source.allowedFields())));
    }

    public List<SearchResult> search(
            SearchActor actor,
            SearchQuery query,
            List<ApprovedSearchDocument> documents,
            List<SemanticEmbeddingRecord> embeddings,
            int limit) {
        if (mode == SemanticDiscoveryMode.DISABLED || query == null || disabledInstances.contains(query.instanceId()) || isBlank(query.text()) || limit <= 0) {
            return List.of();
        }
        Map<UUID, ApprovedSearchDocument> eligibleDocuments = new LinkedHashMap<>();
        for (ApprovedSearchDocument document : documents == null ? List.<ApprovedSearchDocument>of() : documents) {
            if (eligibilityPolicy.canReturn(actor, document)) {
                eligibleDocuments.put(document.songId(), document);
            }
        }
        double[] queryVector = embed(query.text());
        return (embeddings == null ? List.<SemanticEmbeddingRecord>of() : embeddings).stream()
                .filter(record -> eligibilityMetadataStillEligible(record) && eligibleDocuments.containsKey(record.songId()))
                .filter(record -> providerIdentifier.equals(record.providerIdentifier()) && modelVersion.equals(record.modelVersion()))
                .map(record -> semanticResult(eligibleDocuments.get(record.songId()), cosine(queryVector, record.vector())))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed().thenComparing(SearchResult::title))
                .limit(limit)
                .toList();
    }

    public boolean invalidates(SearchProjectionEventReason reason) {
        return INVALIDATING_REASONS.contains(reason);
    }

    public SearchProjectionInvalidation invalidation(UUID sourceEntityId, SearchProjectionEventReason reason) {
        if (!invalidates(reason)) {
            throw new IllegalArgumentException("unsupportedSemanticInvalidationReason");
        }
        return new SearchProjectionInvalidation(sourceEntityId, reason, Instant.now(clock).plus(SearchProjectionInvalidationService.REFRESH_TARGET));
    }

    private SearchResult semanticResult(ApprovedSearchDocument document, double similarity) {
        double bounded = Math.max(0.0d, Math.min(1.0d, similarity));
        return new SearchResult(document.songId(), document.arrangementId(), document.title(), document.arrangementLabel(), bounded,
                List.of(new RankingFactor("semanticSimilarity", bounded, "publicSafe")), "semantic-discovery-" + modelVersion);
    }

    private boolean eligibilityMetadataStillEligible(SemanticEmbeddingRecord record) {
        return record.approved() && record.active() && record.visible() && record.licensed() && record.packageVisible();
    }

    private static void add(List<String> fields, String name, String value) {
        String normalized = SearchNormalizer.normalizeText(value);
        if (!normalized.isBlank()) {
            fields.add(name + ": " + normalized);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static double[] embed(String text) {
        double[] vector = new double[32];
        for (String token : SearchNormalizer.tokens(text)) {
            int bucket = Math.floorMod(token.hashCode(), vector.length);
            vector[bucket] += 1.0d;
        }
        return vector;
    }

    private static double cosine(double[] left, double[] right) {
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        return leftNorm == 0 || rightNorm == 0 ? 0 : dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
