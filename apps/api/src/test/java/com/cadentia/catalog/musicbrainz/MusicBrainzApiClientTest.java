package com.cadentia.catalog.musicbrainz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.cadentia.catalog.musicbrainz.MusicBrainzModels.SearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class MusicBrainzApiClientTest {
    private static final UUID RECORDING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ARTIST_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID RELEASE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID RELEASE_GROUP_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void mapsRecordingSearchResultsAndSendsDescriptiveUserAgent() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MusicBrainzApiClient client = client(restTemplate, Duration.ZERO);
        server.expect(once(), requestTo("http://musicbrainz.local/ws/2/recording?query=recording%3A%22O%2BM%5C%22%22"
                        + "+AND+artist%3A%22The+Band%22&limit=5&inc=artist-credits+releases+release-groups+isrcs&fmt=json"))
                .andExpect(header("User-Agent", "Cadentia-admin-web/1.0 (admin@example.test)"))
                .andRespond(withSuccess("""
                        {
                          "recordings": [{
                            "id": "11111111-1111-1111-1111-111111111111",
                            "title": "O+M\\\"",
                            "length": 215000,
                            "score": 98,
                            "artist-credit": [{"name": "The Band", "artist": {
                              "id": "22222222-2222-2222-2222-222222222222",
                              "name": "The Band", "sort-name": "Band, The", "type": "Group", "country": "US"
                            }}],
                            "isrcs": ["US-AAA-00-00001"],
                            "releases": [{
                              "id": "33333333-3333-3333-3333-333333333333",
                              "title": "The Album", "status": " official", "date": "2020-01-02", "country": "US",
                              "release-group": {"id": "44444444-4444-4444-4444-444444444444", "title": "The Album", "primary-type": "Album", "first-release-date": "2020-01-02"}
                            }]
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        MusicBrainzApiClient.SearchResponse result = client.search(
                new SearchRequest("O+M\"", "The Band", null, null, 5));

        assertThat(result.enabled()).isTrue();
        assertThat(result.cacheHit()).isFalse();
        assertThat(result.candidates()).hasSize(1);
        MusicBrainzApiClient.RemoteCandidate candidate = result.candidates().get(0);
        assertThat(candidate.recording().mbid()).isEqualTo(RECORDING_ID);
        assertThat(candidate.artist().name()).isEqualTo("The Band");
        assertThat(candidate.releaseGroup().title()).isEqualTo("The Album");
        assertThat(candidate.release().country()).isEqualTo("US");
        assertThat(candidate.recording().isrcs()).containsExactly("US-AAA-00-00001");
        server.verify();
    }

    @Test
    void cachesIdenticalSearchesByRequest() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MusicBrainzApiClient client = client(restTemplate, Duration.ofMinutes(1));
        server.expect(once(), requestTo("http://musicbrainz.local/ws/2/recording?query=recording%3A%22Song%22&limit=1&inc=artist-credits+releases+release-groups+isrcs&fmt=json"))
                .andRespond(withSuccess("{\"recordings\":[]}", MediaType.APPLICATION_JSON));
        SearchRequest request = new SearchRequest("Song", null, null, null, 1);

        assertThat(client.search(request).cacheHit()).isFalse();
        assertThat(client.search(request).cacheHit()).isTrue();
        server.verify();
    }

    @Test
    void mapsUpstreamRateLimitWithoutLeakingResponsePayload() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MusicBrainzApiClient client = client(restTemplate, Duration.ZERO);
        server.expect(once(), requestTo("http://musicbrainz.local/ws/2/recording?query=recording%3A%22Song%22&limit=1&inc=artist-credits+releases+release-groups+isrcs&fmt=json"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("secret upstream payload"));

        assertThatThrownBy(() -> client.search(new SearchRequest("Song", null, null, null, 1)))
                .isInstanceOfSatisfying(MusicBrainzClientException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(429);
                    assertThat(exception.getMessage()).doesNotContain("secret upstream payload");
                });
        server.verify();
    }

    private static MusicBrainzApiClient client(RestTemplate restTemplate, Duration cacheTtl) {
        return new MusicBrainzApiClient(restTemplate, new ObjectMapper(), true,
                "http://musicbrainz.local/ws/2", "Cadentia-admin-web/1.0 (admin@example.test)",
                Duration.ofSeconds(1), Duration.ZERO, cacheTtl);
    }
}
