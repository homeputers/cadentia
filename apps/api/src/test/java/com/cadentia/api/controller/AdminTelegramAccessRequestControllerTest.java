package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.bot.telegram.TelegramAccessRequest;
import com.cadentia.bot.telegram.TelegramAccessRequestRepository;
import com.cadentia.bot.telegram.TelegramAccessRequestService;
import com.cadentia.bot.telegram.TelegramAccessRequestStatus;
import com.cadentia.bot.telegram.TelegramChannelEvent;
import com.cadentia.bot.telegram.TelegramCommand;
import com.cadentia.bot.telegram.TelegramEventKind;
import com.cadentia.bot.telegram.TelegramIdentifierHasher;
import com.cadentia.bot.telegram.TelegramIdentityRepository;
import com.cadentia.bot.telegram.TelegramIdentityStatus;
import com.cadentia.bot.telegram.TelegramLinkedActor;
import com.cadentia.generated.model.TelegramAccessRequestDecisionRequest;
import com.cadentia.generated.model.TelegramAccessRequestDecisionResponse;
import com.cadentia.generated.model.TelegramAccessRequestListResponse;
import com.cadentia.runtime.InstanceConfiguration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AdminTelegramAccessRequestControllerTest {

    private TelegramAccessRequestService service;
    private AdminTelegramAccessRequestController controller;

    @BeforeEach
    void setUp() {
        TelegramIdentifierHasher hasher = new TelegramIdentifierHasher("test-secret");
        service = new TelegramAccessRequestService(
                hasher,
                new InMemoryIdentityRepository(),
                new InMemoryAccessRequestRepository(),
                () -> InstanceConfiguration.localDevelopment(
                        "church-a", "local", "bucket", "assets", "key", "cache", "events", List.of("cadentia")),
                null,
                null);
        controller = new AdminTelegramAccessRequestController(service);
    }

    @Test
    void listReturnsPendingRequestsWithMaskedReferenceOnly() {
        // Arrange
        service.requestAccess(event("42", "99"));

        // Act
        TelegramAccessRequestListResponse response = controller.listTelegramAccessRequests("church-a", null).getBody();

        // Assert
        String expectedMaskedReference = new TelegramIdentifierHasher("test-secret").hash("telegram", "42").substring(0, 12);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getStatus().getValue()).isEqualTo("PENDING");
        assertThat(response.getItems().get(0).getMaskedReference()).isEqualTo(expectedMaskedReference);
        assertThat(response.getItems().get(0).getMaskedReference()).isNotEqualTo("42");
    }

    @Test
    void approveReturnsDecidedRequest() {
        // Arrange
        UUID requestId = service.requestAccess(event("42", "99")).request().id();

        // Act
        TelegramAccessRequestDecisionResponse response = controller.approveTelegramAccessRequest(
                "church-a", requestId, new TelegramAccessRequestDecisionRequest().reason("Known member")).getBody();

        // Assert
        assertThat(response.getRequest().getStatus().getValue()).isEqualTo("APPROVED");
        assertThat(response.getRequest().getDecisionReason()).isEqualTo("Known member");
        assertThat(response.getRequest().getDecidedAt()).isNotNull();
    }

    @Test
    void approveUnknownRequestReturns404() {
        // Act / Assert
        assertThatThrownBy(() -> controller.approveTelegramAccessRequest("church-a", UUID.randomUUID(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void decidingTwiceReturns409() {
        // Arrange
        UUID requestId = service.requestAccess(event("42", "99")).request().id();
        controller.rejectTelegramAccessRequest("church-a", requestId, null);

        // Act / Assert
        assertThatThrownBy(() -> controller.approveTelegramAccessRequest("church-a", requestId, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void invalidStatusFilterReturns400() {
        // Act / Assert
        assertThatThrownBy(() -> controller.listTelegramAccessRequests("church-a", "BOGUS"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static TelegramChannelEvent event(String chatId, String userId) {
        return new TelegramChannelEvent(11L, TelegramEventKind.MESSAGE, chatId, userId, 7, "/requestaccess",
                TelegramCommand.REQUEST_ACCESS, null, null, null, null, Locale.ROOT, "corr");
    }

    private static class InMemoryIdentityRepository implements TelegramIdentityRepository {
        private final Map<String, TelegramLinkedActor> rows = new HashMap<>();

        @Override
        public Optional<TelegramLinkedActor> findByTelegramHashes(String channel, String chatHash, String userHash) {
            return Optional.ofNullable(rows.get(chatHash + ":" + userHash));
        }

        @Override
        public TelegramLinkedActor saveLink(String channel, String chatHash, String userHash, String churchInstanceId, Set<String> roles) {
            TelegramLinkedActor actor = new TelegramLinkedActor(UUID.randomUUID(), churchInstanceId, roles, TelegramIdentityStatus.LINKED);
            rows.put(chatHash + ":" + userHash, actor);
            return actor;
        }
    }

    private static class InMemoryAccessRequestRepository implements TelegramAccessRequestRepository {
        private final Map<UUID, TelegramAccessRequest> rows = new HashMap<>();

        @Override
        public TelegramAccessRequest save(TelegramAccessRequest request) {
            rows.put(request.id(), request);
            return request;
        }

        @Override
        public Optional<TelegramAccessRequest> findPending(String channel, String chatHash, String userHash, String churchInstanceId) {
            return rows.values().stream()
                    .filter(request -> request.chatHash().equals(chatHash) && request.userHash().equals(userHash)
                            && request.status() == TelegramAccessRequestStatus.PENDING)
                    .findFirst();
        }

        @Override
        public Optional<TelegramAccessRequest> findById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public List<TelegramAccessRequest> findByInstanceAndStatus(String churchInstanceId, TelegramAccessRequestStatus status) {
            return rows.values().stream()
                    .filter(request -> request.churchInstanceId().equals(churchInstanceId) && request.status() == status)
                    .toList();
        }

        @Override
        public Optional<TelegramAccessRequest> decide(
                UUID id, TelegramAccessRequestStatus decision, String decidedBy, String decisionReason, Instant decidedAt) {
            TelegramAccessRequest current = rows.get(id);
            if (current == null || current.status() != TelegramAccessRequestStatus.PENDING) {
                return Optional.empty();
            }
            TelegramAccessRequest updated = new TelegramAccessRequest(current.id(), current.channel(), current.chatHash(),
                    current.userHash(), null, current.churchInstanceId(), decision, current.requestedAt(), decidedAt,
                    decidedBy, decisionReason);
            rows.put(id, updated);
            return Optional.of(updated);
        }
    }
}
