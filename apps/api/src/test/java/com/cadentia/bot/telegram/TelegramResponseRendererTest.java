package com.cadentia.bot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.generated.model.RecommendationExplanation;
import com.cadentia.generated.model.RecommendationExplanationCode;
import com.cadentia.generated.model.RecommendationExplanationEntry;
import com.cadentia.generated.model.RecommendationExplanationEvidence;
import com.cadentia.generated.model.RecommendationExplanationScope;
import com.cadentia.generated.model.RecommendationExplanationSubject;
import com.cadentia.generated.model.SetlistProposalResponse;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class TelegramResponseRendererTest {
    private final TelegramResponseRenderer renderer = new TelegramResponseRenderer();

    @Test
    void rendersSupportedCommandOutcomesWithSafeEscapingAndGuidance() {
        TelegramAdapterResponse response = new TelegramAdapterResponse(
                TelegramAdapterResponseStatus.STARTED,
                "Welcome <leader> token=123 secret=abc",
                event(TelegramEventKind.MESSAGE),
                null);

        List<TelegramRenderedMessage> rendered = renderer.render(response);

        assertThat(rendered).hasSize(1);
        assertThat(rendered.get(0).text())
                .contains("&lt;leader&gt;")
                .contains("token=[redacted]")
                .contains("secret=[redacted]")
                .contains("/newsetlist");
        assertThat(rendered.get(0).inlineKeyboard().rows()).hasSize(2);
    }

    @Test
    void rendersClarificationValidationCancellationAndOperationalFailures() {
        assertThat(renderer.render(new TelegramAdapterResponse(TelegramAdapterResponseStatus.CONTINUED,
                "Please clarify scripture.", event(TelegramEventKind.MESSAGE), "verseText")).get(0).text())
                .contains("Cadentia update")
                .contains("Please clarify scripture.");
        assertThat(renderer.render(new TelegramAdapterResponse(TelegramAdapterResponseStatus.INVALID,
                "raw prompt text: should not leak", event(TelegramEventKind.MESSAGE), null)).get(0).text())
                .doesNotContain("should not leak")
                .contains("Could not process");
        assertThat(renderer.render(new TelegramAdapterResponse(TelegramAdapterResponseStatus.CANCELLED,
                "Session cancelled.", event(TelegramEventKind.MESSAGE), null)).get(0).text())
                .contains("Cancelled")
                .contains("/newsetlist");
        assertThat(renderer.render(new TelegramAdapterResponse(TelegramAdapterResponseStatus.DISABLED,
                "Channel disabled.", event(TelegramEventKind.MESSAGE), null)).get(0).text())
                .contains("Unavailable");
    }

    @Test
    void callbackResponsesIncludeAcknowledgementBeforeMessage() {
        TelegramChannelEvent event = event(TelegramEventKind.CALLBACK_QUERY);
        TelegramAdapterResponse response = new TelegramAdapterResponse(TelegramAdapterResponseStatus.COMPLETED,
                "Proposal generated.", event, "confirmation");

        List<TelegramRenderedMessage> rendered = renderer.render(response);

        assertThat(rendered).hasSize(2);
        assertThat(rendered.get(0).callbackQueryId()).isEqualTo("cb-1");
        assertThat(rendered.get(0).callbackAcknowledgement()).isEqualTo("Proposal generated.");
        assertThat(rendered.get(1).text()).contains("Setlist ready");
    }

    @Test
    void setlistProposalRendersApprovedSongReferencesWithoutUnapprovedDetails() {
        RecommendationExplanationEntry selected = new RecommendationExplanationEntry(
                RecommendationExplanationCode.APPROVAL_ELIGIBLE,
                RecommendationExplanationEntry.SeverityEnum.INFO,
                RecommendationExplanationScope.SELECTED_SONG,
                RecommendationExplanationEntry.AudienceEnum.PUBLIC,
                new RecommendationExplanationSubject(RecommendationExplanationSubject.TypeEnum.SONG, "song-1"),
                "selected.approved",
                Map.of(),
                List.of(
                        new RecommendationExplanationEvidence(RecommendationExplanationEvidence.TypeEnum.CATALOG, "catalog:song-1"),
                        new RecommendationExplanationEvidence(RecommendationExplanationEvidence.TypeEnum.APPROVAL, "approval:approved-1"),
                        new RecommendationExplanationEvidence(RecommendationExplanationEvidence.TypeEnum.SCORE, "hidden-score")))
                .defaultText("Holy <Song> fits the approved thanksgiving theme.");
        SetlistProposalResponse proposal = new SetlistProposalResponse()
                .status("PROPOSED")
                .recommendationResultId("rec-1")
                .auditMessages(List.of("Approved-only policy applied.", "unapproved candidate Foo excluded."))
                .explanation(new RecommendationExplanation().selectedSongs(List.of(selected)));

        List<TelegramRenderedMessage> rendered = renderer.renderProposal("42", proposal);

        assertThat(rendered).hasSize(1);
        assertThat(rendered.get(0).text())
                .contains("Setlist proposal")
                .contains("Holy &lt;Song&gt;")
                .contains("catalog:song-1")
                .contains("approval:approved-1")
                .doesNotContain("hidden-score")
                .doesNotContain("Foo");
    }

    @Test
    void splitsLongMessagesDeterministicallyWithinTelegramLimit() {
        String longMessage = "Line\n".repeat(1000);

        List<String> chunks = renderer.split(longMessage);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allMatch(chunk -> chunk.length() <= TelegramResponseRenderer.SAFE_MESSAGE_LIMIT);
        assertThat(String.join("\n", chunks)).contains("Line");
    }

    private static TelegramChannelEvent event(TelegramEventKind kind) {
        return new TelegramChannelEvent(10L, kind, "42", "99", 7, "/start", TelegramCommand.START,
                kind == TelegramEventKind.CALLBACK_QUERY ? TelegramCallbackAction.CONFIRM : null,
                "accepted", kind == TelegramEventKind.CALLBACK_QUERY ? "cb-1" : null, 7, Locale.ROOT, "corr-1");
    }
}
