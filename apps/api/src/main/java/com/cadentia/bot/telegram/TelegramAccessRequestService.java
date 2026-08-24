package com.cadentia.bot.telegram;

import com.cadentia.runtime.InstanceConfigurationProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TelegramAccessRequestService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramAccessRequestService.class);
    private static final String CHANNEL = "telegram";
    private static final Set<String> DEFAULT_ROLES = Set.of("ROLE_WORSHIP_LEADER");

    private final TelegramIdentifierHasher hasher;
    private final TelegramIdentityRepository identityRepository;
    private final TelegramAccessRequestRepository accessRequestRepository;
    private final InstanceConfigurationProvider configurationProvider;
    private final TelegramOutboundSendService outboundSendService;
    private final TelegramObservabilityRecorder observabilityRecorder;
    private final Clock clock;

    @Autowired
    public TelegramAccessRequestService(
            TelegramIdentifierHasher hasher,
            TelegramIdentityRepository identityRepository,
            TelegramAccessRequestRepository accessRequestRepository,
            InstanceConfigurationProvider configurationProvider,
            TelegramOutboundSendService outboundSendService,
            TelegramObservabilityRecorder observabilityRecorder) {
        this(hasher, identityRepository, accessRequestRepository, configurationProvider, outboundSendService,
                observabilityRecorder, Clock.systemUTC());
    }

    TelegramAccessRequestService(
            TelegramIdentifierHasher hasher,
            TelegramIdentityRepository identityRepository,
            TelegramAccessRequestRepository accessRequestRepository,
            InstanceConfigurationProvider configurationProvider,
            TelegramOutboundSendService outboundSendService,
            TelegramObservabilityRecorder observabilityRecorder,
            Clock clock) {
        this.hasher = hasher;
        this.identityRepository = identityRepository;
        this.accessRequestRepository = accessRequestRepository;
        this.configurationProvider = configurationProvider;
        this.outboundSendService = outboundSendService;
        this.observabilityRecorder = observabilityRecorder;
        this.clock = clock;
    }

    public enum AccessRequestOutcome {
        ALREADY_LINKED,
        ALREADY_PENDING,
        CREATED
    }

    public record AccessRequestResult(AccessRequestOutcome outcome, TelegramAccessRequest request) {}

    /**
     * Records a self-service access request for an unauthorized Telegram caller. This method
     * intentionally does not require an authorized identity; it is the entry point for creating one.
     */
    public AccessRequestResult requestAccess(TelegramChannelEvent event) {
        Instant startedAt = clock.instant();
        String chatHash = hasher.hash(CHANNEL, event.chatId());
        String userHash = hasher.hash(CHANNEL, event.userId());
        String churchInstanceId = configurationProvider.current().instanceId();

        Optional<TelegramLinkedActor> existing = identityRepository.findByTelegramHashes(CHANNEL, chatHash, userHash);
        if (existing.isPresent()
                && existing.get().status() == TelegramIdentityStatus.LINKED
                && churchInstanceId.equals(existing.get().churchInstanceId())) {
            record("access_request", "already_linked", startedAt, churchInstanceId, userHash);
            return new AccessRequestResult(AccessRequestOutcome.ALREADY_LINKED, null);
        }

        Optional<TelegramAccessRequest> pending = accessRequestRepository.findPending(CHANNEL, chatHash, userHash, churchInstanceId);
        if (pending.isPresent()) {
            record("access_request", "already_pending", startedAt, churchInstanceId, userHash);
            return new AccessRequestResult(AccessRequestOutcome.ALREADY_PENDING, pending.get());
        }

        TelegramAccessRequest request = accessRequestRepository.save(new TelegramAccessRequest(
                UUID.randomUUID(), CHANNEL, chatHash, userHash, event.chatId(), churchInstanceId,
                TelegramAccessRequestStatus.PENDING, clock.instant(), null, null, null));
        record("access_request", "created", startedAt, churchInstanceId, userHash);
        audit("telegram_access_requested", request.id(), "telegram", churchInstanceId);
        return new AccessRequestResult(AccessRequestOutcome.CREATED, request);
    }

    public List<TelegramAccessRequest> listRequests(String churchInstanceId, TelegramAccessRequestStatus status) {
        return accessRequestRepository.findByInstanceAndStatus(churchInstanceId, status);
    }

    public TelegramAccessRequest approve(UUID requestId, String adminActorId, String reason) {
        TelegramAccessRequest pending = pendingOrThrow(requestId);
        String rawChatId = pending.chatId();
        identityRepository.saveLink(pending.channel(), pending.chatHash(), pending.userHash(),
                pending.churchInstanceId(), DEFAULT_ROLES);
        TelegramAccessRequest decided = accessRequestRepository
                .decide(requestId, TelegramAccessRequestStatus.APPROVED, adminActorId, reason, clock.instant())
                .orElseThrow(() -> new TelegramAccessRequestDecisionException(
                        TelegramAccessRequestDecisionException.Reason.ALREADY_DECIDED,
                        "Telegram access request was already decided."));
        audit("telegram_access_request_approved", requestId, adminActorId, decided.churchInstanceId());
        notifyRequester(rawChatId, "accessApproved", requestId, "telegram_access_request_approved");
        return decided;
    }

    public TelegramAccessRequest reject(UUID requestId, String adminActorId, String reason) {
        TelegramAccessRequest pending = pendingOrThrow(requestId);
        String rawChatId = pending.chatId();
        TelegramAccessRequest decided = accessRequestRepository
                .decide(requestId, TelegramAccessRequestStatus.REJECTED, adminActorId, reason, clock.instant())
                .orElseThrow(() -> new TelegramAccessRequestDecisionException(
                        TelegramAccessRequestDecisionException.Reason.ALREADY_DECIDED,
                        "Telegram access request was already decided."));
        audit("telegram_access_request_rejected", requestId, adminActorId, decided.churchInstanceId());
        notifyRequester(rawChatId, "accessRejected", requestId, "telegram_access_request_rejected");
        return decided;
    }

    private TelegramAccessRequest pendingOrThrow(UUID requestId) {
        TelegramAccessRequest request = accessRequestRepository.findById(requestId)
                .orElseThrow(() -> new TelegramAccessRequestDecisionException(
                        TelegramAccessRequestDecisionException.Reason.NOT_FOUND,
                        "Telegram access request was not found."));
        if (request.status() != TelegramAccessRequestStatus.PENDING) {
            throw new TelegramAccessRequestDecisionException(
                    TelegramAccessRequestDecisionException.Reason.ALREADY_DECIDED,
                    "Telegram access request was already decided.");
        }
        return request;
    }

    private void notifyRequester(String rawChatId, String messageKey, UUID requestId, String operation) {
        if (rawChatId == null || rawChatId.isBlank() || outboundSendService == null) {
            return;
        }
        try {
            outboundSendService.send(
                    TelegramRenderedMessage.message(rawChatId, TelegramI18n.text(messageKey, locale()), null),
                    "telegram-access-request:" + requestId,
                    operation);
        } catch (RuntimeException ex) {
            LOGGER.warn("telegram_access_notification_failed requestId={} category={}", requestId,
                    ex.getClass().getSimpleName());
        }
    }

    private Locale locale() {
        return TelegramI18n.locale(configurationProvider.current().locale());
    }

    private void record(String operation, String outcome, Instant startedAt, String instanceRef, String actorRef) {
        if (observabilityRecorder != null) {
            observabilityRecorder.record(operation, outcome, Duration.between(startedAt, clock.instant()), 0,
                    "none", "none", instanceRef, actorRef);
        }
    }

    private void audit(String action, UUID requestId, String actorRef, String churchInstanceId) {
        if (observabilityRecorder != null) {
            observabilityRecorder.audit(action, "telegram_access_request", String.valueOf(requestId), actorRef,
                    churchInstanceId, Map.of());
        }
    }
}
