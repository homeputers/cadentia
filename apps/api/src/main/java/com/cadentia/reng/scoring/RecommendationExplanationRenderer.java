package com.cadentia.reng.scoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecommendationExplanationRenderer {

    private static final String DEFAULT_LOCALE = "en";

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_]+)}");

    private static final Map<String, String> TEMPLATE_BY_KEY = Map.ofEntries(
            Map.entry("item.theme_match", "Matched theme tags {themes}."),
            Map.entry("item.scripture_match", "Matched scripture references {scripture}."),
            Map.entry("item.role_fit", "Role-fit component scored {score}."),
            Map.entry("item.approval_eligible", "Approval eligibility includes lyrics provenance: {hasProvenance}."),
            Map.entry("item.score_component_musical_fit", "Musical-fit component scored {score}."),
            Map.entry("item.score_component_energy_fit", "Energy-fit component scored {score}."),
            Map.entry("item.metadata_low_confidence", "Metadata confidence is {confidence}."),
            Map.entry("item.feedback_tuning", "Feedback tuning contributed {feedbackContribution} to ranking."),
            Map.entry("transition.same_key", "Transition kept the same key from {fromKey} to {toKey}."),
            Map.entry("transition.relative_key", "Transition from {fromKey} to {toKey} used allowed relative major/minor movement."),
            Map.entry("transition.close_key", "Transition uses close key movement from {fromKey} to {toKey}."),
            Map.entry("transition.modulation_penalty", "Transition modulation penalty was {penalty}."),
            Map.entry("transition.tempo_policy", "Tempo moved from {fromBpm} BPM to {toBpm} BPM within max jump {maxJumpBpm}."),
            Map.entry("transition.meter_compatibility", "Meter moved from {fromMeter} to {toMeter}."),
            Map.entry("transition.energy_continuity", "Energy moved from {fromEnergy} to {toEnergy}."),
            Map.entry("set.count_target", "Set selected {selected} songs against target {target}."),
            Map.entry("set.key_centers", "Set uses {distinctKeyCenters} key centers with max policy {maxKeyCenters}."),
            Map.entry("set.energy_arc", "Set follows requested energy arc {requestedArc} from position {firstPosition} to {lastPosition}."),
            Map.entry("set.theme_coverage", "Theme coverage matched {coveredItems} of {selectedItems} selected items for {requestedThemes}."),
            Map.entry("set.defaults_applied", "Defaults applied: counts={countsDefaulted}, keyPolicy={keyPolicyDefaulted}, tempoPolicy={tempoPolicyDefaulted}, language={languageDefaulted}."),
            Map.entry("warning.low_confidence_metadata", "Some selected metadata had lower confidence: {reason}."),
            Map.entry("warning.insufficient_candidates", "Only {selected} songs were selected for target {target}; available candidates: {availableCandidates}."),
            Map.entry("candidate_exclusion.quota_filled", "Candidate {candidateTitle} was excluded because the quota was already filled."),
            Map.entry("candidate_exclusion.weaker_score", "Candidate {candidateTitle} was excluded because its score {candidateScore} ranked lower."));

    public ExplanationRenderResult render(RecommendationExplanationFact fact) {
        return render(fact, DEFAULT_LOCALE);
    }

    public ExplanationRenderResult render(RecommendationExplanationFact fact, String locale) {
        String template = TEMPLATE_BY_KEY.get(fact.templateKey());
        if (template == null) {
            return new ExplanationRenderResult(
                    "[" + fact.code() + "]",
                    fact.templateKey(),
                    List.of("Unknown template key: " + fact.templateKey()));
        }

        List<String> errors = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1);
            Object value = fact.values().get(token);
            if (value == null) {
                errors.add("Missing template value: " + token);
                matcher.appendReplacement(rendered, Matcher.quoteReplacement("{" + token + "}"));
                continue;
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(rendered);

        return new ExplanationRenderResult(rendered.toString(), fact.templateKey(), errors);
    }

    public List<ExplanationRenderResult> renderAll(List<RecommendationExplanationFact> facts) {
        return renderAll(facts, DEFAULT_LOCALE);
    }

    public List<ExplanationRenderResult> renderAll(List<RecommendationExplanationFact> facts, String locale) {
        if (facts == null || facts.isEmpty()) {
            return List.of();
        }
        String normalizedLocale = (locale == null || locale.isBlank()) ? DEFAULT_LOCALE : locale;
        return facts.stream().map(fact -> render(fact, normalizedLocale)).toList();
    }
}
