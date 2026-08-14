package com.cadentia.bot.telegram;

import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.KeyPolicy;
import com.cadentia.generated.model.RecommendationExplanation;
import com.cadentia.generated.model.RecommendationExplanationEntry;
import com.cadentia.generated.model.RecommendationExplanationEvidence;
import com.cadentia.generated.model.RecommendationExplanationScope;
import com.cadentia.generated.model.SetlistCounts;
import com.cadentia.generated.model.SetlistProposalResponse;
import com.cadentia.generated.model.TempoPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TelegramResponseRenderer {
    static final int TELEGRAM_MESSAGE_LIMIT = 4096;
    static final int SAFE_MESSAGE_LIMIT = 3900;
    private static final int CALLBACK_LIMIT = 180;

    private static final Map<String, String> LANGUAGE_LABELS = Map.of("en", "English", "es", "Spanish", "pt", "Portuguese");
    private static final Map<String, String> ENERGY_ARC_LABELS = Map.of(
            "rising", "Rising", "steady", "Steady", "falling", "Falling", "low_to_high", "Low to high", "high_to_low", "High to low");
    private static final Map<String, String> SERVICE_MOMENT_LABELS = Map.of(
            "opening", "Opening", "communion", "Communion", "response", "Response", "altar_call", "Altar call", "sending", "Sending", "other", "Other");
    private static final Map<String, String> KEY_POLICY_LABELS = Map.of("minimal", "Tight keys", "same", "Same key", "flex", "Flexible keys");
    private static final Map<String, String> TEMPO_POLICY_LABELS = Map.of("tight", "Tight tempo", "smooth", "Smooth tempo", "open", "Open tempo");

    public List<TelegramRenderedMessage> render(TelegramAdapterResponse response) {
        if (response == null) {
            return List.of(TelegramRenderedMessage.message(null, "Cadentia could not process that update. Please try again.", null));
        }
        List<TelegramRenderedMessage> rendered = new ArrayList<>();
        TelegramChannelEvent event = response.event();
        if (event != null && event.callbackQueryId() != null) {
            rendered.add(TelegramRenderedMessage.callbackAck(event.callbackQueryId(), callbackAcknowledgement(response)));
        }
        if (response.status() == TelegramAdapterResponseStatus.COMPLETED && response.proposal() != null) {
            rendered.addAll(renderProposal(event == null ? null : event.chatId(), response.proposal()));
            return rendered;
        }
        if (shouldSuppressMessage(response)) {
            return rendered;
        }
        TelegramRenderedMessage.TelegramInlineKeyboard keyboard = keyboardFor(response);
        for (String chunk : split(renderBody(response))) {
            rendered.add(TelegramRenderedMessage.message(event == null ? null : event.chatId(), chunk, keyboard));
            keyboard = null;
        }
        return rendered;
    }

    public List<TelegramRenderedMessage> renderProposal(String chatId, SetlistProposalResponse proposal) {
        StringBuilder body = new StringBuilder();
        body.append("<b>Setlist proposal</b>\n");
        if (proposal == null) {
            body.append("No approved songs were returned yet. ");
            body.append("\nReview the proposal in Cadentia before publishing. Only approved catalog evidence is shown.");
            return List.of(TelegramRenderedMessage.message(chatId, body.toString(), completedKeyboard()));
        }
        if (StringUtils.hasText(proposal.getRecommendationResultId())) {
            body.append("Result: <code>").append(escape(proposal.getRecommendationResultId())).append("</code>\n");
        }
        RecommendationExplanation explanation = proposal.getExplanation();
        if (explanation != null && StringUtils.hasText(explanation.getSetlistId())) {
            body.append("Setlist: <code>").append(escape(explanation.getSetlistId())).append("</code>\n");
        }
        if (explanation != null && StringUtils.hasText(explanation.getSetlistVersionId())) {
            body.append("Version: <code>").append(escape(explanation.getSetlistVersionId())).append("</code>\n");
        }
        List<RecommendationExplanationEntry> selected = explanation == null || explanation.getSelectedSongs() == null
                ? List.of()
                : explanation.getSelectedSongs().stream()
                        .filter(this::publicSelectedSong)
                        .toList();
        if (selected.isEmpty()) {
            body.append("No approved songs were returned yet. ");
        } else {
            body.append("\n<b>Recommended songs</b>\n");
            int index = 1;
            for (RecommendationExplanationEntry entry : selected) {
                body.append(index++).append(". ").append(escape(summary(entry))).append("\n");
                conciseEvidence(entry).forEach(ref -> body.append("   • ref: <code>").append(escape(ref)).append("</code>\n"));
            }
        }
        appendAuditMessages(body, proposal.getAuditMessages());
        body.append("\nReview the proposal in Cadentia before publishing. Only approved catalog evidence is shown.");
        List<TelegramRenderedMessage> messages = new ArrayList<>();
        TelegramRenderedMessage.TelegramInlineKeyboard keyboard = completedKeyboard();
        for (String chunk : split(body.toString())) {
            messages.add(TelegramRenderedMessage.message(chatId, chunk, keyboard));
            keyboard = null;
        }
        return messages;
    }

    String escape(String value) {
        if (value == null) {
            return "";
        }
        return redact(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    List<String> split(String html) {
        if (html.length() <= SAFE_MESSAGE_LIMIT) {
            return List.of(html);
        }
        List<String> chunks = new ArrayList<>();
        String remaining = html;
        while (remaining.length() > SAFE_MESSAGE_LIMIT) {
            int boundary = remaining.lastIndexOf('\n', SAFE_MESSAGE_LIMIT);
            if (boundary < 500) {
                boundary = SAFE_MESSAGE_LIMIT;
            }
            chunks.add(remaining.substring(0, boundary));
            remaining = remaining.substring(boundary).stripLeading();
        }
        if (!remaining.isBlank()) {
            chunks.add(remaining);
        }
        return chunks;
    }

    private boolean shouldSuppressMessage(TelegramAdapterResponse response) {
        return response.status() == TelegramAdapterResponseStatus.STALE_CALLBACK
                || response.status() == TelegramAdapterResponseStatus.UNSUPPORTED && response.event() != null
                && response.event().kind() == TelegramEventKind.CALLBACK_QUERY;
    }

    private String renderBody(TelegramAdapterResponse response) {
        StringBuilder body = new StringBuilder();
        body.append(switch (response.status()) {
            case STARTED -> "<b>Cadentia</b>\n" + escape(response.message());
            case CONTINUED, ALREADY_ACTIVE -> "<b>Cadentia update</b>\n" + escape(response.message());
            case CANCELLED -> "<b>Cancelled</b>\n" + escape(response.message()) + " You can start again with /newsetlist.";
            case COMPLETED -> "<b>Setlist ready</b>\n" + escape(response.message()) + "\nOpen Cadentia to review approved references before publishing.";
            case UNAUTHORIZED -> "<b>Access needed</b>\n" + escape(response.message());
            case DISABLED -> "<b>Unavailable</b>\n" + escape(response.message());
            case INVALID -> "<b>Could not process that Telegram update</b>\nPlease retry or use /help.";
            case UNSUPPORTED -> "<b>Unsupported Telegram action</b>\nUse /help for supported commands.";
            case STALE_CALLBACK -> "<b>Expired action</b>\nThat button is no longer active. Use /status or /newsetlist.";
        });
        String selectionSummary = selectionSummary(response.currentSlots());
        if (!selectionSummary.isBlank()) {
            body.append("\n\n<b>Selected so far:</b>\n").append(selectionSummary);
        }
        if (response.status() == TelegramAdapterResponseStatus.STARTED) {
            body.append("\nSend your Scripture focus or use /newsetlist.");
        }
        return body.toString();
    }

    private String selectionSummary(GenerateSetlistRequest slots) {
        if (slots == null) {
            return "";
        }
        StringBuilder summary = new StringBuilder();
        appendIfPresent(summary, "Structure", formatCounts(slots.getCounts()));
        appendIfPresent(summary, "Language", lookupLabel(LANGUAGE_LABELS, slots.getLanguage()));
        appendIfPresent(summary, "Energy arc", lookupLabel(ENERGY_ARC_LABELS, enumValue(slots.getEnergyArc())));
        appendIfPresent(summary, "Service moment", lookupLabel(SERVICE_MOMENT_LABELS, enumValue(slots.getServiceMoment())));
        appendIfPresent(summary, "Key policy", formatKeyPolicy(slots.getKeyPolicy()));
        appendIfPresent(summary, "Tempo policy", formatTempoPolicy(slots.getTempoPolicy()));
        appendIfPresent(summary, "Scripture", slots.getVerseText());
        return summary.toString();
    }

    private String lookupLabel(Map<String, String> labels, String key) {
        if (key == null) {
            return null;
        }
        return labels.getOrDefault(key, key);
    }

    @SuppressWarnings("unchecked")
    private String enumValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return (String) value.getClass().getMethod("getValue").invoke(value);
        } catch (Exception e) {
            return value.toString();
        }
    }

    private void appendIfPresent(StringBuilder builder, String label, String value) {
        if (StringUtils.hasText(value)) {
            builder.append("  • ").append(label).append(": ").append(escape(value)).append("\n");
        }
    }

    private String formatCounts(SetlistCounts counts) {
        if (counts == null) {
            return null;
        }
        return counts.getPraise() + " praise + " + counts.getWorship() + " worship";
    }

    private String formatKeyPolicy(KeyPolicy policy) {
        if (policy == null) {
            return null;
        }
        String key;
        if (policy.getPreferSameKey() && policy.getAllowRelativeMajorMinor() && policy.getMaxKeyCenters() == 2) {
            key = "minimal";
        } else if (policy.getPreferSameKey() && !policy.getAllowRelativeMajorMinor() && policy.getMaxKeyCenters() == 1) {
            key = "same";
        } else if (!policy.getPreferSameKey() && policy.getAllowRelativeMajorMinor() && policy.getMaxKeyCenters() == 4) {
            key = "flex";
        } else {
            key = null;
        }
        if (key != null) {
            return KEY_POLICY_LABELS.get(key);
        }
        return (policy.getPreferSameKey() ? "Same key" : "Flexible") + "/" + policy.getMaxKeyCenters();
    }

    private String formatTempoPolicy(TempoPolicy policy) {
        if (policy == null) {
            return null;
        }
        String key = policy.getMaxJumpBpm() <= 8 ? "tight" : policy.getMaxJumpBpm() <= 12 ? "smooth" : "open";
        return TEMPO_POLICY_LABELS.getOrDefault(key, policy.getMaxJumpBpm() + " BPM max jump");
    }

    private TelegramRenderedMessage.TelegramInlineKeyboard keyboardFor(TelegramAdapterResponse response) {
        TelegramAdapterResponseStatus status = response.status();
        if (status == TelegramAdapterResponseStatus.COMPLETED) {
            return new TelegramRenderedMessage.TelegramInlineKeyboard(List.of(List.of(button("Revise", TelegramCallbackAction.REVISE))));
        }
        if (status != TelegramAdapterResponseStatus.STARTED
                && status != TelegramAdapterResponseStatus.CONTINUED
                && status != TelegramAdapterResponseStatus.ALREADY_ACTIVE) {
            return null;
        }
        GenerateSetlistRequest slots = response.currentSlots();
        GuidedStage stage = determineStage(slots);
        List<List<TelegramRenderedMessage.TelegramInlineKeyboardButton>> rows = new ArrayList<>();
        switch (stage) {
            case COUNTS -> rows.add(List.of(
                    button("3+2", TelegramCallbackAction.SHAPE_COUNTS, "3p2w"),
                    button("4+2", TelegramCallbackAction.SHAPE_COUNTS, "4p2w"),
                    button("10+5", TelegramCallbackAction.SHAPE_COUNTS, "10p5w")));
            case LANGUAGE -> rows.add(List.of(
                    button("English", TelegramCallbackAction.LANGUAGE, "en"),
                    button("Spanish", TelegramCallbackAction.LANGUAGE, "es"),
                    button("Portuguese", TelegramCallbackAction.LANGUAGE, "pt")));
            case ENERGY_ARC -> rows.add(List.of(
                    button("Rising", TelegramCallbackAction.ENERGY_ARC, "rising"),
                    button("Steady", TelegramCallbackAction.ENERGY_ARC, "steady"),
                    button("Falling", TelegramCallbackAction.ENERGY_ARC, "falling")));
            case SERVICE_MOMENT -> rows.add(List.of(
                    button("Opening", TelegramCallbackAction.SERVICE_MOMENT, "opening"),
                    button("Response", TelegramCallbackAction.SERVICE_MOMENT, "response"),
                    button("Sending", TelegramCallbackAction.SERVICE_MOMENT, "sending")));
            case KEY_POLICY -> rows.add(List.of(
                    button("Tight keys", TelegramCallbackAction.KEY_POLICY, "minimal"),
                    button("Flexible keys", TelegramCallbackAction.KEY_POLICY, "flex")));
            case TEMPO_POLICY -> rows.add(List.of(
                    button("Smooth tempo", TelegramCallbackAction.TEMPO_POLICY, "smooth"),
                    button("Open tempo", TelegramCallbackAction.TEMPO_POLICY, "open")));
            case CONFIRM -> {
                rows.add(List.of(button("Confirm", TelegramCallbackAction.CONFIRM), button("Revise", TelegramCallbackAction.REVISE)));
                rows.add(List.of(button("Cancel", TelegramCallbackAction.CANCEL)));
            }
        }
        return rows.isEmpty() ? null : new TelegramRenderedMessage.TelegramInlineKeyboard(rows);
    }

    private GuidedStage determineStage(GenerateSetlistRequest slots) {
        if (slots == null || countsAbsent(slots.getCounts())) {
            return GuidedStage.COUNTS;
        }
        if (!StringUtils.hasText(slots.getLanguage())) {
            return GuidedStage.LANGUAGE;
        }
        if (slots.getEnergyArc() == null) {
            return GuidedStage.ENERGY_ARC;
        }
        if (slots.getServiceMoment() == null) {
            return GuidedStage.SERVICE_MOMENT;
        }
        if (slots.getKeyPolicy() == null) {
            return GuidedStage.KEY_POLICY;
        }
        if (slots.getTempoPolicy() == null) {
            return GuidedStage.TEMPO_POLICY;
        }
        return GuidedStage.CONFIRM;
    }

    private boolean countsAbsent(SetlistCounts counts) {
        if (counts == null) {
            return true;
        }
        return counts.getPraise() == null || counts.getWorship() == null
                || (counts.getPraise() == 0 && counts.getWorship() == 0);
    }

    private TelegramRenderedMessage.TelegramInlineKeyboard completedKeyboard() {
        return new TelegramRenderedMessage.TelegramInlineKeyboard(List.of(List.of(button("Revise", TelegramCallbackAction.REVISE))));
    }

    private TelegramRenderedMessage.TelegramInlineKeyboardButton button(String label, TelegramCallbackAction action) {
        return button(label, action, "");
    }

    private TelegramRenderedMessage.TelegramInlineKeyboardButton button(String label, TelegramCallbackAction action, String value) {
        return new TelegramRenderedMessage.TelegramInlineKeyboardButton(label, TelegramCallbackData.encode(action, value));
    }

    private String callbackAcknowledgement(TelegramAdapterResponse response) {
        String value = switch (response.status()) {
            case COMPLETED -> "Proposal generated.";
            case CANCELLED -> "Cancelled.";
            case STALE_CALLBACK -> "That action expired.";
            case UNAUTHORIZED -> "Not authorized.";
            default -> "Received.";
        };
        return value.length() > CALLBACK_LIMIT ? value.substring(0, CALLBACK_LIMIT) : value;
    }

    private String summary(RecommendationExplanationEntry entry) {
        if (StringUtils.hasText(entry.getDefaultText())) {
            return safeSummary(entry.getDefaultText());
        }
        if (entry.getSubject() != null && StringUtils.hasText(entry.getSubject().getId())) {
            return "Approved selection " + entry.getSubject().getId();
        }
        return "Approved selection";
    }

    private List<String> conciseEvidence(RecommendationExplanationEntry entry) {
        List<RecommendationExplanationEvidence> evidence = entry.getEvidence() == null ? List.of() : entry.getEvidence();
        List<RecommendationExplanationEvidence> safeEvidence = evidence.stream()
                .filter(this::telegramSafeEvidence)
                .toList();
        List<String> refs = new ArrayList<>();
        safeEvidence.stream()
                .filter(item -> "catalog".equals(item.getType().getValue()))
                .findFirst()
                .map(RecommendationExplanationEvidence::getRef)
                .ifPresent(refs::add);
        safeEvidence.stream()
                .filter(item -> "approval".equals(item.getType().getValue()))
                .findFirst()
                .map(RecommendationExplanationEvidence::getRef)
                .ifPresent(refs::add);
        if (refs.size() < 2) {
            safeEvidence.stream()
                    .map(RecommendationExplanationEvidence::getRef)
                    .filter(ref -> !refs.contains(ref))
                    .limit(2 - refs.size())
                    .forEach(refs::add);
        }
        return List.copyOf(refs);
    }

    private void appendAuditMessages(StringBuilder body, List<String> auditMessages) {
        if (auditMessages == null || auditMessages.isEmpty()) {
            return;
        }
        body.append("\n<b>Notes</b>\n");
        auditMessages.stream()
                .filter(this::operatorSafeAuditMessage)
                .limit(3)
                .forEach(message -> body.append("• ").append(escape(message)).append("\n"));
    }

    private boolean operatorSafeAuditMessage(String message) {
        String normalized = message == null ? "" : message.toLowerCase();
        return !normalized.contains("unapproved candidate")
                && !normalized.contains("hidden scoring")
                && !normalized.contains("raw lyric")
                && !normalized.contains("lyrics:")
                && !normalized.contains("prompt text");
    }

    private boolean publicSelectedSong(RecommendationExplanationEntry entry) {
        return entry != null
                && entry.getScope() == RecommendationExplanationScope.SELECTED_SONG
                && entry.getAudience() == RecommendationExplanationEntry.AudienceEnum.PUBLIC
                && !unsafePublicText(entry.getDefaultText());
    }

    private boolean telegramSafeEvidence(RecommendationExplanationEvidence evidence) {
        if (evidence == null || evidence.getType() == null || !StringUtils.hasText(evidence.getRef())) {
            return false;
        }
        String type = evidence.getType().getValue();
        String ref = evidence.getRef();
        String normalizedRef = ref.toLowerCase(Locale.ROOT);
        if ("catalog".equals(type)) {
            return normalizedRef.startsWith("catalog:")
                    && safeEvidenceRef(normalizedRef);
        }
        if ("approval".equals(type)) {
            return normalizedRef.startsWith("approval:")
                    && safeEvidenceRef(normalizedRef);
        }
        return false;
    }

    private boolean safeEvidenceRef(String normalizedRef) {
        return !normalizedRef.contains("unapproved")
                && !normalizedRef.contains("rejected")
                && !normalizedRef.contains("raw")
                && !normalizedRef.contains("lyrics:")
                && !normalizedRef.contains("cross-instance")
                && !normalizedRef.contains("instance:");
    }

    private String safeSummary(String value) {
        return unsafePublicText(value) ? "Approved catalog selection" : value;
    }

    private boolean unsafePublicText(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return normalized.contains("raw lyric")
                || normalized.contains("lyrics:")
                || normalized.contains("hidden scoring")
                || normalized.contains("unapproved candidate")
                || normalized.contains("prompt text:");
    }

    private String redact(String value) {
        return value.replaceAll("(?i)(bot)?token[=: ][^\\s]+", "$1token=[redacted]")
                .replaceAll("(?i)(secret|webhook)[=: ][^\\s]+", "$1=[redacted]")
                .replaceAll("(?i)prompt text:[\\s\\S]*", "prompt text:[redacted]");
    }

    private enum GuidedStage {
        COUNTS, LANGUAGE, ENERGY_ARC, SERVICE_MOMENT, KEY_POLICY, TEMPO_POLICY, CONFIRM
    }
}
