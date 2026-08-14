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
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TelegramResponseRendererTest {
    private final TelegramResponseRenderer renderer = new TelegramResponseRenderer();

    @Test
    void rendersSupportedCommandOutcomesWithSafeEscapingAndGuidance() {
        // Arrange
        TelegramAdapterResponse response = new TelegramAdapterResponse(
                TelegramAdapterResponseStatus.STARTED,
                "Welcome <leader> token=123 secret=abc",
                event(TelegramEventKind.MESSAGE),
                null);

        // Act
        List<TelegramRenderedMessage> rendered = renderer.render(response);

        // Assert
        assertThat(rendered).hasSize(1);
        assertThat(rendered.get(0).text())
                .contains("&lt;leader&gt;")
                .contains("token=[redacted]")
                .contains("secret=[redacted]")
                .contains("/newsetlist");
        assertThat(rendered.get(0).inlineKeyboard().rows()).hasSize(1);
        assertThat(rendered.get(0).inlineKeyboard().rows().get(0))
                .extracting(TelegramRenderedMessage.TelegramInlineKeyboardButton::callbackData)
                .allSatisfy(callbackData -> {
                    assertThat(callbackData).startsWith("cad:v1:");
                    assertThat(callbackData.length()).isLessThanOrEqualTo(TelegramCallbackData.LIMIT);
                })
                .contains(
                        "cad:v1:shape_counts:3p2w",
                        "cad:v1:shape_counts:4p2w",
                        "cad:v1:shape_counts:10p5w");
    }

    @Test
    void stagesKeyboardProgressivelyAsSlotsAreFilled() {
        // Arrange: no slots -> counts stage
        TelegramAdapterResponse empty = new TelegramAdapterResponse(
                TelegramAdapterResponseStatus.CONTINUED, "Step 1.", event(TelegramEventKind.MESSAGE), null,
                new com.cadentia.generated.model.GenerateSetlistRequest());

        // Act / Assert: counts stage only
        List<TelegramRenderedMessage> renderedEmpty = renderer.render(empty);
        assertThat(renderedEmpty.get(0).inlineKeyboard().rows()).hasSize(1);
        assertThat(renderedEmpty.get(0).inlineKeyboard().rows().get(0).get(0).text()).isEqualTo("3+2");

        // Arrange: counts filled -> language stage
        TelegramAdapterResponse withCounts = new TelegramAdapterResponse(
                TelegramAdapterResponseStatus.CONTINUED, "Step 2.", event(TelegramEventKind.MESSAGE), null,
                new com.cadentia.generated.model.GenerateSetlistRequest().counts(new com.cadentia.generated.model.SetlistCounts().praise(3).worship(2)));

        // Act / Assert: language stage only
        List<TelegramRenderedMessage> renderedCounts = renderer.render(withCounts);
        assertThat(renderedCounts.get(0).inlineKeyboard().rows()).hasSize(1);
        assertThat(renderedCounts.get(0).inlineKeyboard().rows().get(0).get(0).text()).isEqualTo("English");
        assertThat(renderedCounts.get(0).text()).contains("Selected so far:").contains("Structure: 3 praise + 2 worship");

        // Arrange: counts + language + energyArc + serviceMoment + keyPolicy filled -> tempo stage
        TelegramAdapterResponse withKey = new TelegramAdapterResponse(
                TelegramAdapterResponseStatus.CONTINUED, "Step 5.", event(TelegramEventKind.MESSAGE), null,
                new com.cadentia.generated.model.GenerateSetlistRequest()
                        .counts(new com.cadentia.generated.model.SetlistCounts().praise(4).worship(2))
                        .language("es")
                        .energyArc(com.cadentia.generated.model.GenerateSetlistRequest.EnergyArcEnum.RISING)
                        .serviceMoment(com.cadentia.generated.model.GenerateSetlistRequest.ServiceMomentEnum.OPENING)
                        .keyPolicy(new com.cadentia.generated.model.KeyPolicy(true, true, 2)));

        // Act / Assert: tempo stage only
        List<TelegramRenderedMessage> renderedKey = renderer.render(withKey);
        assertThat(renderedKey.get(0).inlineKeyboard().rows()).hasSize(1);
        assertThat(renderedKey.get(0).inlineKeyboard().rows().get(0).get(0).text()).isEqualTo("Smooth tempo");
        assertThat(renderedKey.get(0).text())
                .contains("Selected so far:")
                .contains("Structure: 4 praise + 2 worship")
                .contains("Language: Spanish")
                .contains("Energy arc: Rising")
                .contains("Service moment: Opening")
                .contains("Key policy: Tight keys");

        // Arrange: all slots filled -> confirm stage
        TelegramAdapterResponse ready = new TelegramAdapterResponse(
                TelegramAdapterResponseStatus.CONTINUED, "Ready.", event(TelegramEventKind.MESSAGE), null,
                new com.cadentia.generated.model.GenerateSetlistRequest()
                        .counts(new com.cadentia.generated.model.SetlistCounts().praise(10).worship(5))
                        .language("en")
                        .energyArc(com.cadentia.generated.model.GenerateSetlistRequest.EnergyArcEnum.STEADY)
                        .serviceMoment(com.cadentia.generated.model.GenerateSetlistRequest.ServiceMomentEnum.RESPONSE)
                        .keyPolicy(new com.cadentia.generated.model.KeyPolicy(false, true, 4))
                        .tempoPolicy(new com.cadentia.generated.model.TempoPolicy(20)));

        // Act / Assert: confirm buttons shown
        List<TelegramRenderedMessage> renderedReady = renderer.render(ready);
        assertThat(renderedReady.get(0).inlineKeyboard().rows()).hasSize(2);
        assertThat(renderedReady.get(0).inlineKeyboard().rows().get(0))
                .extracting(TelegramRenderedMessage.TelegramInlineKeyboardButton::text)
                .contains("Confirm", "Revise");
        assertThat(renderedReady.get(0).text())
                .contains("Selected so far:")
                .contains("Structure: 10 praise + 5 worship")
                .contains("Language: English")
                .contains("Energy arc: Steady")
                .contains("Service moment: Response")
                .contains("Key policy: Flexible keys")
                .contains("Tempo policy: Open tempo");
    }

    @Test
    void rendersClarificationValidationCancellationAndOperationalFailures() {
        // Arrange / Act / Assert
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
        // Arrange
        TelegramChannelEvent event = event(TelegramEventKind.CALLBACK_QUERY);
        RecommendationExplanationEntry selected = selectedSong(
                "Approved <Song>",
                List.of(new RecommendationExplanationEvidence(
                        RecommendationExplanationEvidence.TypeEnum.CATALOG,
                        "catalog:song-1")));
        SetlistProposalResponse proposal = new SetlistProposalResponse()
                .status("PROPOSED")
                .recommendationResultId("rec-1")
                .explanation(new RecommendationExplanation()
                        .setlistId("setlist-123")
                        .setlistVersionId("version-123")
                        .selectedSongs(List.of(selected)));
        TelegramAdapterResponse response = new TelegramAdapterResponse(TelegramAdapterResponseStatus.COMPLETED,
                "Proposal generated.", event, "confirmation", null, proposal);

        // Act
        List<TelegramRenderedMessage> rendered = renderer.render(response);

        // Assert
        assertThat(rendered).hasSize(2);
        assertThat(rendered.get(0).callbackQueryId()).isEqualTo("cb-1");
        assertThat(rendered.get(0).callbackAcknowledgement()).isEqualTo("Proposal generated.");
        assertThat(rendered.get(1).text())
                .contains("Setlist proposal")
                .contains("setlist-123")
                .contains("version-123")
                .contains("Approved &lt;Song&gt;")
                .contains("catalog:song-1")
                .doesNotContain("Setlist ready");
    }

    @Test
    void setlistProposalRendersApprovedSongReferencesWithoutUnapprovedDetails() {
        // Arrange
        RecommendationExplanationEntry selected = selectedSong(
                "Holy <Song> fits the approved thanksgiving theme.",
                List.of(
                        new RecommendationExplanationEvidence(RecommendationExplanationEvidence.TypeEnum.CATALOG, "catalog:song-1"),
                        new RecommendationExplanationEvidence(RecommendationExplanationEvidence.TypeEnum.APPROVAL, "approval:approved-1"),
                        new RecommendationExplanationEvidence(RecommendationExplanationEvidence.TypeEnum.SCORE, "hidden-score")));
        SetlistProposalResponse proposal = new SetlistProposalResponse()
                .status("PROPOSED")
                .recommendationResultId("rec-1")
                .auditMessages(List.of("Approved-only policy applied.", "unapproved candidate Foo excluded."))
                .explanation(new RecommendationExplanation().selectedSongs(List.of(selected)));

        // Act
        List<TelegramRenderedMessage> rendered = renderer.renderProposal("42", proposal);

        // Assert
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
    void setlistProposalOmitsUnsafePublicEntriesAndEvidence() {
        // Arrange
        RecommendationExplanationEntry publicSelection = selectedSong(
                "Safe public song",
                List.of(
                        new RecommendationExplanationEvidence(RecommendationExplanationEvidence.TypeEnum.CATALOG, "catalog:safe-song"),
                        new RecommendationExplanationEvidence(RecommendationExplanationEvidence.TypeEnum.APPROVAL, "approval:safe-approval"),
                        new RecommendationExplanationEvidence(RecommendationExplanationEvidence.TypeEnum.CATALOG, "catalog:instance:other-tenant-song"),
                        new RecommendationExplanationEvidence(RecommendationExplanationEvidence.TypeEnum.PROVENANCE, "lyrics_document:private")));
        RecommendationExplanationEntry adminSelection = selectedSong(
                "Admin-only song",
                List.of(new RecommendationExplanationEvidence(RecommendationExplanationEvidence.TypeEnum.CATALOG, "catalog:admin-song")))
                .audience(RecommendationExplanationEntry.AudienceEnum.ADMIN);
        RecommendationExplanationEntry unsafeTextSelection = selectedSong(
                "raw lyrics: private line",
                List.of(new RecommendationExplanationEvidence(RecommendationExplanationEvidence.TypeEnum.CATALOG, "catalog:raw-lyrics-song")));
        SetlistProposalResponse proposal = new SetlistProposalResponse()
                .status("PROPOSED")
                .auditMessages(List.of(
                        "Approved-only policy applied.",
                        "raw lyric detail should not leak.",
                        "hidden scoring component should not leak."))
                .explanation(new RecommendationExplanation().selectedSongs(List.of(
                        publicSelection,
                        adminSelection,
                        unsafeTextSelection)));

        // Act
        String text = renderer.renderProposal("42", proposal).get(0).text();

        // Assert
        assertThat(text)
                .contains("Safe public song")
                .contains("catalog:safe-song")
                .contains("approval:safe-approval")
                .contains("Approved-only policy applied.")
                .doesNotContain("Admin-only song")
                .doesNotContain("raw lyrics")
                .doesNotContain("lyrics_document")
                .doesNotContain("other-tenant")
                .doesNotContain("hidden scoring");
    }

    @Test
    void emptyProposalRendersSafeNoApprovedSongsMessage() {
        // Arrange
        SetlistProposalResponse proposal = new SetlistProposalResponse()
                .status("NO_APPROVED_CANDIDATES")
                .auditMessages(List.of("No approved eligible catalog candidates matched the request; no songs were fabricated."))
                .explanation(new RecommendationExplanation().selectedSongs(List.of()));

        // Act
        String text = renderer.renderProposal("42", proposal).get(0).text();

        // Assert
        assertThat(text)
                .contains("No approved songs were returned yet.")
                .contains("no songs were fabricated")
                .doesNotContain("raw lyric");
    }

    @Test
    void longProposalMessagesSplitWithKeyboardOnlyOnFirstChunk() {
        // Arrange
        List<RecommendationExplanationEntry> selectedSongs = java.util.stream.IntStream.rangeClosed(1, 260)
                .mapToObj(index -> selectedSong(
                        "Approved catalog selection " + index + " with a long but safe title for Telegram rendering",
                        List.of(new RecommendationExplanationEvidence(
                                RecommendationExplanationEvidence.TypeEnum.CATALOG,
                                "catalog:song-" + index))))
                .toList();
        SetlistProposalResponse proposal = new SetlistProposalResponse()
                .status("PROPOSED")
                .recommendationResultId("rec-long")
                .explanation(new RecommendationExplanation().selectedSongs(selectedSongs));

        // Act
        List<TelegramRenderedMessage> rendered = renderer.renderProposal("42", proposal);

        // Assert
        assertThat(rendered).hasSizeGreaterThan(1);
        assertThat(rendered).allSatisfy(message ->
                assertThat(message.text().length()).isLessThanOrEqualTo(TelegramResponseRenderer.SAFE_MESSAGE_LIMIT));
        assertThat(rendered.get(0).inlineKeyboard()).isNotNull();
        assertThat(rendered.subList(1, rendered.size()))
                .allSatisfy(message -> assertThat(message.inlineKeyboard()).isNull());
    }

    @Test
    void splitsLongMessagesDeterministicallyWithinTelegramLimit() {
        // Arrange
        String longMessage = "Line\n".repeat(1000);

        // Act
        List<String> chunks = renderer.split(longMessage);

        // Assert
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allMatch(chunk -> chunk.length() <= TelegramResponseRenderer.SAFE_MESSAGE_LIMIT);
        assertThat(String.join("\n", chunks)).contains("Line");
    }

    private static TelegramChannelEvent event(TelegramEventKind kind) {
        return new TelegramChannelEvent(10L, kind, "42", "99", 7, "/start", TelegramCommand.START,
                kind == TelegramEventKind.CALLBACK_QUERY ? TelegramCallbackAction.CONFIRM : null,
                "accepted", kind == TelegramEventKind.CALLBACK_QUERY ? "cb-1" : null, 7, Locale.ROOT, "corr-1");
    }

    private static RecommendationExplanationEntry selectedSong(
            String defaultText,
            List<RecommendationExplanationEvidence> evidence) {
        return new RecommendationExplanationEntry(
                RecommendationExplanationCode.APPROVAL_ELIGIBLE,
                RecommendationExplanationEntry.SeverityEnum.INFO,
                RecommendationExplanationScope.SELECTED_SONG,
                RecommendationExplanationEntry.AudienceEnum.PUBLIC,
                new RecommendationExplanationSubject(RecommendationExplanationSubject.TypeEnum.SONG, "song-1"),
                "selected.approved",
                Map.of(),
                evidence)
                .defaultText(defaultText);
    }
}
