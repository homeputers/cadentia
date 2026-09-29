package com.cadentia.catalog.musicbrainz;

import com.cadentia.catalog.musicbrainz.MusicBrainzModels.Artist;
import com.cadentia.catalog.musicbrainz.MusicBrainzModels.Recording;
import com.cadentia.catalog.musicbrainz.MusicBrainzModels.Release;
import com.cadentia.catalog.musicbrainz.MusicBrainzModels.ReleaseGroup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
public class MusicBrainzApiClient {
    private static final String DEFAULT_BASE_URL = "https://musicbrainz.org/ws/2";
    private static final String DEFAULT_USER_AGENT = "Cadentia/0.1.0 (https://github.com/cadentia/cadentia)";
    private static final String INCLUDES = "artist-credits+releases+release-groups+isrcs";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String baseUrl;
    private final String userAgent;
    private final Duration minimumRequestInterval;
    private final Duration cacheTtl;
    private final Map<String, CachedResponse> cache = new ConcurrentHashMap<>();
    private long lastRequestAtMillis;

    @Autowired
    public MusicBrainzApiClient(
            ObjectMapper objectMapper,
            @Value("${cadentia.musicbrainz.enabled:false}") boolean enabled,
            @Value("${cadentia.musicbrainz.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl,
            @Value("${cadentia.musicbrainz.user-agent:" + DEFAULT_USER_AGENT + "}") String userAgent,
            @Value("${cadentia.musicbrainz.timeout:PT10S}") Duration timeout,
            @Value("${cadentia.musicbrainz.minimum-request-interval:PT1S}") Duration minimumRequestInterval,
            @Value("${cadentia.musicbrainz.cache-ttl:PT24H}") Duration cacheTtl) {
        this(createRestTemplate(timeout), objectMapper, enabled, baseUrl, userAgent, timeout,
                minimumRequestInterval, cacheTtl);
    }

    private static RestTemplate createRestTemplate(Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = Math.toIntExact(timeout.toMillis());
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        return new RestTemplate(requestFactory);
    }

    MusicBrainzApiClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            boolean enabled,
            String baseUrl,
            String userAgent,
            Duration timeout,
            Duration minimumRequestInterval,
            Duration cacheTtl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.userAgent = userAgent;
        this.minimumRequestInterval = minimumRequestInterval;
        this.cacheTtl = cacheTtl;
    }

    public boolean enabled() {
        return enabled;
    }

    public SearchResponse search(MusicBrainzModels.SearchRequest request) {
        if (!enabled) {
            return new SearchResponse(false, true, List.of());
        }
        String cacheKey = cacheKey(request);
        CachedResponse cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return new SearchResponse(true, true, cached.candidates());
        }
        JsonNode payload = request.recordingMbid() == null
                ? get("/recording?query=" + query(request) + "&limit=" + request.limit()
                        + "&inc=" + INCLUDES + "&fmt=json")
                : get("/recording/" + request.recordingMbid() + "?inc=" + INCLUDES + "&fmt=json");
        List<RemoteCandidate> candidates = request.recordingMbid() == null
                ? parseSearch(payload)
                : List.of(parseCandidate(payload, BigDecimal.ONE));
        cache.put(cacheKey, new CachedResponse(Instant.now().plus(cacheTtl), candidates));
        return new SearchResponse(true, false, candidates);
    }

    private JsonNode get(String path) {
        awaitRateLimit();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, userAgent);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            ResponseEntity<String> response = restTemplate.exchange(
                    URI.create(baseUrl + path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
        } catch (HttpStatusCodeException exception) {
            throw new MusicBrainzClientException(
                    exception.getStatusCode().value(), "MusicBrainz request failed.", exception);
        } catch (Exception exception) {
            throw new MusicBrainzClientException(503, "MusicBrainz request failed.", exception);
        }
    }

    private synchronized void awaitRateLimit() {
        long waitMillis = minimumRequestInterval.toMillis() - (System.currentTimeMillis() - lastRequestAtMillis);
        if (waitMillis > 0) {
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new MusicBrainzClientException(503, "MusicBrainz request was interrupted.", exception);
            }
        }
        lastRequestAtMillis = System.currentTimeMillis();
    }

    private List<RemoteCandidate> parseSearch(JsonNode payload) {
        List<RemoteCandidate> candidates = new ArrayList<>();
        for (JsonNode recording : payload.path("recordings")) {
            candidates.add(parseCandidate(recording, decimal(recording.path("score").asDouble(0) / 100.0)));
        }
        return candidates;
    }

    private RemoteCandidate parseCandidate(JsonNode recording, BigDecimal score) {
        UUID recordingMbid = uuid(recording.path("id").asText(null));
        if (recordingMbid == null) {
            throw new MusicBrainzClientException(502, "MusicBrainz recording response omitted an MBID.");
        }
        Artist artist = firstArtist(recording.path("artist-credit"));
        JsonNode release = recording.path("releases").isArray() && recording.path("releases").size() > 0
                ? recording.path("releases").get(0) : null;
        Release releaseValue = parseRelease(release);
        ReleaseGroup releaseGroup = parseReleaseGroup(release == null ? null : release.path("release-group"));
        TrackPosition position = findTrackPosition(release, recordingMbid);
        Recording recordingValue = new Recording(
                recordingMbid,
                text(recording, "title", "Unknown recording"),
                integer(recording, "length"),
                position.discNumber(),
                position.trackNumber(),
                strings(recording.path("isrcs")),
                sourceUri("recording", recordingMbid));
        List<String> sourceReferences = new ArrayList<>();
        sourceReferences.add(recordingValue.sourceUri());
        if (releaseValue != null) sourceReferences.add(sourceUri("release", releaseValue.mbid()));
        if (releaseGroup != null) sourceReferences.add(sourceUri("release-group", releaseGroup.mbid()));
        if (artist != null) sourceReferences.add(sourceUri("artist", artist.mbid()));
        return new RemoteCandidate(
                score,
                artist,
                releaseGroup,
                releaseValue,
                recordingValue,
                List.of(),
                sourceReferences,
                recording.toString());
    }

    private Artist firstArtist(JsonNode credits) {
        if (!credits.isArray() || credits.isEmpty()) return null;
        JsonNode artist = credits.get(0).path("artist");
        UUID mbid = uuid(artist.path("id").asText(null));
        return mbid == null ? null : new Artist(
                mbid,
                text(artist, "name", "Unknown artist"),
                nullableText(artist, "sort-name"),
                nullableText(artist, "type"),
                nullableText(artist, "country"));
    }

    private Release parseRelease(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        UUID mbid = uuid(node.path("id").asText(null));
        return mbid == null ? null : new Release(
                mbid,
                text(node, "title", "Unknown release"),
                nullableText(node, "status"),
                nullableText(node, "date"),
                nullableText(node, "country"));
    }

    private ReleaseGroup parseReleaseGroup(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        UUID mbid = uuid(node.path("id").asText(null));
        return mbid == null ? null : new ReleaseGroup(
                mbid,
                text(node, "title", "Unknown release group"),
                nullableText(node, "primary-type"),
                nullableText(node, "first-release-date"));
    }

    private TrackPosition findTrackPosition(JsonNode release, UUID recordingMbid) {
        if (release == null) return new TrackPosition(null, null);
        for (JsonNode medium : release.path("media")) {
            for (JsonNode track : medium.path("tracks")) {
                if (recordingMbid.toString().equals(track.path("recording").path("id").asText())) {
                    return new TrackPosition(integer(track, "position"), integer(medium, "position"));
                }
            }
        }
        return new TrackPosition(null, null);
    }

    private String query(MusicBrainzModels.SearchRequest request) {
        StringBuilder value = new StringBuilder("recording:\"").append(escape(request.title())).append("\"");
        if (hasText(request.artist())) value.append(" AND artist:\"").append(escape(request.artist())).append("\"");
        if (hasText(request.album())) value.append(" AND release:\"").append(escape(request.album())).append("\"");
        return URLEncoder.encode(value.toString(), StandardCharsets.UTF_8);
    }

    private String cacheKey(MusicBrainzModels.SearchRequest request) {
        return String.join("|", normalize(request.title()), normalize(request.artist()), normalize(request.album()),
                request.recordingMbid() == null ? "" : request.recordingMbid().toString(), String.valueOf(request.limit()));
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Integer integer(JsonNode node, String field) {
        return node.hasNonNull(field) && node.path(field).canConvertToInt() ? node.path(field).asInt() : null;
    }

    private static String text(JsonNode node, String field, String fallback) {
        return node.hasNonNull(field) && !node.path(field).asText().isBlank() ? node.path(field).asText() : fallback;
    }

    private static String nullableText(JsonNode node, String field) {
        return node.hasNonNull(field) && !node.path(field).asText().isBlank() ? node.path(field).asText() : null;
    }

    private static List<String> strings(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }

    private static UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(Math.max(0, Math.min(1, value))).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private static String sourceUri(String type, UUID mbid) {
        return "https://musicbrainz.org/" + type + "/" + mbid;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return DEFAULT_BASE_URL;
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record SearchResponse(boolean enabled, boolean cacheHit, List<RemoteCandidate> candidates) {
    }

    public record RemoteCandidate(
            BigDecimal score,
            Artist artist,
            ReleaseGroup releaseGroup,
            Release release,
            Recording recording,
            List<String> warnings,
            List<String> sourceReferences,
            String rawJson) {
    }

    private record CachedResponse(Instant expiresAt, List<RemoteCandidate> candidates) {
    }

    private record TrackPosition(Integer trackNumber, Integer discNumber) {
    }
}
