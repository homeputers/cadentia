package com.cadentia.bot.telegram;

import com.cadentia.api.controller.ConversationSessionFacade;
import com.cadentia.generated.model.ConversationConfirmRequest;
import com.cadentia.generated.model.ConversationSessionStateResponse;
import com.cadentia.generated.model.ConversationState;
import com.cadentia.generated.model.ConversationSlotUpdateRequest;
import com.cadentia.generated.model.ConversationSlotUpdateRequestSlotPatch;
import com.cadentia.generated.model.KeyPolicy;
import com.cadentia.generated.model.SetlistCounts;
import com.cadentia.generated.model.SetlistProposalResponse;
import com.cadentia.generated.model.SlotValueSource;
import com.cadentia.generated.model.TempoPolicy;
import com.cadentia.reng.SetlistService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DefaultTelegramConversationGateway implements TelegramConversationGateway {
    private static final Pattern COUNTS_VALUE = Pattern.compile("(?<praise>\\d{1,2})p(?<worship>\\d{1,2})w");
    private static final Map<String, KeyPolicy> KEY_POLICIES = Map.of(
            "minimal", new KeyPolicy(true, true, 2),
            "same", new KeyPolicy(true, false, 1),
            "flex", new KeyPolicy(false, true, 4));
    private static final Map<String, TempoPolicy> TEMPO_POLICIES = Map.of(
            "tight", new TempoPolicy(8),
            "smooth", new TempoPolicy(12),
            "open", new TempoPolicy(20));

    private final ConversationSessionFacade facade;
    private final TelegramAuthorizationService authorizationService;
    private final SetlistService setlistService;

    @Autowired
    public DefaultTelegramConversationGateway(
            ConversationSessionFacade facade,
            TelegramAuthorizationService authorizationService,
            SetlistService setlistService) {
        this.facade = facade;
        this.authorizationService = authorizationService;
        this.setlistService = setlistService;
    }

    DefaultTelegramConversationGateway(ConversationSessionFacade facade) {
        this.facade = facade;
        this.authorizationService = null;
        this.setlistService = null;
    }

    @Override
    public TelegramAdapterResponse start(TelegramChannelEvent event) {
        TelegramAuthorizationService.TelegramAuthorizationDecision decision = authorize(event, TelegramProtectedAction.SESSION_STATUS);
        if (!decision.permitted()) {
            return denied(event, decision);
        }
        facade.get(sessionId(event));
        touch(decision, event, TelegramSessionState.IDLE);
        return new TelegramAdapterResponse(TelegramAdapterResponseStatus.STARTED, "Welcome to Cadentia.", event, null);
    }

    @Override
    public TelegramAdapterResponse help(TelegramChannelEvent event) {
        return new TelegramAdapterResponse(TelegramAdapterResponseStatus.CONTINUED, "Use /newsetlist to begin.", event, null);
    }

    @Override
    public TelegramAdapterResponse newSetlist(TelegramChannelEvent event) {
        TelegramAuthorizationService.TelegramAuthorizationDecision decision = authorize(event, TelegramProtectedAction.SETLIST_GENERATE);
        if (!decision.permitted()) {
            return denied(event, decision);
        }
        ConversationSessionStateResponse state = facade.update(sessionId(event), new ConversationSlotUpdateRequest().source(SlotValueSource.MENU));
        touch(decision, event, TelegramSessionState.NEW_SETLIST_ACTIVE);
        return new TelegramAdapterResponse(
                TelegramAdapterResponseStatus.STARTED,
                "New setlist flow started.",
                event,
                null,
                state.getSlots());
    }

    @Override
    public TelegramAdapterResponse status(TelegramChannelEvent event) {
        TelegramAuthorizationService.TelegramAuthorizationDecision decision = authorize(event, TelegramProtectedAction.SESSION_STATUS);
        if (!decision.permitted()) {
            return denied(event, decision);
        }
        ConversationSessionStateResponse state = facade.get(sessionId(event));
        touch(decision, event, currentState(decision));
        return new TelegramAdapterResponse(TelegramAdapterResponseStatus.CONTINUED, statusSummary(state), event, null);
    }

    @Override
    public TelegramAdapterResponse cancel(TelegramChannelEvent event) {
        TelegramAuthorizationService.TelegramAuthorizationDecision decision = authorize(event, TelegramProtectedAction.SESSION_CANCEL);
        if (!decision.permitted()) {
            return denied(event, decision);
        }
        facade.cancel(sessionId(event));
        touch(decision, event, TelegramSessionState.CANCELLED);
        return new TelegramAdapterResponse(TelegramAdapterResponseStatus.CANCELLED, "Session cancelled.", event, null);
    }

    @Override
    public TelegramAdapterResponse settings(TelegramChannelEvent event) {
        TelegramAuthorizationService.TelegramAuthorizationDecision decision = authorize(event, TelegramProtectedAction.SETTINGS);
        if (!decision.permitted()) {
            return denied(event, decision);
        }
        touch(decision, event, currentState(decision));
        return new TelegramAdapterResponse(TelegramAdapterResponseStatus.CONTINUED, "Settings opened.", event, null);
    }

    @Override
    public TelegramAdapterResponse text(TelegramChannelEvent event) {
        TelegramAuthorizationService.TelegramAuthorizationDecision decision = authorize(event, TelegramProtectedAction.CONVERSATION_CONTINUE);
        if (!decision.permitted()) {
            return denied(event, decision);
        }
        ConversationSessionStateResponse state = facade.ingestFreeText(sessionId(event), event.text());
        touch(decision, event, toTelegramState(state));
        return new TelegramAdapterResponse(
                TelegramAdapterResponseStatus.CONTINUED,
                String.join(" ", state.getAuditMessages()),
                event,
                null,
                state.getSlots());
    }

    @Override
    public TelegramAdapterResponse menuSelection(TelegramChannelEvent event) {
        TelegramAuthorizationService.TelegramAuthorizationDecision decision = authorize(event, TelegramProtectedAction.CONVERSATION_CONTINUE);
        if (!decision.permitted()) {
            return denied(event, decision);
        }
        if (event.callbackAction() == TelegramCallbackAction.CONFIRM) {
            ConversationSessionStateResponse current = facade.get(sessionId(event));
            if (current.getState() != ConversationState.READY_TO_CONFIRM) {
                touch(decision, event, toTelegramState(current));
                return new TelegramAdapterResponse(
                        TelegramAdapterResponseStatus.CONTINUED,
                        "Session is not ready to confirm. Reply with scripture, theme, or setlist details first.",
                        event,
                        event.callbackAction().guidedField(),
                        current.getSlots());
            }
            ConversationSessionStateResponse state = facade.confirm(sessionId(event), new ConversationConfirmRequest().accepted(true));
            SetlistProposalResponse proposal = setlistService == null ? null : setlistService.generate(state.getSlots());
            if (proposal != null) {
                facade.recordRecommendationCorrelation(
                        sessionId(event),
                        proposal.getRecommendationResultId(),
                        proposal.getExplanation() == null ? null : proposal.getExplanation().getSetlistId(),
                        proposal.getExplanation() == null ? null : proposal.getExplanation().getSetlistVersionId());
            }
            touch(decision, event, TelegramSessionState.COMPLETED);
            return new TelegramAdapterResponse(
                    TelegramAdapterResponseStatus.COMPLETED,
                    proposal == null ? "Setlist confirmed." : recommendationSummary(proposal),
                    event,
                    event.callbackAction().guidedField(),
                    state.getSlots(),
                    proposal);
        }
        if (event.callbackAction() == TelegramCallbackAction.CANCEL) {
            return cancel(event);
        }
        if (event.callbackAction() == TelegramCallbackAction.REVISE) {
            ConversationSessionStateResponse state = facade.update(sessionId(event), new ConversationSlotUpdateRequest().source(SlotValueSource.USER_EDIT));
            touch(decision, event, TelegramSessionState.NEW_SETLIST_ACTIVE);
            return new TelegramAdapterResponse(
                    TelegramAdapterResponseStatus.CONTINUED,
                    String.join(" ", state.getAuditMessages()),
                    event,
                    event.callbackAction().guidedField(),
                    state.getSlots());
        }
        ConversationSlotUpdateRequest request = menuPatch(event);
        if (request == null) {
            touch(decision, event, currentState(decision));
            return new TelegramAdapterResponse(
                    TelegramAdapterResponseStatus.INVALID,
                    "Unsupported guided menu selection.",
                    event,
                    event.callbackAction().guidedField());
        }
        ConversationSessionStateResponse state = facade.update(sessionId(event), request);
        touch(decision, event, toTelegramState(state));
        return new TelegramAdapterResponse(
                TelegramAdapterResponseStatus.CONTINUED,
                String.join(" ", state.getAuditMessages()),
                event,
                event.callbackAction().guidedField(),
                state.getSlots());
    }

    private ConversationSlotUpdateRequest menuPatch(TelegramChannelEvent event) {
        ConversationSlotUpdateRequestSlotPatch patch = new ConversationSlotUpdateRequestSlotPatch();
        String value = event.callbackValue() == null ? "" : event.callbackValue().toLowerCase(Locale.ROOT);
        switch (event.callbackAction()) {
            case LANGUAGE -> {
                if (!List.of("en", "es", "pt").contains(value)) {
                    return null;
                }
                patch.language(value);
            }
            case SHAPE_COUNTS -> {
                SetlistCounts counts = counts(value);
                if (counts == null) {
                    return null;
                }
                patch.counts(counts);
            }
            case KEY_POLICY -> {
                KeyPolicy keyPolicy = KEY_POLICIES.get(value);
                if (keyPolicy == null) {
                    return null;
                }
                patch.keyPolicy(keyPolicy);
            }
            case TEMPO_POLICY -> {
                TempoPolicy tempoPolicy = TEMPO_POLICIES.get(value);
                if (tempoPolicy == null) {
                    return null;
                }
                patch.tempoPolicy(tempoPolicy);
            }
            case ENERGY_ARC -> {
                ConversationSlotUpdateRequestSlotPatch.EnergyArcEnum energyArc =
                        ConversationSlotUpdateRequestSlotPatch.EnergyArcEnum.fromValue(value);
                if (energyArc == null) {
                    return null;
                }
                patch.energyArc(energyArc);
            }
            case SERVICE_MOMENT -> {
                ConversationSlotUpdateRequestSlotPatch.ServiceMomentEnum serviceMoment =
                        ConversationSlotUpdateRequestSlotPatch.ServiceMomentEnum.fromValue(value);
                if (serviceMoment == null) {
                    return null;
                }
                patch.serviceMoment(serviceMoment);
            }
            default -> {
                return null;
            }
        }
        return new ConversationSlotUpdateRequest().source(SlotValueSource.MENU).slotPatch(patch);
    }

    private SetlistCounts counts(String value) {
        Matcher matcher = COUNTS_VALUE.matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        int praise = Integer.parseInt(matcher.group("praise"));
        int worship = Integer.parseInt(matcher.group("worship"));
        if (praise > 25 || worship > 25 || praise + worship == 0) {
            return null;
        }
        return new SetlistCounts(praise, worship);
    }

    private TelegramSessionState toTelegramState(ConversationSessionStateResponse state) {
        return switch (state.getState()) {
            case READY_TO_CONFIRM, CONFIRMED -> TelegramSessionState.PENDING_CONFIRMATION;
            case CANCELLED -> TelegramSessionState.CANCELLED;
            default -> TelegramSessionState.NEW_SETLIST_ACTIVE;
        };
    }

    private String recommendationSummary(SetlistProposalResponse proposal) {
        List<String> auditMessages = proposal.getAuditMessages() == null ? List.of() : proposal.getAuditMessages();
        return "Deterministic setlist proposal generated. " + String.join(" ", auditMessages);
    }

    private String statusSummary(ConversationSessionStateResponse state) {
        StringBuilder summary = new StringBuilder("Session state: ")
                .append(state.getState().getValue().toLowerCase(Locale.ROOT))
                .append(".");
        if (state.getRecommendationResultId() != null && !state.getRecommendationResultId().isBlank()) {
            summary.append(" Recommendation: ").append(state.getRecommendationResultId()).append(".");
        }
        if (state.getSetlistId() != null && !state.getSetlistId().isBlank()) {
            summary.append(" Setlist: ").append(state.getSetlistId()).append(".");
        }
        if (state.getSetlistVersionId() != null && !state.getSetlistVersionId().isBlank()) {
            summary.append(" Version: ").append(state.getSetlistVersionId()).append(".");
        }
        return summary.toString();
    }

    private TelegramSessionState currentState(TelegramAuthorizationService.TelegramAuthorizationDecision decision) {
        return decision.session() == null ? TelegramSessionState.IDLE : decision.session().state();
    }

    private void touch(TelegramAuthorizationService.TelegramAuthorizationDecision decision, TelegramChannelEvent event, TelegramSessionState state) {
        facade.recordChannelCorrelation(
                sessionId(event),
                "telegram",
                String.valueOf(event.updateId()),
                event.correlationId());
        if (authorizationService != null) {
            authorizationService.touch(decision, event, state);
        }
    }

    private TelegramAuthorizationService.TelegramAuthorizationDecision authorize(TelegramChannelEvent event, TelegramProtectedAction action) {
        if (authorizationService == null) {
            return new TelegramAuthorizationService.TelegramAuthorizationDecision(true, TelegramIdentityStatus.LINKED, "Authorized.", null, null);
        }
        return authorizationService.authorize(event, action);
    }

    private TelegramAdapterResponse denied(TelegramChannelEvent event, TelegramAuthorizationService.TelegramAuthorizationDecision decision) {
        TelegramAdapterResponseStatus status = decision.status() == TelegramIdentityStatus.DISABLED
                ? TelegramAdapterResponseStatus.DISABLED
                : TelegramAdapterResponseStatus.UNAUTHORIZED;
        return new TelegramAdapterResponse(status, decision.safeResponse(), event, null);
    }

    private UUID sessionId(TelegramChannelEvent event) {
        return UUID.nameUUIDFromBytes(("telegram:" + event.chatId() + ":" + event.userId()).getBytes(StandardCharsets.UTF_8));
    }
}
