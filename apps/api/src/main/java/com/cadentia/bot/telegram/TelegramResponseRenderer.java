package com.cadentia.bot.telegram;

import com.cadentia.generated.model.ConversationSlotSource;
import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.KeyPolicy;
import com.cadentia.generated.model.SlotValueSource;
import com.cadentia.generated.model.RecommendationExplanation;
import com.cadentia.generated.model.RecommendationExplanationEntry;
import com.cadentia.generated.model.RecommendationExplanationEvidence;
import com.cadentia.generated.model.RecommendationExplanationScope;
import com.cadentia.generated.model.SetlistCounts;
import com.cadentia.generated.model.SetlistProposalResponse;
import com.cadentia.generated.model.TempoPolicy;
import com.cadentia.runtime.InstanceConfigurationProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TelegramResponseRenderer {
    static final int TELEGRAM_MESSAGE_LIMIT = 4096;
    static final int SAFE_MESSAGE_LIMIT = 3900;
    private static final int CALLBACK_LIMIT = 180;

    private final InstanceConfigurationProvider configurationProvider;

    public TelegramResponseRenderer() {
        this.configurationProvider = null;
    }

    @Autowired
    public TelegramResponseRenderer(InstanceConfigurationProvider configurationProvider) {
        this.configurationProvider = configurationProvider;
    }

    public List<TelegramRenderedMessage> render(TelegramAdapterResponse response) {
        if (response == null) {
            return List.of(TelegramRenderedMessage.message(null, TelegramI18n.format("couldNotProcess", locale()) + ". " + TelegramI18n.text("retryHelp", locale()), null));
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
        body.append("<b>").append(TelegramI18n.text("proposal", locale())).append("</b>\n");
        if (proposal == null) {
            body.append(TelegramI18n.text("noApprovedSongs", locale())).append(" ");
            body.append(TelegramI18n.text("reviewProposal", locale()));
            return List.of(TelegramRenderedMessage.message(chatId, body.toString(), completedKeyboard()));
        }
        if (StringUtils.hasText(proposal.getRecommendationResultId())) {
            body.append(TelegramI18n.format("result", locale(), escape(proposal.getRecommendationResultId())));
        }
        RecommendationExplanation explanation = proposal.getExplanation();
        if (explanation != null && StringUtils.hasText(explanation.getSetlistId())) {
            body.append(TelegramI18n.format("setlistId", locale(), escape(explanation.getSetlistId())));
        }
        if (explanation != null && StringUtils.hasText(explanation.getSetlistVersionId())) {
            body.append(TelegramI18n.format("versionId", locale(), escape(explanation.getSetlistVersionId())));
        }
        List<RecommendationExplanationEntry> selected = explanation == null || explanation.getSelectedSongs() == null
                ? List.of()
                : explanation.getSelectedSongs().stream()
                        .filter(this::publicSelectedSong)
                        .toList();
        if (selected.isEmpty()) {
            body.append(TelegramI18n.text("noApprovedSongs", locale())).append(" ");
        } else {
            body.append(TelegramI18n.text("recommendedSongs", locale()));
            int index = 1;
            for (RecommendationExplanationEntry entry : selected) {
                body.append(index++).append(". ").append(escape(summary(entry))).append("\n");
                    conciseEvidence(entry).forEach(ref -> body.append(TelegramI18n.format("ref", locale(), escape(ref))));
            }
        }
        appendAuditMessages(body, proposal.getAuditMessages());
        body.append(TelegramI18n.text("reviewProposal", locale()));
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
            case STARTED -> "<b>" + TelegramI18n.text("cadentia", locale()) + "</b>\n" + escape(response.message());
            case CONTINUED, ALREADY_ACTIVE -> "<b>" + TelegramI18n.text("cadentiaUpdate", locale()) + "</b>\n" + escape(response.message());
            case CANCELLED -> "<b>" + TelegramI18n.text("cancelledHeading", locale()) + "</b>\n" + escape(response.message()) + " " + TelegramI18n.text("useNewSetlist", locale());
            case COMPLETED -> "<b>" + TelegramI18n.text("setlistReady", locale()) + "</b>\n" + escape(response.message()) + "\n" + TelegramI18n.text("openReview", locale());
            case UNAUTHORIZED -> "<b>" + TelegramI18n.text("accessNeeded", locale()) + "</b>\n" + escape(response.message());
            case DISABLED -> "<b>" + TelegramI18n.text("unavailable", locale()) + "</b>\n" + escape(response.message());
            case INVALID -> "<b>" + TelegramI18n.text("couldNotProcess", locale()) + "</b>\n" + TelegramI18n.text("retryHelp", locale());
            case UNSUPPORTED -> "<b>" + TelegramI18n.text("unsupportedAction", locale()) + "</b>\n" + TelegramI18n.text("useHelp", locale());
            case STALE_CALLBACK -> "<b>" + TelegramI18n.text("expiredAction", locale()) + "</b>\n" + TelegramI18n.text("buttonInactive", locale());
        });
        if (response.status() != TelegramAdapterResponseStatus.STARTED) {
            String selectionSummary = selectionSummary(response.currentSlots());
            if (!selectionSummary.isBlank()) {
                body.append("\n\n<b>").append(TelegramI18n.text("selectedSoFar", locale())).append("</b>\n").append(selectionSummary);
            }
        }
        if (response.status() == TelegramAdapterResponseStatus.STARTED) {
            body.append("\n").append(TelegramI18n.text("sendScripture", locale()));
        }
        return body.toString();
    }

    private String selectionSummary(GenerateSetlistRequest slots) {
        if (slots == null) {
            return "";
        }
        StringBuilder summary = new StringBuilder();
        appendIfPresent(summary, TelegramI18n.text("structure", locale()), formatCounts(slots.getCounts()));
        appendIfPresent(summary, TelegramI18n.text("language", locale()), TelegramI18n.label("language", slots.getLanguage(), locale()));
        appendIfPresent(summary, TelegramI18n.text("energyArc", locale()), TelegramI18n.label("energyArc", enumValue(slots.getEnergyArc()), locale()));
        appendIfPresent(summary, TelegramI18n.text("serviceMoment", locale()), TelegramI18n.label("serviceMoment", enumValue(slots.getServiceMoment()), locale()));
        appendIfPresent(summary, TelegramI18n.text("keyPolicy", locale()), formatKeyPolicy(slots.getKeyPolicy()));
        appendIfPresent(summary, TelegramI18n.text("tempoPolicy", locale()), formatTempoPolicy(slots.getTempoPolicy()));
        appendIfPresent(summary, TelegramI18n.text("scripture", locale()), slots.getVerseText());
        return summary.toString();
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
        return counts.getPraise() + " " + TelegramI18n.text("praise", locale()) + " + "
                + counts.getWorship() + " " + TelegramI18n.text("worship", locale());
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
            return TelegramI18n.label("keyPolicy", key, locale());
        }
        return (policy.getPreferSameKey() ? TelegramI18n.text("sameKey", locale()) : TelegramI18n.text("flexibleKeys", locale()))
                + "/" + policy.getMaxKeyCenters();
    }

    private String formatTempoPolicy(TempoPolicy policy) {
        if (policy == null) {
            return null;
        }
        String key = policy.getMaxJumpBpm() <= 8 ? "tight" : policy.getMaxJumpBpm() <= 12 ? "smooth" : "open";
        return TelegramI18n.label("tempoPolicy", key, locale());
    }

    private TelegramRenderedMessage.TelegramInlineKeyboard keyboardFor(TelegramAdapterResponse response) {
        TelegramAdapterResponseStatus status = response.status();
        if (status == TelegramAdapterResponseStatus.COMPLETED) {
            return new TelegramRenderedMessage.TelegramInlineKeyboard(List.of(List.of(button(TelegramI18n.text("revise", locale()), TelegramCallbackAction.REVISE))));
        }
        if (status != TelegramAdapterResponseStatus.STARTED
                && status != TelegramAdapterResponseStatus.CONTINUED
                && status != TelegramAdapterResponseStatus.ALREADY_ACTIVE) {
            return null;
        }
        GenerateSetlistRequest slots = response.currentSlots();
        GuidedStage stage = determineStage(slots, response.slotSources());
        List<List<TelegramRenderedMessage.TelegramInlineKeyboardButton>> rows = new ArrayList<>();
        switch (stage) {
            case COUNTS -> rows.add(List.of(
                    button("3+2", TelegramCallbackAction.SHAPE_COUNTS, "3p2w"),
                    button("4+2", TelegramCallbackAction.SHAPE_COUNTS, "4p2w"),
                    button("10+5", TelegramCallbackAction.SHAPE_COUNTS, "10p5w")));
            case LANGUAGE -> rows.add(List.of(
                    button(TelegramI18n.text("english", locale()), TelegramCallbackAction.LANGUAGE, "en"),
                    button(TelegramI18n.text("spanish", locale()), TelegramCallbackAction.LANGUAGE, "es"),
                    button(TelegramI18n.text("portuguese", locale()), TelegramCallbackAction.LANGUAGE, "pt")));
            case ENERGY_ARC -> rows.add(List.of(
                    button(TelegramI18n.text("rising", locale()), TelegramCallbackAction.ENERGY_ARC, "rising"),
                    button(TelegramI18n.text("steady", locale()), TelegramCallbackAction.ENERGY_ARC, "steady"),
                    button(TelegramI18n.text("falling", locale()), TelegramCallbackAction.ENERGY_ARC, "falling")));
            case SERVICE_MOMENT -> rows.add(List.of(
                    button(TelegramI18n.text("opening", locale()), TelegramCallbackAction.SERVICE_MOMENT, "opening"),
                    button(TelegramI18n.text("response", locale()), TelegramCallbackAction.SERVICE_MOMENT, "response"),
                    button(TelegramI18n.text("sending", locale()), TelegramCallbackAction.SERVICE_MOMENT, "sending")));
            case KEY_POLICY -> rows.add(List.of(
                    button(TelegramI18n.text("tightKeys", locale()), TelegramCallbackAction.KEY_POLICY, "minimal"),
                    button(TelegramI18n.text("flexibleKeys", locale()), TelegramCallbackAction.KEY_POLICY, "flex")));
            case TEMPO_POLICY -> rows.add(List.of(
                    button(TelegramI18n.text("smoothTempo", locale()), TelegramCallbackAction.TEMPO_POLICY, "smooth"),
                    button(TelegramI18n.text("openTempo", locale()), TelegramCallbackAction.TEMPO_POLICY, "open")));
            case CONFIRM -> {
                rows.add(List.of(button(TelegramI18n.text("confirm", locale()), TelegramCallbackAction.CONFIRM), button(TelegramI18n.text("revise", locale()), TelegramCallbackAction.REVISE)));
                rows.add(List.of(button(TelegramI18n.text("cancel", locale()), TelegramCallbackAction.CANCEL)));
            }
        }
        return rows.isEmpty() ? null : new TelegramRenderedMessage.TelegramInlineKeyboard(rows);
    }

    private GuidedStage determineStage(GenerateSetlistRequest slots, List<ConversationSlotSource> slotSources) {
        if (slots == null || countsAbsent(slots.getCounts()) || sourceIsDefault(slotSources, "counts")) {
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
        if (slots.getKeyPolicy() == null || sourceIsDefault(slotSources, "keyPolicy")) {
            return GuidedStage.KEY_POLICY;
        }
        if (slots.getTempoPolicy() == null || sourceIsDefault(slotSources, "tempoPolicy")) {
            return GuidedStage.TEMPO_POLICY;
        }
        return GuidedStage.CONFIRM;
    }

    private boolean sourceIsDefault(List<ConversationSlotSource> slotSources, String slotName) {
        if (slotSources == null) {
            return false;
        }
        return slotSources.stream()
                .anyMatch(s -> s.getSlot().getValue().equals(slotName)
                        && s.getSource() == SlotValueSource.DEFAULT);
    }

    private boolean countsAbsent(SetlistCounts counts) {
        if (counts == null) {
            return true;
        }
        return counts.getPraise() == null || counts.getWorship() == null
                || (counts.getPraise() == 0 && counts.getWorship() == 0);
    }

    private TelegramRenderedMessage.TelegramInlineKeyboard completedKeyboard() {
        return new TelegramRenderedMessage.TelegramInlineKeyboard(List.of(List.of(button(TelegramI18n.text("revise", locale()), TelegramCallbackAction.REVISE))));
    }

    private TelegramRenderedMessage.TelegramInlineKeyboardButton button(String label, TelegramCallbackAction action) {
        return button(label, action, "");
    }

    private TelegramRenderedMessage.TelegramInlineKeyboardButton button(String label, TelegramCallbackAction action, String value) {
        return new TelegramRenderedMessage.TelegramInlineKeyboardButton(label, TelegramCallbackData.encode(action, value));
    }

    private String callbackAcknowledgement(TelegramAdapterResponse response) {
        String value = switch (response.status()) {
            case COMPLETED -> TelegramI18n.text("proposalAck", locale());
            case CANCELLED -> TelegramI18n.text("cancelledAck", locale());
            case STALE_CALLBACK -> TelegramI18n.text("expiredAck", locale());
            case UNAUTHORIZED -> TelegramI18n.text("unauthorizedAck", locale());
            default -> TelegramI18n.text("received", locale());
        };
        return value.length() > CALLBACK_LIMIT ? value.substring(0, CALLBACK_LIMIT) : value;
    }

    private String summary(RecommendationExplanationEntry entry) {
        if (StringUtils.hasText(entry.getDefaultText())) {
            return safeSummary(entry.getDefaultText());
        }
        if (entry.getSubject() != null && StringUtils.hasText(entry.getSubject().getId())) {
            return TelegramI18n.format("approvedSelection", locale(), entry.getSubject().getId());
        }
        return TelegramI18n.text("approvedSelectionGeneric", locale());
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
        body.append(TelegramI18n.text("notes", locale()));
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
        return unsafePublicText(value) ? TelegramI18n.text("approvedCatalogSelection", locale()) : value;
    }

    private boolean unsafePublicText(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return normalized.contains("raw lyric")
                || normalized.contains("lyrics:")
                || normalized.contains("hidden scoring")
                || normalized.contains("unapproved candidate")
                || normalized.contains("prompt text:");
    }

    private Locale locale() {
        return configurationProvider == null
                ? Locale.US
                : TelegramI18n.locale(configurationProvider.current().locale());
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
