import { describe, expect, it } from "vitest";

import {
  EXPLANATION_CODES,
  parseExplanationFact
} from "../src/explanations.js";

describe("recommendation explanation fact contract", () => {
  it("accepts governed explanation fact payload", () => {
    const result = parseExplanationFact({
      code: EXPLANATION_CODES.RELATIVE_KEY_TRANSITION,
      severity: "info",
      scope: "transition",
      subject: {
        type: "transition",
        id: "song-1->song-2",
        sourceId: "song-1",
        targetId: "song-2"
      },
      templateKey: "transition.relative_key",
      values: {
        fromKey: "G",
        toKey: "Em",
        allowRelativeMajorMinor: true
      },
      evidence: [
        { type: "request", ref: "slots.keyPolicy.allowRelativeMajorMinor" },
        { type: "transition", ref: "transition[2]", field: "keyMovement" }
      ],
      scoreImpact: 1.5
    });

    expect(result.code).toBe(EXPLANATION_CODES.RELATIVE_KEY_TRANSITION);
    expect(result.evidence).toHaveLength(2);
  });

  it("rejects unknown explanation codes", () => {
    expect(() =>
      parseExplanationFact({
        code: "NEW_CODE_NOT_REGISTERED",
        severity: "info",
        scope: "item",
        subject: { type: "song", id: "song-1" },
        templateKey: "item.unknown",
        values: {},
        evidence: [{ type: "score", ref: "score.theme" }]
      })
    ).toThrow();
  });

  it("contains required baseline code registry entries", () => {
    expect(EXPLANATION_CODES).toMatchObject({
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
    });
  });
});
