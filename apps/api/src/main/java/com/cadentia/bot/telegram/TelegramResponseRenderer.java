package com.cadentia.bot.telegram;

import com.cadentia.generated.model.RecommendationExplanation;
import com.cadentia.generated.model.RecommendationExplanationEntry;
import com.cadentia.generated.model.RecommendationExplanationEvidence;
import com.cadentia.generated.model.RecommendationExplanationScope;
import com.cadentia.generated.model.SetlistProposalResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TelegramResponseRenderer {
    static final int TELEGRAM_MESSAGE_LIMIT = 4096;
    static final int SAFE_MESSAGE_LIMIT = 3900;
    private static final int CALLBACK_LIMIT = 180;

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
        TelegramRenderedMessage.TelegramInlineKeyboard keyboard = keyboardFor(response.status());
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
            return List.of(TelegramRenderedMessage.message(chatId, body.toString(), keyboardFor(TelegramAdapterResponseStatus.COMPLETED)));
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
        TelegramRenderedMessage.TelegramInlineKeyboard keyboard = keyboardFor(TelegramAdapterResponseStatus.COMPLETED);
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
        return switch (response.status()) {
            case STARTED -> "<b>Cadentia</b>\n" + escape(response.message()) + "\nSend your Scripture focus or use /newsetlist.";
            case CONTINUED, ALREADY_ACTIVE -> "<b>Cadentia update</b>\n" + escape(response.message()) + "\nUse the buttons or reply with concise details.";
            case CANCELLED -> "<b>Cancelled</b>\n" + escape(response.message()) + " You can start again with /newsetlist.";
            case COMPLETED -> "<b>Setlist ready</b>\n" + escape(response.message()) + "\nOpen Cadentia to review approved references before publishing.";
            case UNAUTHORIZED -> "<b>Access needed</b>\n" + escape(response.message());
            case DISABLED -> "<b>Unavailable</b>\n" + escape(response.message());
            case INVALID -> "<b>Could not process that Telegram update</b>\nPlease retry or use /help.";
            case UNSUPPORTED -> "<b>Unsupported Telegram action</b>\nUse /help for supported commands.";
            case STALE_CALLBACK -> "<b>Expired action</b>\nThat button is no longer active. Use /status or /newsetlist.";
        };
    }

    private TelegramRenderedMessage.TelegramInlineKeyboard keyboardFor(TelegramAdapterResponseStatus status) {
        return switch (status) {
            case STARTED, CONTINUED, ALREADY_ACTIVE -> new TelegramRenderedMessage.TelegramInlineKeyboard(List.of(
                    List.of(
                            button("3+2", TelegramCallbackAction.SHAPE_COUNTS, "3p2w"),
                            button("4+2", TelegramCallbackAction.SHAPE_COUNTS, "4p2w"),
                            button("10+5", TelegramCallbackAction.SHAPE_COUNTS, "10p5w")),
                    List.of(
                            button("English", TelegramCallbackAction.LANGUAGE, "en"),
                            button("Spanish", TelegramCallbackAction.LANGUAGE, "es"),
                            button("Portuguese", TelegramCallbackAction.LANGUAGE, "pt")),
                    List.of(
                            button("Rising", TelegramCallbackAction.ENERGY_ARC, "rising"),
                            button("Steady", TelegramCallbackAction.ENERGY_ARC, "steady"),
                            button("Falling", TelegramCallbackAction.ENERGY_ARC, "falling")),
                    List.of(
                            button("Opening", TelegramCallbackAction.SERVICE_MOMENT, "opening"),
                            button("Response", TelegramCallbackAction.SERVICE_MOMENT, "response"),
                            button("Sending", TelegramCallbackAction.SERVICE_MOMENT, "sending")),
                    List.of(
                            button("Tight keys", TelegramCallbackAction.KEY_POLICY, "minimal"),
                            button("Flexible keys", TelegramCallbackAction.KEY_POLICY, "flex")),
                    List.of(
                            button("Smooth tempo", TelegramCallbackAction.TEMPO_POLICY, "smooth"),
                            button("Open tempo", TelegramCallbackAction.TEMPO_POLICY, "open")),
                    List.of(button("Confirm", TelegramCallbackAction.CONFIRM), button("Revise", TelegramCallbackAction.REVISE)),
                    List.of(button("Cancel", TelegramCallbackAction.CANCEL))));
            case COMPLETED -> new TelegramRenderedMessage.TelegramInlineKeyboard(List.of(List.of(button("Revise", TelegramCallbackAction.REVISE))));
            default -> null;
        };
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
}
