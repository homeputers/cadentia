package com.cadentia.bot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.runtime.InstanceConfiguration;
import com.cadentia.runtime.InstanceConfigurationProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TelegramAuthorizationServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-19T12:00:00Z"), ZoneOffset.UTC);
    private final TelegramIdentifierHasher hasher = new TelegramIdentifierHasher("test-secret");
    private final InMemoryIdentityRepository identities = new InMemoryIdentityRepository();
    private final InMemorySessionRepository sessions = new InMemorySessionRepository();
    private final TelegramAuthorizationService service = new TelegramAuthorizationService(
            hasher, identities, sessions, provider("church-a"), CLOCK, Duration.ofMinutes(30), Duration.ofHours(4));

    @Test
    void deniesUnlinkedUsersWithoutCreatingSession() {
        // Arrange
        TelegramChannelEvent event = event("42", "99");

        // Act
        TelegramAuthorizationService.TelegramAuthorizationDecision decision = service.authorize(event, TelegramProtectedAction.SETLIST_GENERATE);

        // Assert
        assertThat(decision.permitted()).isFalse();
        assertThat(decision.status()).isEqualTo(TelegramIdentityStatus.UNLINKED);
        assertThat(sessions.saved).isEmpty();
    }

    @Test
    void authorizesLinkedUserAndStoresOnlyHashedReferences() {
        // Arrange
        TelegramChannelEvent event = event("42", "99");
        identities.put(event, new TelegramLinkedActor(UUID.randomUUID(), "church-a", Set.of("ROLE_WORSHIP_LEADER"), TelegramIdentityStatus.LINKED));

        // Act
        TelegramAuthorizationService.TelegramAuthorizationDecision decision = service.authorize(event, TelegramProtectedAction.SETLIST_GENERATE);
        TelegramBotSession session = service.touch(decision, event, TelegramSessionState.NEW_SETLIST_ACTIVE);

        // Assert
        assertThat(decision.permitted()).isTrue();
        assertThat(session.state()).isEqualTo(TelegramSessionState.NEW_SETLIST_ACTIVE);
        assertThat(session.chatHash()).isNotEqualTo("42");
        assertThat(session.userHash()).isNotEqualTo("99");
        assertThat(session.auditMetadataJson()).doesNotContain("42", "99");
    }

    @Test
    void deniesCrossInstanceRevokedDisabledAndUnauthorizedRolesDeterministically() {
        // Arrange
        TelegramChannelEvent cross = event("1", "1");
        TelegramChannelEvent revoked = event("2", "2");
        TelegramChannelEvent disabled = event("3", "3");
        TelegramChannelEvent unauthorized = event("4", "4");
        identities.put(cross, new TelegramLinkedActor(UUID.randomUUID(), "church-b", Set.of("ROLE_WORSHIP_LEADER"), TelegramIdentityStatus.LINKED));
        identities.put(revoked, new TelegramLinkedActor(UUID.randomUUID(), "church-a", Set.of("ROLE_WORSHIP_LEADER"), TelegramIdentityStatus.REVOKED));
        identities.put(disabled, new TelegramLinkedActor(UUID.randomUUID(), "church-a", Set.of("ROLE_WORSHIP_LEADER"), TelegramIdentityStatus.DISABLED));
        identities.put(unauthorized, new TelegramLinkedActor(UUID.randomUUID(), "church-a", Set.of("ROLE_ASSIGNED_MUSICIAN"), TelegramIdentityStatus.LINKED));

        // Act / Assert
        assertThat(service.authorize(cross, TelegramProtectedAction.SETLIST_GENERATE).status()).isEqualTo(TelegramIdentityStatus.UNAUTHORIZED);
        assertThat(service.authorize(revoked, TelegramProtectedAction.SETLIST_GENERATE).status()).isEqualTo(TelegramIdentityStatus.REVOKED);
        assertThat(service.authorize(disabled, TelegramProtectedAction.SETLIST_GENERATE).status()).isEqualTo(TelegramIdentityStatus.DISABLED);
        assertThat(service.authorize(unauthorized, TelegramProtectedAction.SETLIST_GENERATE).status()).isEqualTo(TelegramIdentityStatus.UNAUTHORIZED);
        assertThat(sessions.saved).isEmpty();
    }

    @Test
    void resumesCancelsAndExpiresPersistedSessionsWithoutCompletingFlow() {
        // Arrange
        TelegramChannelEvent event = event("42", "99");
        identities.put(event, new TelegramLinkedActor(UUID.randomUUID(), "church-a", Set.of("ROLE_WORSHIP_LEADER"), TelegramIdentityStatus.LINKED));
        TelegramAuthorizationService.TelegramAuthorizationDecision started = service.authorize(event, TelegramProtectedAction.SETLIST_GENERATE);
        TelegramBotSession active = service.touch(started, event, TelegramSessionState.PENDING_CONFIRMATION);

        // Act
        TelegramAuthorizationService.TelegramAuthorizationDecision resumed = service.authorize(event, TelegramProtectedAction.SESSION_STATUS);
        service.touch(resumed, event, TelegramSessionState.CANCELLED);
        sessions.save(new TelegramBotSession(active.id(), active.channel(), active.chatHash(), active.userHash(), active.churchInstanceId(), active.actorId(),
                TelegramSessionState.PENDING_CONFIRMATION, "proposal-1", active.lastUpdateId(), active.lastMessageId(), active.createdAt(), active.updatedAt(),
                Instant.parse("2026-06-19T11:00:00Z"), active.absoluteExpiration(), active.auditMetadataJson()));
        TelegramAuthorizationService.TelegramAuthorizationDecision afterExpiry = service.authorize(event, TelegramProtectedAction.SESSION_STATUS);

        // Assert
        assertThat(resumed.session().id()).isEqualTo(active.id());
        assertThat(sessions.saved.get(active.id()).state()).isEqualTo(TelegramSessionState.EXPIRED);
        assertThat(afterExpiry.session().id()).isNotEqualTo(active.id());
        assertThat(afterExpiry.session().state()).isEqualTo(TelegramSessionState.IDLE);
    }

    private TelegramChannelEvent event(String chatId, String userId) {
        return new TelegramChannelEvent(11L, TelegramEventKind.MESSAGE, chatId, userId, 7, "/newsetlist", TelegramCommand.NEW_SETLIST,
                null, null, null, null, java.util.Locale.ROOT, "corr");
    }

    private InstanceConfigurationProvider provider(String instanceId) {
        return () -> InstanceConfiguration.localDevelopment(instanceId, "local", "bucket", "assets", "key", "cache", "events", java.util.List.of("cadentia"));
    }

    private class InMemoryIdentityRepository implements TelegramIdentityRepository {
        private final Map<String, TelegramLinkedActor> rows = new HashMap<>();

        void put(TelegramChannelEvent event, TelegramLinkedActor actor) {
            rows.put(hasher.hash("telegram", event.chatId()) + ":" + hasher.hash("telegram", event.userId()), actor);
        }

        @Override
        public Optional<TelegramLinkedActor> findByTelegramHashes(String channel, String chatHash, String userHash) {
            return Optional.ofNullable(rows.get(chatHash + ":" + userHash));
        }
    }

    private static class InMemorySessionRepository implements TelegramBotSessionRepository {
        private final Map<UUID, TelegramBotSession> saved = new HashMap<>();

        @Override
        public Optional<TelegramBotSession> findActive(String channel, String chatHash, String userHash) {
            return saved.values().stream()
                    .filter(session -> session.channel().equals(channel) && session.chatHash().equals(chatHash) && session.userHash().equals(userHash))
                    .filter(session -> session.state() != TelegramSessionState.CANCELLED && session.state() != TelegramSessionState.COMPLETED && session.state() != TelegramSessionState.EXPIRED)
                    .findFirst();
        }

        @Override
        public TelegramBotSession save(TelegramBotSession session) {
            saved.put(session.id(), session);
            return session;
        }

        @Override
        public void transition(UUID sessionId, TelegramSessionState state) {
            TelegramBotSession session = saved.get(sessionId);
            saved.put(sessionId, new TelegramBotSession(session.id(), session.channel(), session.chatHash(), session.userHash(), session.churchInstanceId(),
                    session.actorId(), state, session.pendingConfirmationRef(), session.lastUpdateId(), session.lastMessageId(), session.createdAt(),
                    session.updatedAt(), session.inactivityDeadline(), session.absoluteExpiration(), session.auditMetadataJson()));
        }
    }
}
