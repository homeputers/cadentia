package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cadentia.bot.telegram.DefaultTelegramConversationGateway;
import com.cadentia.bot.telegram.InMemoryTelegramWebhookIdempotencyStore;
import com.cadentia.bot.telegram.TelegramAuthorizationService;
import com.cadentia.bot.telegram.TelegramBotAdapter;
import com.cadentia.bot.telegram.TelegramBotSession;
import com.cadentia.bot.telegram.TelegramBotSessionRepository;
import com.cadentia.bot.telegram.TelegramClientException;
import com.cadentia.bot.telegram.TelegramIdentifierHasher;
import com.cadentia.bot.telegram.TelegramIdentityRepository;
import com.cadentia.bot.telegram.TelegramIdentityStatus;
import com.cadentia.bot.telegram.TelegramLinkedActor;
import com.cadentia.bot.telegram.TelegramOperationalControlService;
import com.cadentia.bot.telegram.TelegramOutboundClient;
import com.cadentia.bot.telegram.TelegramOutboundModels.FailureCategory;
import com.cadentia.bot.telegram.TelegramOutboundModels.OutboundStatus;
import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramDeadLetterRecord;
import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramOutboundSendRecord;
import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramSendResult;
import com.cadentia.bot.telegram.TelegramOutboundRepository;
import com.cadentia.bot.telegram.TelegramOutboundSendService;
import com.cadentia.bot.telegram.TelegramRenderedMessage;
import com.cadentia.bot.telegram.TelegramResponseRenderer;
import com.cadentia.bot.telegram.TelegramSecretResolver;
import com.cadentia.bot.telegram.TelegramSessionState;
import com.cadentia.bot.telegram.TelegramWebhookAuthenticationFilter;
import com.cadentia.bot.telegram.TelegramWebhookProperties;
import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.model.TagType;
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
import com.cadentia.reng.ApprovalGateSummary;
import com.cadentia.reng.CandidateRetriever;
import com.cadentia.reng.CandidateSearchCriteria;
import com.cadentia.reng.RecommendableArrangement;
import com.cadentia.reng.RecommendationTag;
import com.cadentia.reng.SetlistService;
import com.cadentia.runtime.InstanceConfiguration;
import com.cadentia.runtime.StaticInstanceConfigurationProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TelegramWebhookE2eEquivalenceTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String BOT_ID = "bot-e2e";
    private static final String CHAT_ID = "42001";
    private static final String USER_ID = "99001";
    private static final String WEBHOOK_SECRET = "current-secret";
    private static final String INSTANCE_ID = "local-development";

    @Test
    void telegramWebhookNewSetlistMatchesHttpProposalForSameNormalizedRequest() throws Exception {
        // Arrange
        FixtureHarness harness = FixtureHarness.linked(true);

        // Act
        harness.post(messageUpdate(9101, "/newsetlist"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        harness.post(messageUpdate(9102, "Psalm 100 thanksgiving and joy"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        ConversationSessionStateResponse ready = harness.facade.get(sessionId());
        harness.post(callbackUpdate(9103, "callback-confirm-9103", "cad:v1:confirm", Instant.now()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        SetlistProposalResponse telegramProposal = harness.setlistService.lastProposal;
        SetlistProposalResponse httpProposal = new SetlistController(
                harness.setlistService,
                null,
                null,
                null,
                null,
                null)
                .generateSetlistProposal(harness.setlistService.lastRequest)
                .getBody();

        // Assert
        assertThat(ready.getState()).isEqualTo(ConversationState.READY_TO_CONFIRM);
        assertThat(harness.setlistService.lastRequest).usingRecursiveComparison().isEqualTo(ready.getSlots());
        assertThat(harness.candidateRetriever.criteria)
                .extracting(CandidateSearchCriteria::requiredApprovalStatus)
                .containsOnly(ApprovalStatus.APPROVED);
        assertThat(telegramProposal.getStatus()).isEqualTo(httpProposal.getStatus());
        assertThat(telegramProposal.getRecommendationResultId()).isEqualTo(httpProposal.getRecommendationResultId());
        assertThat(selectedTitles(telegramProposal)).containsExactlyElementsOf(selectedTitles(httpProposal));
        assertThat(selectedEvidenceRefs(telegramProposal)).containsExactlyElementsOf(selectedEvidenceRefs(httpProposal));
        assertThat(harness.client.sent).hasSize(4);
        TelegramRenderedMessage renderedProposal = harness.client.sent.get(3);
        assertThat(renderedProposal.text())
                .contains("Setlist proposal")
                .contains("Living Thanksgiving")
                .contains("Quiet Response")
                .contains("catalog:arrangement:" + id("arrangement-1"))
                .contains("approval:approval_gate_summary")
                .doesNotContain("raw lyric")
                .doesNotContain("unapproved");
    }

    @Test
    void duplicateWebhookUpdateDoesNotRepeatOutboundSideEffects() throws Exception {
        // Arrange
        FixtureHarness harness = FixtureHarness.linked(true);
        String update = messageUpdate(9201, "/newsetlist");

        // Act / Assert
        harness.post(update)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        harness.post(update)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("DUPLICATE_ACCEPTED"));
        assertThat(harness.client.sent).hasSize(1);
    }

    @Test
    void webhookSecretAuthorizationAndChannelAuthorizationFailSafely() throws Exception {
        // Arrange
        FixtureHarness linked = FixtureHarness.linked(true);
        FixtureHarness unlinked = FixtureHarness.linked(false);
        FixtureHarness disabled = FixtureHarness.disabled();

        // Act / Assert
        linked.post(messageUpdate(9301, "/newsetlist"), "wrong-secret")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Invalid Telegram secret-token header."));
        unlinked.post(messageUpdate(9302, "/newsetlist"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        disabled.post(messageUpdate(9303, "/newsetlist"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        assertThat(linked.client.sent).isEmpty();
        assertThat(unlinked.client.sent).hasSize(1);
        assertThat(unlinked.client.sent.get(0).text()).contains("Access needed");
        assertThat(disabled.client.sent).hasSize(1);
        assertThat(disabled.client.sent.get(0).text()).contains("Unavailable");
    }

    @Test
    void staleCallbackCancellationRetryAndDeadLetterPathsAreCoveredByFixtures() throws Exception {
        // Arrange
        FixtureHarness harness = FixtureHarness.linked(true);
        FixtureHarness retrying = FixtureHarness.linked(true);
        FixtureHarness deadLettering = FixtureHarness.linked(true);
        retrying.client.failures.add(new TelegramClientException(502, "Telegram unavailable token=secret"));
        deadLettering.client.failures.add(new TelegramClientException(403, "bot was blocked by the user prompt text: private"));

        // Act
        harness.post(callbackUpdate(9401, "callback-stale-9401", "cad:v1:confirm", Instant.now().minus(Duration.ofHours(1))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        harness.post(messageUpdate(9402, "/newsetlist"))
                .andExpect(status().isAccepted());
        harness.post(callbackUpdate(9403, "callback-cancel-9403", "cad:v1:cancel", Instant.now()))
                .andExpect(status().isAccepted());
        retrying.post(messageUpdate(9404, "/newsetlist"))
                .andExpect(status().isAccepted());
        deadLettering.post(messageUpdate(9405, "/newsetlist"))
                .andExpect(status().isAccepted());

        // Assert
        assertThat(harness.client.sent).hasSize(3);
        assertThat(harness.client.sent.get(1).callbackAcknowledgement()).isEqualTo("Cancelled.");
        assertThat(harness.client.sent.get(2).text()).contains("Cancelled");
        assertThat(retrying.outboundRepository.records())
                .extracting(TelegramOutboundSendRecord::status)
                .containsExactly(OutboundStatus.RETRY_SCHEDULED);
        assertThat(deadLettering.outboundRepository.deadLetters())
                .extracting(TelegramDeadLetterRecord::failureCategory)
                .containsExactly(FailureCategory.CHAT_BLOCKED);
    }

    private static List<String> selectedTitles(SetlistProposalResponse proposal) {
        return proposal.getExplanation().getSelectedSongs().stream()
                .map(entry -> entry.getDefaultText())
                .toList();
    }

    private static List<List<String>> selectedEvidenceRefs(SetlistProposalResponse proposal) {
        return proposal.getExplanation().getSelectedSongs().stream()
                .map(entry -> entry.getEvidence().stream()
                        .map(evidence -> evidence.getType().getValue() + ":" + evidence.getRef())
                        .toList())
                .toList();
    }

    private static String messageUpdate(long updateId, String text) {
        return """
                {
                  "update_id": %d,
                  "message": {
                    "message_id": %d,
                    "date": %d,
                    "chat": {"id": %s, "type": "private"},
                    "from": {"id": %s, "is_bot": false, "language_code": "en"},
                    "text": "%s"
                  }
                }
                """.formatted(updateId, updateId, Instant.now().getEpochSecond(), CHAT_ID, USER_ID, text);
    }

    private static String callbackUpdate(long updateId, String callbackId, String data, Instant messageDate) {
        return """
                {
                  "update_id": %d,
                  "callback_query": {
                    "id": "%s",
                    "from": {"id": %s, "is_bot": false, "language_code": "en"},
                    "message": {
                      "message_id": %d,
                      "date": %d,
                      "chat": {"id": %s, "type": "private"},
                      "text": "Synthetic callback"
                    },
                    "data": "%s"
                  }
                }
                """.formatted(updateId, callbackId, USER_ID, updateId, messageDate.getEpochSecond(), CHAT_ID, data);
    }

    private static UUID sessionId() {
        return UUID.nameUUIDFromBytes(("telegram:" + CHAT_ID + ":" + USER_ID).getBytes(StandardCharsets.UTF_8));
    }

    private static GenerateSetlistIntent normalizedIntent() {
        return new GenerateSetlistIntent(
                "v1",
                new GenerateSetlistSlots(
                        "Psalm 100",
                        List.of("Psalm 100:1-5"),
                        List.of("thanksgiving", "joy"),
                        new Counts(2, 1),
                        new IntentKeyPolicy(true, true, 2),
                        new IntentTempoPolicy(12),
                        "en",
                        "rising",
                        List.of(),
                        "opening"));
    }

    private static InstanceConfiguration configuration() {
        return InstanceConfiguration.localDevelopment(
                INSTANCE_ID,
                "local",
                "cadentia-local-assets",
                INSTANCE_ID,
                "env:CADENTIA_LOCAL_ASSET_KEY_REF",
                "cadentia:local:development",
                "local.development",
                List.of("local.development.audit-events", "local.development.recommendation-events"));
    }

    private static RecommendableArrangement candidate(
            String suffix,
            String title,
            String role,
            int bpm,
            String themeSlug,
            String scriptureSlug) {
        RecommendationTag theme = new RecommendationTag(id("tag-theme-" + suffix), TagType.THEME, themeSlug, themeSlug);
        RecommendationTag scripture = new RecommendationTag(id("tag-scripture-" + suffix), TagType.SCRIPTURE, scriptureSlug, scriptureSlug);
        return new RecommendableArrangement(
                id("arrangement-" + suffix),
                id("song-" + suffix),
                id("lyrics-" + suffix),
                title,
                "en",
                "G",
                KeyMode.MAJOR,
                bpm,
                "4/4",
                75,
                List.of(role),
                List.of(theme, scripture),
                List.of(theme, scripture),
                approvedSummary());
    }

    private static ApprovalGateSummary approvedSummary() {
        return new ApprovalGateSummary(
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED);
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static class FixtureHarness {
        private final ConversationSessionFacade facade = new ConversationSessionFacade(
                new DefaultSessionMergeService(),
                new ValidatedSetlistRequestMapper(),
                Duration.ofMinutes(30),
                Duration.ofHours(4),
                input -> IntentParseResult.accepted(normalizedIntent(), false));
        private final CapturingCandidateRetriever candidateRetriever = new CapturingCandidateRetriever(List.of(
                candidate("1", "Living Thanksgiving", "praise", 82, "thanksgiving", "psalm-100"),
                candidate("2", "Quiet Response", "worship", 68, "thanksgiving", "psalm-100"),
                candidate("3", "Joyful Sending", "praise", 74, "joy", "psalm-100")));
        private final CapturingSetlistService setlistService = new CapturingSetlistService(candidateRetriever);
        private final ScriptedTelegramClient client = new ScriptedTelegramClient();
        private final CapturingOutboundRepository outboundRepository = new CapturingOutboundRepository();
        private final MockMvc mockMvc;

        private FixtureHarness(boolean linked, boolean channelEnabled) {
            TelegramIdentifierHasher hasher = new TelegramIdentifierHasher("hash-secret");
            InMemoryIdentityRepository identityRepository = new InMemoryIdentityRepository();
            if (linked) {
                identityRepository.link(hasher);
            }
            TelegramAuthorizationService authorizationService = new TelegramAuthorizationService(
                    hasher,
                    identityRepository,
                    new InMemorySessionRepository(),
                    new StaticInstanceConfigurationProvider(configuration()),
                    Duration.ofMinutes(30),
                    Duration.ofHours(4));
            TelegramOperationalControlService controls = new TelegramOperationalControlService(channelEnabled, null);
            TelegramBotAdapter adapter = new TelegramBotAdapter(
                    OBJECT_MAPPER,
                    new DefaultTelegramConversationGateway(
                            facade,
                            authorizationService,
                            setlistService),
                    false,
                    Duration.ofMinutes(30),
                    null,
                    controls);
            TelegramWebhookProperties properties = new TelegramWebhookProperties();
            properties.setBotTokenRef("env:TELEGRAM_BOT_TOKEN");
            properties.setSecretTokenRef("env:CURRENT_TELEGRAM_SECRET");
            properties.setMaxUpdateAge(Duration.ofHours(24));
            MockEnvironment environment = new MockEnvironment()
                    .withProperty("TELEGRAM_BOT_TOKEN", "123456:bot-token")
                    .withProperty("CURRENT_TELEGRAM_SECRET", WEBHOOK_SECRET);
            TelegramWebhookProblemFactory problemFactory = new TelegramWebhookProblemFactory();
            TelegramWebhookController controller = new TelegramWebhookController(
                    properties,
                    new InMemoryTelegramWebhookIdempotencyStore(),
                    problemFactory,
                    java.time.Clock.systemUTC(),
                    adapter,
                    new TelegramResponseRenderer(),
                    new TelegramOutboundSendService(outboundRepository, client, hasher),
                    OBJECT_MAPPER);
            TelegramWebhookAuthenticationFilter filter = new TelegramWebhookAuthenticationFilter(
                    properties,
                    new TelegramSecretResolver(environment),
                    problemFactory,
                    OBJECT_MAPPER);
            mockMvc = MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(new TelegramWebhookExceptionHandler())
                    .addFilters(filter)
                    .build();
        }

        static FixtureHarness linked(boolean linked) {
            return new FixtureHarness(linked, true);
        }

        static FixtureHarness disabled() {
            return new FixtureHarness(true, false);
        }

        org.springframework.test.web.servlet.ResultActions post(String payload) throws Exception {
            return post(payload, WEBHOOK_SECRET);
        }

        org.springframework.test.web.servlet.ResultActions post(String payload, String secret) throws Exception {
            return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/telegram/webhooks/" + BOT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Telegram-Bot-Api-Secret-Token", secret)
                    .header("X-Request-ID", "req-" + BOT_ID)
                    .header("X-Correlation-ID", "corr-" + BOT_ID)
                    .content(payload));
        }
    }

    private static class CapturingSetlistService extends SetlistService {
        private GenerateSetlistRequest lastRequest;
        private SetlistProposalResponse lastProposal;

        private CapturingSetlistService(CandidateRetriever candidateRetriever) {
            super(candidateRetriever, null);
        }

        @Override
        public SetlistProposalResponse generate(GenerateSetlistRequest request) {
            lastRequest = request;
            lastProposal = super.generate(request);
            return lastProposal;
        }
    }

    private static class CapturingCandidateRetriever implements CandidateRetriever {
        private final List<RecommendableArrangement> candidates;
        private final List<CandidateSearchCriteria> criteria = new ArrayList<>();

        private CapturingCandidateRetriever(List<RecommendableArrangement> candidates) {
            this.candidates = candidates;
        }

        @Override
        public List<RecommendableArrangement> findCandidates(CandidateSearchCriteria criteria) {
            this.criteria.add(criteria);
            return candidates;
        }
    }

    private static class InMemoryIdentityRepository implements TelegramIdentityRepository {
        private final Map<String, TelegramLinkedActor> actors = new LinkedHashMap<>();

        private void link(TelegramIdentifierHasher hasher) {
            actors.put(
                    key(hasher.hash("telegram", CHAT_ID), hasher.hash("telegram", USER_ID)),
                    new TelegramLinkedActor(
                            id("actor-worship-leader"),
                            INSTANCE_ID,
                            Set.of("ROLE_WORSHIP_LEADER"),
                            TelegramIdentityStatus.LINKED));
        }

        @Override
        public Optional<TelegramLinkedActor> findByTelegramHashes(String channel, String chatHash, String userHash) {
            return Optional.ofNullable(actors.get(key(chatHash, userHash)));
        }

        private String key(String chatHash, String userHash) {
            return chatHash + ":" + userHash;
        }
    }

    private static class InMemorySessionRepository implements TelegramBotSessionRepository {
        private final Map<UUID, TelegramBotSession> sessions = new LinkedHashMap<>();

        @Override
        public Optional<TelegramBotSession> findActive(String channel, String chatHash, String userHash) {
            return sessions.values().stream()
                    .filter(session -> session.channel().equals(channel))
                    .filter(session -> session.chatHash().equals(chatHash))
                    .filter(session -> session.userHash().equals(userHash))
                    .filter(session -> session.state() != TelegramSessionState.CANCELLED)
                    .filter(session -> session.state() != TelegramSessionState.EXPIRED)
                    .findFirst();
        }

        @Override
        public TelegramBotSession save(TelegramBotSession session) {
            sessions.put(session.id(), session);
            return session;
        }

        @Override
        public void transition(UUID sessionId, TelegramSessionState state) {
            TelegramBotSession session = sessions.get(sessionId);
            sessions.put(sessionId, new TelegramBotSession(
                    session.id(),
                    session.channel(),
                    session.chatHash(),
                    session.userHash(),
                    session.churchInstanceId(),
                    session.actorId(),
                    state,
                    session.pendingConfirmationRef(),
                    session.lastUpdateId(),
                    session.lastMessageId(),
                    session.createdAt(),
                    session.updatedAt(),
                    session.inactivityDeadline(),
                    session.absoluteExpiration(),
                    session.auditMetadataJson()));
        }
    }

    private static class CapturingOutboundRepository implements TelegramOutboundRepository {
        private final Map<String, TelegramOutboundSendRecord> records = new LinkedHashMap<>();
        private final List<TelegramDeadLetterRecord> deadLetters = new ArrayList<>();

        @Override
        public TelegramOutboundSendRecord createIfAbsent(TelegramOutboundSendRecord record) {
            return records.computeIfAbsent(record.idempotencyKey(), ignored -> record);
        }

        @Override
        public Optional<TelegramOutboundSendRecord> findByIdempotencyKey(String idempotencyKey) {
            return Optional.ofNullable(records.get(idempotencyKey));
        }

        @Override
        public TelegramOutboundSendRecord markSent(String idempotencyKey, String telegramMessageId, Instant now) {
            TelegramOutboundSendRecord current = records.get(idempotencyKey);
            TelegramOutboundSendRecord updated = copy(current, OutboundStatus.SENT, current.attempts() + 1, null, telegramMessageId, null, null, now);
            records.put(idempotencyKey, updated);
            return updated;
        }

        @Override
        public TelegramOutboundSendRecord markRetry(
                TelegramOutboundSendRecord record,
                String category,
                String sanitizedDetail,
                Instant retryAt,
                Instant now) {
            TelegramOutboundSendRecord updated = copy(
                    record,
                    OutboundStatus.RETRY_SCHEDULED,
                    record.attempts() + 1,
                    retryAt,
                    null,
                    FailureCategory.valueOf(category),
                    sanitizedDetail,
                    now);
            records.put(record.idempotencyKey(), updated);
            return updated;
        }

        @Override
        public TelegramDeadLetterRecord deadLetter(
                TelegramOutboundSendRecord record,
                String category,
                String sanitizedDetail,
                Instant now) {
            TelegramOutboundSendRecord updated = copy(
                    record,
                    OutboundStatus.DEAD_LETTERED,
                    record.attempts() + 1,
                    null,
                    null,
                    FailureCategory.valueOf(category),
                    sanitizedDetail,
                    now);
            records.put(record.idempotencyKey(), updated);
            TelegramDeadLetterRecord deadLetter = new TelegramDeadLetterRecord(
                    id("dead-letter-" + deadLetters.size()),
                    record.id(),
                    record.idempotencyKey(),
                    record.correlationId(),
                    record.chatHash(),
                    record.operation(),
                    FailureCategory.valueOf(category),
                    sanitizedDetail,
                    record.sanitizedPreview(),
                    updated.attempts(),
                    now);
            deadLetters.add(deadLetter);
            return deadLetter;
        }

        @Override
        public List<TelegramDeadLetterRecord> deadLetters() {
            return List.copyOf(deadLetters);
        }

        private List<TelegramOutboundSendRecord> records() {
            return List.copyOf(records.values());
        }

        private TelegramOutboundSendRecord copy(
                TelegramOutboundSendRecord current,
                OutboundStatus status,
                int attempts,
                Instant retryAt,
                String telegramMessageId,
                FailureCategory category,
                String detail,
                Instant now) {
            return new TelegramOutboundSendRecord(
                    current.id(),
                    current.idempotencyKey(),
                    current.correlationId(),
                    current.chatHash(),
                    current.operation(),
                    current.sanitizedPreview(),
                    status,
                    attempts,
                    current.maxAttempts(),
                    retryAt,
                    telegramMessageId,
                    category,
                    detail,
                    current.createdAt(),
                    now);
        }
    }

    private static class ScriptedTelegramClient implements TelegramOutboundClient {
        private final List<RuntimeException> failures = new ArrayList<>();
        private final List<TelegramRenderedMessage> sent = new ArrayList<>();

        @Override
        public TelegramSendResult send(TelegramRenderedMessage message) {
            if (!failures.isEmpty()) {
                throw failures.remove(0);
            }
            sent.add(message);
            return TelegramSendResult.delivered("telegram-message-" + sent.size());
        }
    }
}
