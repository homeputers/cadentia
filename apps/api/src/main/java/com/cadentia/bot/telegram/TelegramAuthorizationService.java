package com.cadentia.bot.telegram;

import com.cadentia.runtime.InstanceConfigurationProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TelegramAuthorizationService {
    private static final String CHANNEL = "telegram";
    private static final Set<String> SETLIST_ROLES = Set.of("ROLE_ADMIN", "ROLE_WORSHIP_LEADER", "ROLE_TEAM_SCHEDULER");
    private static final Set<String> SETTINGS_ROLES = Set.of("ROLE_ADMIN", "ROLE_WORSHIP_LEADER");
    private static final Set<String> SESSION_ROLES = Set.of("ROLE_ADMIN", "ROLE_WORSHIP_LEADER", "ROLE_TEAM_SCHEDULER");

    private final TelegramIdentifierHasher hasher;
    private final TelegramIdentityRepository identityRepository;
    private final TelegramBotSessionRepository sessionRepository;
    private final InstanceConfigurationProvider configurationProvider;
    private final Clock clock;
    private final Duration inactivityTtl;
    private final Duration absoluteTtl;

    @Autowired
    public TelegramAuthorizationService(
            TelegramIdentifierHasher hasher,
            TelegramIdentityRepository identityRepository,
            TelegramBotSessionRepository sessionRepository,
            InstanceConfigurationProvider configurationProvider,
            @Value("${cadentia.telegram.session-inactivity-ttl:PT30M}") Duration inactivityTtl,
            @Value("${cadentia.telegram.session-absolute-ttl:PT4H}") Duration absoluteTtl) {
        this(hasher, identityRepository, sessionRepository, configurationProvider, Clock.systemUTC(), inactivityTtl, absoluteTtl);
    }

    TelegramAuthorizationService(
            TelegramIdentifierHasher hasher,
            TelegramIdentityRepository identityRepository,
            TelegramBotSessionRepository sessionRepository,
            InstanceConfigurationProvider configurationProvider,
            Clock clock,
            Duration inactivityTtl,
            Duration absoluteTtl) {
        this.hasher = hasher;
        this.identityRepository = identityRepository;
        this.sessionRepository = sessionRepository;
        this.configurationProvider = configurationProvider;
        this.clock = clock;
        this.inactivityTtl = inactivityTtl;
        this.absoluteTtl = absoluteTtl;
    }

    public TelegramAuthorizationDecision authorize(TelegramChannelEvent event, TelegramProtectedAction action) {
        String chatHash = hasher.hash(CHANNEL, event.chatId());
        String userHash = hasher.hash(CHANNEL, event.userId());
        Optional<TelegramLinkedActor> actor = identityRepository.findByTelegramHashes(CHANNEL, chatHash, userHash);
        if (actor.isEmpty()) {
            return TelegramAuthorizationDecision.deny(TelegramIdentityStatus.UNLINKED, "Please link your Cadentia account before using this bot.");
        }
        TelegramLinkedActor linked = actor.get();
        if (linked.status() != TelegramIdentityStatus.LINKED) {
            return TelegramAuthorizationDecision.deny(linked.status(), safeMessage(linked.status()));
        }
        String currentInstance = configurationProvider.current().instanceId();
        if (!currentInstance.equals(linked.churchInstanceId())) {
            return TelegramAuthorizationDecision.deny(TelegramIdentityStatus.UNAUTHORIZED, "This Telegram account is not authorized for this church instance.");
        }
        if (!rolePermits(linked, action)) {
            return TelegramAuthorizationDecision.deny(TelegramIdentityStatus.UNAUTHORIZED, "Your Cadentia role cannot perform this action from Telegram.");
        }
        TelegramBotSession session = sessionRepository.findActive(CHANNEL, chatHash, userHash)
                .map(existing -> expireIfNeeded(existing).orElse(existing))
                .filter(existing -> existing.state() != TelegramSessionState.EXPIRED)
                .orElseGet(() -> newSession(event, linked, chatHash, userHash));
        return TelegramAuthorizationDecision.allow(linked, session);
    }

    public TelegramBotSession touch(TelegramAuthorizationDecision decision, TelegramChannelEvent event, TelegramSessionState nextState) {
        Instant now = Instant.now(clock);
        TelegramBotSession current = decision.session();
        TelegramBotSession updated = new TelegramBotSession(current.id(), current.channel(), current.chatHash(), current.userHash(),
                current.churchInstanceId(), current.actorId(), nextState, current.pendingConfirmationRef(), event.updateId(), event.messageId(),
                current.createdAt(), now, now.plus(inactivityTtl), current.absoluteExpiration(), "{\"source\":\"telegram\"}");
        return sessionRepository.save(updated);
    }

    private Optional<TelegramBotSession> expireIfNeeded(TelegramBotSession session) {
        Instant now = Instant.now(clock);
        if (now.isAfter(session.inactivityDeadline()) || now.isAfter(session.absoluteExpiration())) {
            sessionRepository.transition(session.id(), TelegramSessionState.EXPIRED);
            return Optional.of(new TelegramBotSession(session.id(), session.channel(), session.chatHash(), session.userHash(), session.churchInstanceId(),
                    session.actorId(), TelegramSessionState.EXPIRED, session.pendingConfirmationRef(), session.lastUpdateId(), session.lastMessageId(),
                    session.createdAt(), now, session.inactivityDeadline(), session.absoluteExpiration(), session.auditMetadataJson()));
        }
        return Optional.empty();
    }

    private TelegramBotSession newSession(TelegramChannelEvent event, TelegramLinkedActor actor, String chatHash, String userHash) {
        Instant now = Instant.now(clock);
        return sessionRepository.save(new TelegramBotSession(UUID.randomUUID(), CHANNEL, chatHash, userHash, actor.churchInstanceId(), actor.actorId(),
                TelegramSessionState.IDLE, null, event.updateId(), event.messageId(), now, now, now.plus(inactivityTtl), now.plus(absoluteTtl),
                "{\"source\":\"telegram\"}"));
    }

    private boolean rolePermits(TelegramLinkedActor actor, TelegramProtectedAction action) {
        return switch (action) {
            case SETTINGS -> actor.hasAnyRole(SETTINGS_ROLES);
            case SETLIST_GENERATE, SESSION_STATUS, SESSION_CANCEL, CONVERSATION_CONTINUE -> actor.hasAnyRole(SESSION_ROLES);
        };
    }

    private String safeMessage(TelegramIdentityStatus status) {
        return switch (status) {
            case REVOKED -> "Telegram access has been revoked. Please contact your Cadentia administrator.";
            case DISABLED -> "Telegram access is disabled for this church instance.";
            case UNAUTHORIZED -> "This Telegram account is not authorized for that action.";
            case UNLINKED -> "Please link your Cadentia account before using this bot.";
            case LINKED -> "Authorized.";
        };
    }

    public record TelegramAuthorizationDecision(
            boolean permitted,
            TelegramIdentityStatus status,
            String safeResponse,
            TelegramLinkedActor actor,
            TelegramBotSession session) {
        static TelegramAuthorizationDecision allow(TelegramLinkedActor actor, TelegramBotSession session) {
            return new TelegramAuthorizationDecision(true, TelegramIdentityStatus.LINKED, "Authorized.", actor, session);
        }

        static TelegramAuthorizationDecision deny(TelegramIdentityStatus status, String safeResponse) {
            return new TelegramAuthorizationDecision(false, status, safeResponse, null, null);
        }
    }
}
