package com.cadentia.api.auth;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Component
@ConditionalOnProperty(name = "cadentia.auth.provider", havingValue = "first-party")
public class FirstPartyAuthClient {

    private static final String INTERNAL_API_KEY_HEADER = "X-Cadentia-Internal-Key";
    private static final Logger LOGGER = LoggerFactory.getLogger(FirstPartyAuthClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalApiKey;

    public FirstPartyAuthClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${cadentia.auth.service-base-url:http://localhost:8081}") String baseUrl,
            @Value("${cadentia.auth.internal-api-key:}") String internalApiKey) {
        this.restTemplate = restTemplateBuilder.build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.internalApiKey = internalApiKey;
    }

    public FirstPartyAuthUser findByEmail(String email) {
        if (internalApiKey == null || internalApiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "First-party auth service integration is not configured.");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_API_KEY_HEADER, internalApiKey);
        try {
            ResponseEntity<FirstPartyAuthUser> response = restTemplate.exchange(
                    baseUrl + "/internal/users/by-email?email={email}",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    FirstPartyAuthUser.class,
                    normalizedEmail);
            if (response.getBody() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The auth service returned an empty account response.");
            }
            return response.getBody();
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No active first-party account exists for that email.", exception);
        } catch (HttpClientErrorException.Forbidden exception) {
            LOGGER.error("First-party auth service rejected the internal account lookup with HTTP 403.");
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "The first-party auth service rejected the API credentials. Verify CADENTIA_AUTH_INTERNAL_API_KEY is identical in both services.",
                    exception);
        } catch (HttpClientErrorException.Unauthorized exception) {
            LOGGER.error("First-party auth service rejected the internal account lookup with HTTP 401.");
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "The first-party auth service rejected the internal account lookup. Verify the auth service is using the current build and internal API configuration.",
                    exception);
        } catch (HttpStatusCodeException exception) {
            LOGGER.error("First-party auth service returned HTTP {} during the internal account lookup.",
                    exception.getStatusCode().value());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "The first-party auth service returned an unexpected error while resolving the account.",
                    exception);
        } catch (ResourceAccessException exception) {
            LOGGER.error("First-party auth service could not be reached at {}.", baseUrl);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The first-party auth service could not be reached.", exception);
        } catch (RestClientException exception) {
            LOGGER.error("First-party auth service request failed with {}.", exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The first-party auth service could not be reached.", exception);
        }
    }

    public FirstPartyAuthInvitation invite(String email, String displayName) {
        if (internalApiKey == null || internalApiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "First-party auth service integration is not configured.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_API_KEY_HEADER, internalApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<FirstPartyAuthInvitation> response = restTemplate.exchange(
                    baseUrl + "/internal/users/invitations",
                    HttpMethod.POST,
                    new HttpEntity<>(new FirstPartyAuthInvitationRequest(email, displayName), headers),
                    FirstPartyAuthInvitation.class);
            if (response.getBody() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The auth service returned an empty invitation response.");
            }
            return response.getBody();
        } catch (HttpClientErrorException.Conflict exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An authentication account already exists for that email.", exception);
        } catch (HttpClientErrorException.Forbidden exception) {
            LOGGER.error("First-party auth service rejected the internal invitation request with HTTP 403.");
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "The first-party auth service rejected the API credentials. Verify CADENTIA_AUTH_INTERNAL_API_KEY is identical in both services.",
                    exception);
        } catch (HttpClientErrorException.Unauthorized exception) {
            LOGGER.error("First-party auth service rejected the internal invitation request with HTTP 401.");
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "The first-party auth service rejected the internal invitation request. Verify the auth service is using the current build and internal API configuration.",
                    exception);
        } catch (HttpStatusCodeException exception) {
            LOGGER.error("First-party auth service returned HTTP {} during the internal invitation request.",
                    exception.getStatusCode().value());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "The first-party auth service returned an unexpected error while creating the account.",
                    exception);
        } catch (ResourceAccessException exception) {
            LOGGER.error("First-party auth service could not be reached at {} during invitation.", baseUrl);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The first-party auth service could not be reached.", exception);
        } catch (RestClientException exception) {
            LOGGER.error("First-party auth invitation request failed with {}.", exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The first-party auth service could not be reached.", exception);
        }
    }
}
