import { z } from "zod";

export const RECOMMENDATION_EXPLANATION_SCHEMA_NAME = "recommendation_explanation" as const;
export const RECOMMENDATION_EXPLANATION_SCHEMA_VERSION = "recommendation_explanation.v1" as const;

export const EXPLANATION_CODES = {
  THEME_MATCH: "THEME_MATCH",
  ROLE_FIT: "ROLE_FIT",
  APPROVAL_ELIGIBLE: "APPROVAL_ELIGIBLE",
  SAME_KEY_TRANSITION: "SAME_KEY_TRANSITION",
  RELATIVE_KEY_TRANSITION: "RELATIVE_KEY_TRANSITION",
  TEMPO_POLICY_OK: "TEMPO_POLICY_OK",
  ENERGY_ARC_MATCH: "ENERGY_ARC_MATCH",
  COUNT_TARGET_MET: "COUNT_TARGET_MET",
  KEY_CENTER_POLICY_MET: "KEY_CENTER_POLICY_MET",
  THEME_COVERAGE: "THEME_COVERAGE",
  REQUEST_DEFAULTS_APPLIED: "REQUEST_DEFAULTS_APPLIED",
  DETERMINISTIC_TIE_BREAK_APPLIED: "DETERMINISTIC_TIE_BREAK_APPLIED",
  INSUFFICIENT_CANDIDATES: "INSUFFICIENT_CANDIDATES",
  LOW_CONFIDENCE_METADATA_PRESENT: "LOW_CONFIDENCE_METADATA_PRESENT",
  EXCLUDED_APPROVAL_GATE: "EXCLUDED_APPROVAL_GATE",
  EXCLUDED_MISSING_PROVENANCE: "EXCLUDED_MISSING_PROVENANCE",
  EXCLUDED_LICENSING_CONCERN: "EXCLUDED_LICENSING_CONCERN",
  EXCLUDED_INACTIVE_ARRANGEMENT: "EXCLUDED_INACTIVE_ARRANGEMENT",
  EXCLUDED_DUPLICATE_ARRANGEMENT: "EXCLUDED_DUPLICATE_ARRANGEMENT",
  EXCLUDED_KEY_CENTER_LIMIT: "EXCLUDED_KEY_CENTER_LIMIT",
  EXCLUDED_TEMPO_POLICY: "EXCLUDED_TEMPO_POLICY",
  EXCLUDED_WEAKER_SCORE: "EXCLUDED_WEAKER_SCORE",
  EXCLUDED_QUOTA_FILLED: "EXCLUDED_QUOTA_FILLED"
} as const;

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
  .strict();

export const explanationFactListSchema = z.array(explanationFactSchema).max(500);

const requestPolicySummarySchema = z
  .object({
    counts: z
      .object({
        praise: z.number().int().min(0).max(25),
        worship: z.number().int().min(0).max(25)
      })
      .strict(),
    keyPolicy: z
      .object({
        preferSameKey: z.boolean(),
        allowRelativeMajorMinor: z.boolean(),
        maxKeyCenters: z.number().int().min(1).max(12)
      })
      .strict(),
    tempoPolicy: z
      .object({
        maxJumpBpm: z.number().int().min(1).max(60)
      })
      .strict(),
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
