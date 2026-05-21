import { z } from "zod";

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
  "item",
  "transition",
  "set",
  "warning",
  "candidate_exclusion"
]);

export const explanationSubjectSchema = z
  .object({
    type: z.enum(["song", "arrangement", "transition", "set", "candidate"]),
    id: z.string().min(1).max(120),
    sourceId: z.string().min(1).max(120).optional(),
    targetId: z.string().min(1).max(120).optional()
  })
  .strict();

export const explanationEvidenceSchema = z
  .object({
    type: z.enum(["catalog", "request", "score", "transition", "approval", "provenance"]),
    ref: z.string().min(1).max(240),
    field: z.string().min(1).max(120).optional(),
    confidence: z.number().min(0).max(1).optional()
  })
  .strict();

export const explanationFactSchema = z
  .object({
    code: explanationCodeSchema,
    severity: explanationSeveritySchema,
    scope: explanationScopeSchema,
    subject: explanationSubjectSchema,
    templateKey: z.string().min(1).max(120),
    defaultText: z.string().min(1).max(280).optional(),
    values: z.record(z.union([z.string(), z.number(), z.boolean(), z.null()])),
    evidence: z.array(explanationEvidenceSchema).min(1).max(20),
    scoreImpact: z.number().finite().optional()
  })
  .strict();

export const explanationFactListSchema = z.array(explanationFactSchema).max(500);

export type ExplanationCode = z.infer<typeof explanationCodeSchema>;
export type ExplanationFact = z.infer<typeof explanationFactSchema>;

export function parseExplanationFact(input: unknown): ExplanationFact {
  return explanationFactSchema.parse(input);
}
