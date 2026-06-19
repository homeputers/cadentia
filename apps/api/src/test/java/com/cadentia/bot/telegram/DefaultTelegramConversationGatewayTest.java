package com.cadentia.bot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.api.controller.ConversationSessionFacade;
import com.cadentia.api.controller.ValidatedSetlistRequestMapper;
import com.cadentia.generated.model.ConversationSessionStateResponse;
import com.cadentia.generated.model.ConversationState;
import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.SetlistProposalResponse;
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
        return new TelegramChannelEvent(11L, action == null ? TelegramEventKind.MESSAGE : TelegramEventKind.CALLBACK_QUERY,
                "42", "99", 7, text, command, action, "accepted", "cb-1", 7, Locale.ROOT, "corr");
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
