package com.cadentia.bot.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.bot.telegram.TelegramAccessRequestService.AccessRequestOutcome;
import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramSendResult;
import com.cadentia.runtime.InstanceConfiguration;
import com.cadentia.runtime.InstanceConfigurationProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TelegramAccessRequestServiceTest {

    private TelegramIdentifierHasher hasher;
    private InMemoryIdentityRepository identityRepository;
    private InMemoryAccessRequestRepository accessRequestRepository;
    private CapturingOutboundClient outboundClient;
    private TelegramAccessRequestService service;

    @BeforeEach
    void setUp() {
        hasher = new TelegramIdentifierHasher("test-secret");
        identityRepository = new InMemoryIdentityRepository();
        accessRequestRepository = new InMemoryAccessRequestRepository();
        outboundClient = new CapturingOutboundClient();
        TelegramOutboundSendService outboundSendService = new TelegramOutboundSendService(
                new InMemoryTelegramOutboundRepository(), outboundClient, hasher);
        service = new TelegramAccessRequestService(
                hasher,
                identityRepository,
                accessRequestRepository,
                provider("church-a"),
                outboundSendService,
                null,
                Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void requestAccessCreatesPendingRequestWithRawChatId() {
        // Arrange
        TelegramChannelEvent event = event("42", "99");

        // Act
        TelegramAccessRequestService.AccessRequestResult result = service.requestAccess(event);

        // Assert
        assertThat(result.outcome()).isEqualTo(AccessRequestOutcome.CREATED);
        assertThat(result.request().status()).isEqualTo(TelegramAccessRequestStatus.PENDING);
        assertThat(result.request().chatId()).isEqualTo("42");
        assertThat(result.request().churchInstanceId()).isEqualTo("church-a");
        assertThat(result.request().chatHash()).isNotEqualTo("42");
        assertThat(result.request().userHash()).isNotEqualTo("99");
    }

    @Test
    void requestAccessIsIdempotentWhilePending() {
        // Arrange
        TelegramChannelEvent event = event("42", "99");
        service.requestAccess(event);

        // Act
        TelegramAccessRequestService.AccessRequestResult second = service.requestAccess(event);

        // Assert
        assertThat(second.outcome()).isEqualTo(AccessRequestOutcome.ALREADY_PENDING);
        assertThat(accessRequestRepository.rows).hasSize(1);
    }

    @Test
    void requestAccessReturnsAlreadyLinkedForLinkedIdentity() {
        // Arrange
        TelegramChannelEvent event = event("42", "99");
        identityRepository.saveLink("telegram", hasher.hash("telegram", "42"), hasher.hash("telegram", "99"),
                "church-a", Set.of("ROLE_WORSHIP_LEADER"));

        // Act
        TelegramAccessRequestService.AccessRequestResult result = service.requestAccess(event);

        // Assert
        assertThat(result.outcome()).isEqualTo(AccessRequestOutcome.ALREADY_LINKED);
        assertThat(accessRequestRepository.rows).isEmpty();
    }

    @Test
    void approveCreatesLinkedIdentityPurgesChatIdAndNotifiesRequester() {
        // Arrange
        TelegramAccessRequest request = service.requestAccess(event("42", "99")).request();

        // Act
        TelegramAccessRequest decided = service.approve(request.id(), "admin-1", "Known worship leader");

        // Assert
        assertThat(decided.status()).isEqualTo(TelegramAccessRequestStatus.APPROVED);
        assertThat(decided.decidedBy()).isEqualTo("admin-1");
        assertThat(decided.decisionReason()).isEqualTo("Known worship leader");
        assertThat(decided.chatId()).isNull();
        TelegramLinkedActor actor = identityRepository
                .findByTelegramHashes("telegram", request.chatHash(), request.userHash())
                .orElseThrow();
        assertThat(actor.status()).isEqualTo(TelegramIdentityStatus.LINKED);
        assertThat(actor.roles()).containsExactly("ROLE_WORSHIP_LEADER");
        assertThat(actor.churchInstanceId()).isEqualTo("church-a");
        assertThat(outboundClient.sent).hasSize(1);
        assertThat(outboundClient.sent.get(0).chatId()).isEqualTo("42");
        assertThat(outboundClient.sent.get(0).text()).contains("approved");
    }

    @Test
    void rejectPurgesChatIdAndNotifiesWithoutLinking() {
        // Arrange
        TelegramAccessRequest request = service.requestAccess(event("42", "99")).request();

        // Act
        TelegramAccessRequest decided = service.reject(request.id(), "admin-1", null);

        // Assert
        assertThat(decided.status()).isEqualTo(TelegramAccessRequestStatus.REJECTED);
        assertThat(decided.chatId()).isNull();
        assertThat(identityRepository.findByTelegramHashes("telegram", request.chatHash(), request.userHash())).isEmpty();
        assertThat(outboundClient.sent).hasSize(1);
        assertThat(outboundClient.sent.get(0).text()).contains("not approved");
    }

    @Test
    void approveUnknownRequestThrowsNotFound() {
        // Act / Assert
        assertThatThrownBy(() -> service.approve(UUID.randomUUID(), "admin-1", null))
                .isInstanceOf(TelegramAccessRequestDecisionException.class)
                .extracting(ex -> ((TelegramAccessRequestDecisionException) ex).reasonKind())
                .isEqualTo(TelegramAccessRequestDecisionException.Reason.NOT_FOUND);
    }

    @Test
    void approveDecidedRequestThrowsAlreadyDecided() {
        // Arrange
        TelegramAccessRequest request = service.requestAccess(event("42", "99")).request();
        service.reject(request.id(), "admin-1", null);

        // Act / Assert
        assertThatThrownBy(() -> service.approve(request.id(), "admin-1", null))
                .isInstanceOf(TelegramAccessRequestDecisionException.class)
                .extracting(ex -> ((TelegramAccessRequestDecisionException) ex).reasonKind())
                .isEqualTo(TelegramAccessRequestDecisionException.Reason.ALREADY_DECIDED);
    }

    @Test
    void listRequestsFiltersByInstanceAndStatus() {
        // Arrange
        TelegramAccessRequest pending = service.requestAccess(event("42", "99")).request();
        TelegramAccessRequest decided = service.requestAccess(event("43", "98")).request();
        service.approve(decided.id(), "admin-1", null);

        // Act
        List<TelegramAccessRequest> pendingRequests = service.listRequests("church-a", TelegramAccessRequestStatus.PENDING);
        List<TelegramAccessRequest> approvedRequests = service.listRequests("church-a", TelegramAccessRequestStatus.APPROVED);

        // Assert
        assertThat(pendingRequests).extracting(TelegramAccessRequest::id).containsExactly(pending.id());
        assertThat(approvedRequests).extracting(TelegramAccessRequest::id).containsExactly(decided.id());
        assertThat(service.listRequests("church-b", TelegramAccessRequestStatus.PENDING)).isEmpty();
    }

    private static TelegramChannelEvent event(String chatId, String userId) {
        return new TelegramChannelEvent(11L, TelegramEventKind.MESSAGE, chatId, userId, 7, "/requestaccess",
                TelegramCommand.REQUEST_ACCESS, null, null, null, null, Locale.ROOT, "corr");
    }

    private static InstanceConfigurationProvider provider(String instanceId) {
        return () -> InstanceConfiguration.localDevelopment(instanceId, "local", "bucket", "assets", "key", "cache", "events", List.of("cadentia"));
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
                    .filter(request -> request.channel().equals(channel)
                            && request.chatHash().equals(chatHash)
                            && request.userHash().equals(userHash)
                            && request.churchInstanceId().equals(churchInstanceId)
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

    private static class CapturingOutboundClient implements TelegramOutboundClient {
        private final List<TelegramRenderedMessage> sent = new ArrayList<>();

        @Override
        public TelegramSendResult send(TelegramRenderedMessage message) {
            sent.add(message);
            return TelegramSendResult.delivered("msg-1");
        }
    }
}
