package com.cadentia.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.server.ResponseStatusException;

class FirstPartyAuthClientTest {

    private MockRestServiceServer server;
    private FirstPartyAuthClient client;

    @BeforeEach
    void setUp() {
        RestTemplateBuilder builder = new RestTemplateBuilder()
                .customizers(restTemplate -> server = MockRestServiceServer.bindTo(restTemplate).build());
        client = new FirstPartyAuthClient(builder, "http://auth.local/", "shared-secret");
    }

    @Test
    void findByEmailReturnsTheActiveAuthAccount() {
        UUID userId = UUID.randomUUID();
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/internal/users/by-email");
                    assertThat(request.getURI().getQuery()).isEqualTo("email=owner@example.com");
                })
                .andExpect(header("X-Cadentia-Internal-Key", "shared-secret"))
                .andRespond(withSuccess("""
                        {
                          "userId": "%s",
                          "email": "owner@example.com",
                          "displayName": "Owner"
                        }
                        """.formatted(userId), MediaType.APPLICATION_JSON));

        FirstPartyAuthUser user = client.findByEmail(" Owner@Example.com ");

        assertThat(user.userId()).isEqualTo(userId);
        assertThat(user.email()).isEqualTo("owner@example.com");
        server.verify();
    }

    @Test
    void findByEmailExplainsAnInternalCredentialMismatch() {
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/internal/users/by-email"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.findByEmail("owner@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getReason()).contains("CADENTIA_AUTH_INTERNAL_API_KEY");
                });
        server.verify();
    }

    @Test
    void findByEmailDistinguishesMissingAuthAccount() {
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/internal/users/by-email"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.findByEmail("missing@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo("No active first-party account exists for that email.");
                });
        server.verify();
    }

    @Test
    void inviteCreatesAnAuthAccountAndForwardsTheActivationToken() {
        UUID userId = UUID.randomUUID();
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/internal/users/invitations");
                    assertThat(request.getMethod()).isEqualTo(org.springframework.http.HttpMethod.POST);
                    assertThat(request.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
                })
                .andExpect(header("X-Cadentia-Internal-Key", "shared-secret"))
                .andRespond(withSuccess("""
                        {
                          "userId": "%s",
                          "email": "new@example.com",
                          "displayName": "New User",
                          "activationToken": "activation-token"
                        }
                        """.formatted(userId), MediaType.APPLICATION_JSON));

        FirstPartyAuthInvitation invitation = client.invite("new@example.com", "New User");

        assertThat(invitation.userId()).isEqualTo(userId);
        assertThat(invitation.activationToken()).isEqualTo("activation-token");
        server.verify();
    }
}
