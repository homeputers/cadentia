package com.cadentia.bot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.api.controller.ConversationSessionFacade;
import com.cadentia.api.controller.ValidatedSetlistRequestMapper;
import com.cadentia.generated.model.ConversationSessionStateResponse;
import com.cadentia.generated.model.ConversationState;
import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.SetlistProposalResponse;
import com.cadentia.generated.model.SlotValueSource;
import com.cadentia.intent.Counts;
import com.cadentia.intent.DefaultSessionMergeService;
import com.cadentia.intent.GenerateSetlistIntent;
import com.cadentia.intent.GenerateSetlistSlots;
import com.cadentia.intent.IntentKeyPolicy;
import com.cadentia.intent.IntentTempoPolicy;
import com.cadentia.llm.IntentParseResult;
import com.cadentia.llm.IntentService;
import com.cadentia.reng.SetlistService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultTelegramConversationGatewayTest {

    @Test
    void telegramFreeTextAndHttpSessionFacadeProduceMatchingValidatedIntentAndRecommendationRequest() {
        // Arrange
        GenerateSetlistIntent normalizedIntent = normalizedIntent();
        StubIntentService intentService = new StubIntentService(normalizedIntent);
        ConversationSessionFacade facade = new ConversationSessionFacade(
                new DefaultSessionMergeService(),
                new ValidatedSetlistRequestMapper(),
                Duration.ofMinutes(30),
                Duration.ofHours(4),
                intentService);
        CapturingSetlistService setlistService = new CapturingSetlistService();
        DefaultTelegramConversationGateway gateway = new DefaultTelegramConversationGateway(facade, null, setlistService);
        TelegramChannelEvent actorEvent = event("Psalm 100 thanksgiving", null, null);
        UUID telegramSessionId = sessionId(actorEvent);
        UUID httpSessionId = UUID.randomUUID();

        // Act
        gateway.newSetlist(event("/newsetlist", TelegramCommand.NEW_SETLIST, null));
        TelegramAdapterResponse textResponse = gateway.text(actorEvent);
        ConversationSessionStateResponse telegramState = facade.get(telegramSessionId);
        ConversationSessionStateResponse httpState = facade.ingestFreeText(httpSessionId, "Psalm 100 thanksgiving");
        TelegramAdapterResponse confirmResponse = gateway.menuSelection(event(null, null, TelegramCallbackAction.CONFIRM));

        // Assert
        assertThat(textResponse.message()).contains("shared intent boundary");
        assertThat(telegramState.getState()).isEqualTo(ConversationState.READY_TO_CONFIRM);
        assertThat(httpState.getState()).isEqualTo(ConversationState.READY_TO_CONFIRM);
        assertThat(telegramState.getSlots()).usingRecursiveComparison().isEqualTo(httpState.getSlots());
        assertThat(telegramState.getSlots().getCounts()).usingRecursiveComparison().isEqualTo(httpState.getSlots().getCounts());
        assertThat(telegramState.getSlots().getKeyPolicy()).usingRecursiveComparison().isEqualTo(httpState.getSlots().getKeyPolicy());
        assertThat(telegramState.getSlots().getTempoPolicy()).usingRecursiveComparison().isEqualTo(httpState.getSlots().getTempoPolicy());
        assertThat(confirmResponse.status()).isEqualTo(TelegramAdapterResponseStatus.COMPLETED);
        assertThat(setlistService.lastRequest).usingRecursiveComparison().isEqualTo(telegramState.getSlots());
    }

    @Test
    void prematureTelegramConfirmDoesNotInvokeSetlistGeneration() {
        // Arrange
        ConversationSessionFacade facade = new ConversationSessionFacade(
                new DefaultSessionMergeService(),
                new ValidatedSetlistRequestMapper(),
                Duration.ofMinutes(30),
                Duration.ofHours(4),
                input -> IntentParseResult.accepted(normalizedIntent(), false));
        CapturingSetlistService setlistService = new CapturingSetlistService();
        DefaultTelegramConversationGateway gateway = new DefaultTelegramConversationGateway(facade, null, setlistService);

        // Act
        TelegramAdapterResponse confirmResponse = gateway.menuSelection(event(null, null, TelegramCallbackAction.CONFIRM));

        // Assert
        assertThat(confirmResponse.status()).isEqualTo(TelegramAdapterResponseStatus.CONTINUED);
        assertThat(confirmResponse.message()).contains("not ready to confirm");
        assertThat(setlistService.lastRequest).isNull();
    }

    @Test
    void guidedCallbacksPatchSupportedSharedSlots() {
        // Arrange
        ConversationSessionFacade facade = facade(input -> IntentParseResult.accepted(normalizedIntent(), false));
        DefaultTelegramConversationGateway gateway = new DefaultTelegramConversationGateway(facade, null, new CapturingSetlistService());
        TelegramChannelEvent actor = event(null, null, TelegramCallbackAction.SHAPE_COUNTS, "3p2w");

        // Act
        TelegramAdapterResponse countsResponse = gateway.menuSelection(actor);
        gateway.menuSelection(event(null, null, TelegramCallbackAction.LANGUAGE, "es"));
        gateway.menuSelection(event(null, null, TelegramCallbackAction.KEY_POLICY, "minimal"));
        gateway.menuSelection(event(null, null, TelegramCallbackAction.TEMPO_POLICY, "open"));
        gateway.menuSelection(event(null, null, TelegramCallbackAction.ENERGY_ARC, "steady"));
        gateway.menuSelection(event(null, null, TelegramCallbackAction.SERVICE_MOMENT, "response"));
        ConversationSessionStateResponse state = facade.get(sessionId(actor));

        // Assert
        assertThat(countsResponse.status()).isEqualTo(TelegramAdapterResponseStatus.CONTINUED);
        assertThat(state.getSlots().getCounts().getPraise()).isEqualTo(3);
        assertThat(state.getSlots().getCounts().getWorship()).isEqualTo(2);
        assertThat(state.getSlots().getLanguage()).isEqualTo("es");
        assertThat(state.getSlots().getKeyPolicy().getMaxKeyCenters()).isEqualTo(2);
        assertThat(state.getSlots().getTempoPolicy().getMaxJumpBpm()).isEqualTo(20);
        assertThat(state.getSlots().getEnergyArc()).isEqualTo(GenerateSetlistRequest.EnergyArcEnum.STEADY);
        assertThat(state.getSlots().getServiceMoment()).isEqualTo(GenerateSetlistRequest.ServiceMomentEnum.RESPONSE);
        assertThat(state.getSlotSources()).anySatisfy(source -> {
            assertThat(source.getSlot().getValue()).isEqualTo("counts");
            assertThat(source.getSource()).isEqualTo(SlotValueSource.MENU);
        });
    }

    @Test
    void invalidGuidedCallbackValuesFailSafely() {
        // Arrange
        ConversationSessionFacade facade = facade(input -> IntentParseResult.accepted(normalizedIntent(), false));
        DefaultTelegramConversationGateway gateway = new DefaultTelegramConversationGateway(facade, null, new CapturingSetlistService());

        // Act
        TelegramAdapterResponse response = gateway.menuSelection(event(null, null, TelegramCallbackAction.SHAPE_COUNTS, "99p2w"));

        // Assert
        assertThat(response.status()).isEqualTo(TelegramAdapterResponseStatus.INVALID);
        assertThat(response.message()).contains("Unsupported guided menu selection");
    }

    @Test
    void menuSelectionsOverrideFreeTextValuesAndRequireReconfirmation() {
        // Arrange
        ConversationSessionFacade facade = facade(input -> IntentParseResult.accepted(normalizedIntent(), false));
        CapturingSetlistService setlistService = new CapturingSetlistService();
        DefaultTelegramConversationGateway gateway = new DefaultTelegramConversationGateway(facade, null, setlistService);
        TelegramChannelEvent actor = event("Psalm 100 thanksgiving", null, null);

        // Act
        gateway.text(actor);
        gateway.menuSelection(event(null, null, TelegramCallbackAction.SHAPE_COUNTS, "4p2w"));
        ConversationSessionStateResponse revised = facade.get(sessionId(actor));
        TelegramAdapterResponse confirm = gateway.menuSelection(event(null, null, TelegramCallbackAction.CONFIRM));

        // Assert
        assertThat(revised.getState()).isEqualTo(ConversationState.COLLECTING);
        assertThat(revised.getSlots().getCounts().getPraise()).isEqualTo(4);
        assertThat(revised.getSlots().getCounts().getWorship()).isEqualTo(2);
        assertThat(revised.getSlotSources()).anySatisfy(source -> {
            assertThat(source.getSlot().getValue()).isEqualTo("counts");
            assertThat(source.getSource()).isEqualTo(SlotValueSource.MENU);
        });
        assertThat(confirm.status()).isEqualTo(TelegramAdapterResponseStatus.CONTINUED);
        assertThat(setlistService.lastRequest).isNull();
    }

    private static GenerateSetlistIntent normalizedIntent() {
        return new GenerateSetlistIntent("v1", new GenerateSetlistSlots(
                "Psalm 100",
                List.of("Psalm 100:1-5"),
                List.of("thanksgiving", "joy"),
                new Counts(2, 1),
                new IntentKeyPolicy(true, true, 2),
                new IntentTempoPolicy(12),
                "en",
                "rising",
                List.of("Excluded Song"),
                "opening"));
    }

    private static TelegramChannelEvent event(String text, TelegramCommand command, TelegramCallbackAction action) {
        return event(text, command, action, "accepted");
    }

    private static TelegramChannelEvent event(String text, TelegramCommand command, TelegramCallbackAction action, String value) {
        return new TelegramChannelEvent(11L, action == null ? TelegramEventKind.MESSAGE : TelegramEventKind.CALLBACK_QUERY,
                "42", "99", 7, text, command, action, value, "cb-1", 7, Locale.ROOT, "corr");
    }

    private static ConversationSessionFacade facade(IntentService intentService) {
        return new ConversationSessionFacade(
                new DefaultSessionMergeService(),
                new ValidatedSetlistRequestMapper(),
                Duration.ofMinutes(30),
                Duration.ofHours(4),
                intentService);
    }

    private static UUID sessionId(TelegramChannelEvent event) {
        return UUID.nameUUIDFromBytes(("telegram:" + event.chatId() + ":" + event.userId()).getBytes(StandardCharsets.UTF_8));
    }

    private record StubIntentService(GenerateSetlistIntent intent) implements IntentService {
        @Override
        public IntentParseResult parse(String input) {
            return IntentParseResult.accepted(intent, false);
        }
    }

    private static class CapturingSetlistService extends SetlistService {
        private GenerateSetlistRequest lastRequest;

        @Override
        public SetlistProposalResponse generate(GenerateSetlistRequest request) {
            lastRequest = request;
            return new SetlistProposalResponse().status("PROPOSED").auditMessages(List.of(
                    "Candidate eligibility used approved catalog snapshot fixture.",
                    "Recommendation ordering and explanation references are deterministic."));
        }
    }
}
