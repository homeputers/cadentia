import { z } from "zod";

export const RECOMMENDATION_EXPLANATION_SCHEMA_NAME = "recommendation_explanation" as const;
export const RECOMMENDATION_EXPLANATION_SCHEMA_VERSION = "recommendation_explanation.v1" as const;

const explanationCodeRegistryEntries = [
  entry("ROLE_FIT", "item_fit", "item.role_fit", ["item", "selected_song"], ["info"], ["public", "worship_leader", "admin"], ["score"]),
  entry("APPROVAL_ELIGIBLE", "eligibility", "item.approval_eligible", ["item", "selected_song"], ["info"], ["public", "worship_leader", "admin"], ["hasProvenance"]),
  entry("THEME_MATCH", "theme_scripture", "item.theme_match", ["item", "selected_song"], ["info"], ["public", "worship_leader", "admin"], ["themes"]),
  entry("SCRIPTURE_MATCH", "theme_scripture", "item.scripture_match", ["item", "selected_song"], ["info"], ["public", "worship_leader", "admin"], ["scripture"]),
  entry("SCORE_COMPONENT_MUSICAL_FIT", "score_components", "item.score_component_musical_fit", ["item", "selected_song"], ["info"], ["worship_leader", "admin"], ["score"]),
  entry("SCORE_COMPONENT_ENERGY_FIT", "score_components", "item.score_component_energy_fit", ["item", "selected_song"], ["info"], ["worship_leader", "admin"], ["score"]),
  entry("METADATA_LOW_CONFIDENCE", "warnings", "item.metadata_low_confidence", ["item", "selected_song", "warning"], ["warning"], ["worship_leader", "admin"], ["confidence"]),
  entry("FEEDBACK_TUNING", "score_components", "item.feedback_tuning", ["item", "selected_song"], ["info", "warning"], ["worship_leader", "admin"], ["feedbackContribution"]),
  entry("SAME_KEY_TRANSITION", "transitions", "transition.same_key", ["transition", "adjacent_transition"], ["info"], ["public", "worship_leader", "admin"], ["fromKey", "toKey"]),
  entry("RELATIVE_KEY_TRANSITION", "transitions", "transition.relative_key", ["transition", "adjacent_transition"], ["info"], ["public", "worship_leader", "admin"], ["fromKey", "toKey", "allowRelativeMajorMinor"]),
  entry("CLOSE_KEY_TRANSITION", "transitions", "transition.close_key", ["transition", "adjacent_transition"], ["info"], ["worship_leader", "admin"], ["fromKey", "toKey"]),
  entry("MODULATION_PENALTY", "tradeoffs", "transition.modulation_penalty", ["transition", "adjacent_transition"], ["info", "warning"], ["worship_leader", "admin"], ["penalty"]),
  entry("TEMPO_POLICY_OK", "transitions", "transition.tempo_policy", ["transition", "adjacent_transition"], ["info", "warning"], ["public", "worship_leader", "admin"], ["fromBpm", "toBpm", "maxJumpBpm"]),
  entry("METER_COMPATIBLE", "transitions", "transition.meter_compatibility", ["transition", "adjacent_transition"], ["info", "warning"], ["worship_leader", "admin"], ["fromMeter", "toMeter"]),
  entry("ENERGY_ARC_MATCH", "energy_arc", "transition.energy_continuity", ["transition", "adjacent_transition"], ["info", "warning"], ["public", "worship_leader", "admin"], ["fromEnergy", "toEnergy"]),
  entry("SET_ENERGY_ARC_MATCH", "energy_arc", "set.energy_arc", ["set", "set_level"], ["info"], ["public", "worship_leader", "admin"], ["requestedArc", "firstPosition", "lastPosition"]),
  entry("COUNT_TARGET_MET", "set_shape", "set.count_target", ["set", "set_level"], ["info", "warning"], ["public", "worship_leader", "admin"], ["selected", "target"]),
  entry("KEY_CENTER_POLICY_MET", "set_shape", "set.key_centers", ["set", "set_level"], ["info", "warning"], ["public", "worship_leader", "admin"], ["distinctKeyCenters", "maxKeyCenters"]),
  entry("THEME_COVERAGE", "theme_scripture", "set.theme_coverage", ["set", "set_level"], ["info", "warning"], ["public", "worship_leader", "admin"], ["coveredItems", "selectedItems", "requestedThemes"]),
  entry("REQUEST_DEFAULTS_APPLIED", "policy", "set.defaults_applied", ["set", "set_level"], ["info"], ["public", "worship_leader", "admin"], ["countsDefaulted", "keyPolicyDefaulted", "tempoPolicyDefaulted", "languageDefaulted"]),
  entry("INSUFFICIENT_CANDIDATES", "warnings", "warning.insufficient_candidates", ["set", "warning"], ["warning"], ["worship_leader", "admin"], ["selected", "target", "availableCandidates"]),
  entry("LOW_CONFIDENCE_METADATA_PRESENT", "warnings", "warning.low_confidence_metadata", ["set", "warning"], ["warning"], ["worship_leader", "admin"], ["reason"]),
  entry("EXCLUDED_APPROVAL_GATE", "eligibility", "diagnostic.excluded_approval_gate", ["diagnostic"], ["blocked"], ["admin"], ["gate", "candidateCount"]),
  entry("EXCLUDED_MISSING_PROVENANCE", "eligibility", "diagnostic.excluded_missing_provenance", ["diagnostic"], ["blocked"], ["admin"], ["candidateCount"]),
  entry("EXCLUDED_LICENSING_CONCERN", "eligibility", "diagnostic.excluded_licensing_concern", ["diagnostic"], ["blocked"], ["admin"], ["candidateCount"]),
  entry("EXCLUDED_INACTIVE_ARRANGEMENT", "eligibility", "diagnostic.excluded_inactive_arrangement", ["diagnostic"], ["blocked"], ["admin"], ["candidateCount"]),
  entry("EXCLUDED_DUPLICATE_ARRANGEMENT", "near_miss", "diagnostic.excluded_duplicate_arrangement", ["diagnostic"], ["info"], ["admin"], ["duplicateOfArrangementId"]),
  entry("EXCLUDED_KEY_CENTER_LIMIT", "near_miss", "diagnostic.excluded_key_center_limit", ["diagnostic"], ["info"], ["admin"], ["candidateKey", "maxKeyCenters"]),
  entry("EXCLUDED_TEMPO_POLICY", "near_miss", "diagnostic.excluded_tempo_policy", ["diagnostic"], ["warning"], ["admin"], ["fromBpm", "toBpm", "maxJumpBpm"]),
  entry("EXCLUDED_WEAKER_SCORE", "near_miss", "candidate_exclusion.weaker_score", ["candidate_exclusion", "diagnostic"], ["info"], ["admin"], ["candidateTitle", "candidateScore"]),
  entry("EXCLUDED_QUOTA_FILLED", "near_miss", "candidate_exclusion.quota_filled", ["candidate_exclusion", "diagnostic"], ["info"], ["admin"], ["candidateTitle", "candidateScore"]),
  entry("DETERMINISTIC_TIE_BREAK_APPLIED", "tie_breaks", "tie_break.deterministic_applied", ["set", "set_level", "diagnostic"], ["info"], ["public", "worship_leader", "admin"], ["rule", "direction", "affectedResultIds"])
] as const;

function entry<Code extends string>(
  code: Code,
  displayGroup: string,
  localizationKey: string,
  allowedScopes: readonly string[],
  allowedSeverities: readonly string[],
  audiences: readonly string[],
  allowedValueKeys: readonly string[]
) {
  return {
    code,
    status: "active",
    stableForClients: true,
    introducedInVersion: "recommendation_explanation.v1",
    displayGroup,
    localizationKey,
    allowedScopes,
    allowedSeverities,
    audiences,
    allowedValueKeys
  } as const;
}

export const REASON_CODE_REGISTRY = Object.freeze(
  Object.fromEntries(explanationCodeRegistryEntries.map((registryEntry) => [registryEntry.code, registryEntry]))
) as Record<(typeof explanationCodeRegistryEntries)[number]["code"], (typeof explanationCodeRegistryEntries)[number]>;

export const EXPLANATION_CODES = Object.freeze(
  Object.fromEntries(explanationCodeRegistryEntries.map((registryEntry) => [registryEntry.code, registryEntry.code]))
) as Record<(typeof explanationCodeRegistryEntries)[number]["code"], (typeof explanationCodeRegistryEntries)[number]["code"]>;

export const explanationCodeSchema = z.enum(Object.values(EXPLANATION_CODES) as [string, ...string[]]);
export const explanationSeveritySchema = z.enum(["info", "warning", "blocked"]);
export const explanationScopeSchema = z.enum([
  "selected_song",
  "adjacent_transition",
  "set_level",
  "warning",
  "diagnostic",
  "item",
  "transition",
  "set",
  "candidate_exclusion"
]);
export const explanationAudienceSchema = z.enum(["public", "worship_leader", "admin"]);

export const explanationSubjectSchema = z
  .object({
    type: z.enum(["song", "arrangement", "transition", "set", "candidate", "policy", "catalog"]),
    id: z.string().min(1).max(120),
    itemId: z.string().min(1).max(120).optional(),
    songId: z.string().min(1).max(120).optional(),
    arrangementId: z.string().min(1).max(120).optional(),
    sourceId: z.string().min(1).max(120).optional(),
    targetId: z.string().min(1).max(120).optional(),
    sourceItemId: z.string().min(1).max(120).optional(),
    targetItemId: z.string().min(1).max(120).optional(),
    sourceArrangementId: z.string().min(1).max(120).optional(),
    targetArrangementId: z.string().min(1).max(120).optional()
  })
  .strict();

export const explanationEvidenceSchema = z
  .object({
    type: z.enum([
      "catalog",
      "request",
      "score",
      "transition",
      "approval",
      "provenance",
      "policy",
      "diagnostic"
    ]),
    ref: z.string().min(1).max(240),
    field: z.string().min(1).max(120).optional(),
    confidence: z.number().min(0).max(1).optional()
  })
  .strict();

const primitiveValueSchema = z.union([z.string(), z.number(), z.boolean(), z.null()]);

export const explanationFactSchema = z
  .object({
    code: explanationCodeSchema,
    severity: explanationSeveritySchema,
    scope: explanationScopeSchema,
    audience: explanationAudienceSchema.default("public"),
    subject: explanationSubjectSchema,
    templateKey: z.string().min(1).max(120),
    defaultText: z.string().min(1).max(280).optional(),
    values: z.record(primitiveValueSchema),
    evidence: z.array(explanationEvidenceSchema).min(1).max(20),
    scoreImpact: z.number().finite().optional()
  })
  .strict()
  .superRefine((fact, context) => {
    const registryEntry = REASON_CODE_REGISTRY[fact.code as keyof typeof REASON_CODE_REGISTRY];
    if (!registryEntry.allowedSeverities.includes(fact.severity)) {
      context.addIssue({ code: z.ZodIssueCode.custom, message: `Severity ${fact.severity} is not registered for ${fact.code}` });
    }
    if (!registryEntry.allowedScopes.includes(fact.scope)) {
      context.addIssue({ code: z.ZodIssueCode.custom, message: `Scope ${fact.scope} is not registered for ${fact.code}` });
    }
    if (!registryEntry.audiences.includes(fact.audience)) {
      context.addIssue({ code: z.ZodIssueCode.custom, message: `Audience ${fact.audience} is not registered for ${fact.code}` });
    }
    if (fact.templateKey !== registryEntry.localizationKey) {
      context.addIssue({ code: z.ZodIssueCode.custom, message: `Localization key ${fact.templateKey} is not registered for ${fact.code}` });
    }
    for (const valueKey of Object.keys(fact.values)) {
      if (!registryEntry.allowedValueKeys.includes(valueKey)) {
        context.addIssue({ code: z.ZodIssueCode.custom, message: `Value key ${valueKey} is not registered for ${fact.code}` });
      }
    }
  });

export const explanationFactListSchema = z.array(explanationFactSchema).max(500);

const requestPolicySummarySchema = z
  .object({
    counts: z.object({ praise: z.number().int().min(0).max(25), worship: z.number().int().min(0).max(25) }).strict(),
    keyPolicy: z.object({ preferSameKey: z.boolean(), allowRelativeMajorMinor: z.boolean(), maxKeyCenters: z.number().int().min(1).max(12) }).strict(),
    tempoPolicy: z.object({ maxJumpBpm: z.number().int().min(1).max(60) }).strict(),
    themeHints: z.array(z.string().min(1).max(120)).max(20).default([]),
    scriptureReferences: z.array(z.string().min(1).max(100)).max(20).default([]),
    language: z.string().min(1).max(35).nullable().optional(),
    energyArc: z.string().min(1).max(40).nullable().optional(),
    serviceMoment: z.string().min(1).max(40).nullable().optional(),
    appliedDefaults: z.array(z.string().min(1).max(120)).max(40).default([])
  })
  .strict();

const deterministicTieBreakSchema = z
  .object({
    rule: z.string().min(1).max(120),
    direction: z.enum(["asc", "desc"]),
    affectedResultIds: z.array(z.string().min(1).max(120)).min(1).max(50),
    values: z.record(primitiveValueSchema).default({}),
    reasonCode: explanationCodeSchema.default(EXPLANATION_CODES.DETERMINISTIC_TIE_BREAK_APPLIED)
  })
  .strict();

export const recommendationExplanationSchema = z
  .object({
    schemaName: z.literal(RECOMMENDATION_EXPLANATION_SCHEMA_NAME),
    schemaVersion: z.literal(RECOMMENDATION_EXPLANATION_SCHEMA_VERSION),
    generatedBy: z.literal("RecommendationEngine"),
    requestId: z.string().min(1).max(120),
    correlationId: z.string().min(1).max(120).optional(),
    recommendationResultId: z.string().min(1).max(120),
    setlistId: z.string().min(1).max(120).optional(),
    scoringProfileVersion: z.string().min(1).max(120),
    catalogSnapshotVersion: z.string().min(1).max(120),
    generatedAt: z.string().datetime({ offset: true }),
    requestPolicySummary: requestPolicySummarySchema,
    deterministicTieBreaks: z.array(deterministicTieBreakSchema).max(100).default([]),
    selectedSongs: explanationFactListSchema.default([]),
    adjacentTransitions: explanationFactListSchema.default([]),
    setLevel: explanationFactListSchema.default([]),
    warnings: explanationFactListSchema.default([]),
    diagnostics: explanationFactListSchema.default([])
  })
  .strict();

export type ExplanationCode = z.infer<typeof explanationCodeSchema>;
export type ExplanationFact = z.infer<typeof explanationFactSchema>;
export type RecommendationExplanation = z.infer<typeof recommendationExplanationSchema>;

export function parseExplanationFact(input: unknown): ExplanationFact {
  return explanationFactSchema.parse(input);
}

export function parseRecommendationExplanation(input: unknown): RecommendationExplanation {
  return recommendationExplanationSchema.parse(input);
}
