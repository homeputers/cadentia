import { describe, expect, it } from "vitest";
import { readFileSync, readdirSync } from "node:fs";

import {
  EXPLANATION_CODES,
  REASON_CODE_REGISTRY,
  RECOMMENDATION_EXPLANATION_SCHEMA_VERSION,
  parseExplanationFact,
  parseRecommendationExplanation
} from "../src/explanations.js";

const recommendationExplanationFixturesRoot = new URL(
  "../fixtures/v1/recommendation-explanations/valid/",
  import.meta.url
);

function parseRecommendationExplanationFixture(name: string): unknown {
  return JSON.parse(readFileSync(new URL(name, recommendationExplanationFixturesRoot), "utf8"));
}

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
      SCRIPTURE_MATCH: "SCRIPTURE_MATCH",
      ROLE_FIT: "ROLE_FIT",
      SCORE_COMPONENT_MUSICAL_FIT: "SCORE_COMPONENT_MUSICAL_FIT",
      SCORE_COMPONENT_ENERGY_FIT: "SCORE_COMPONENT_ENERGY_FIT",
      APPROVAL_ELIGIBLE: "APPROVAL_ELIGIBLE",
      SAME_KEY_TRANSITION: "SAME_KEY_TRANSITION",
      RELATIVE_KEY_TRANSITION: "RELATIVE_KEY_TRANSITION",
      TEMPO_POLICY_OK: "TEMPO_POLICY_OK",
      ENERGY_ARC_MATCH: "ENERGY_ARC_MATCH",
      TEMPO_TRADEOFF_ACCEPTED: "TEMPO_TRADEOFF_ACCEPTED",
      ARRANGEMENT_COMPATIBLE: "ARRANGEMENT_COMPATIBLE",
      TRANSITION_METADATA_MISSING: "TRANSITION_METADATA_MISSING",
      SET_ENERGY_ARC_MATCH: "SET_ENERGY_ARC_MATCH",
      COUNT_TARGET_MET: "COUNT_TARGET_MET",
      KEY_CENTER_POLICY_MET: "KEY_CENTER_POLICY_MET",
      THEME_COVERAGE: "THEME_COVERAGE",
      REQUEST_DEFAULTS_APPLIED: "REQUEST_DEFAULTS_APPLIED",
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
      EXCLUDED_QUOTA_FILLED: "EXCLUDED_QUOTA_FILLED",
      DETERMINISTIC_TIE_BREAK_APPLIED: "DETERMINISTIC_TIE_BREAK_APPLIED"
    });
  });

  it("publishes UI rendering metadata for every active reason code", () => {
    expect(Object.values(REASON_CODE_REGISTRY)).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: "THEME_MATCH",
          displayGroup: "theme_scripture",
          localizationKey: "item.theme_match",
          audiences: expect.arrayContaining(["public"]),
          allowedValueKeys: expect.arrayContaining(["themes"]),
          stableForClients: true
        }),
        expect.objectContaining({
          code: "EXCLUDED_WEAKER_SCORE",
          displayGroup: "near_miss",
          audiences: ["admin"]
        })
      ])
    );
  });

  it("rejects mismatched localization keys, audiences, and unregistered value keys", () => {
    expect(() =>
      parseExplanationFact({
        code: EXPLANATION_CODES.THEME_MATCH,
        severity: "info",
        scope: "selected_song",
        audience: "public",
        subject: { type: "song", id: "song-1" },
        templateKey: "item.scripture_match",
        values: { scripture: "Psalm 24" },
        evidence: [{ type: "score", ref: "score.theme" }]
      })
    ).toThrow();

    expect(() =>
      parseExplanationFact({
        code: EXPLANATION_CODES.EXCLUDED_WEAKER_SCORE,
        severity: "info",
        scope: "diagnostic",
        audience: "public",
        subject: { type: "candidate", id: "candidate-1" },
        templateKey: "candidate_exclusion.weaker_score",
        values: { candidateTitle: "Example", unrelated: "nope" },
        evidence: [{ type: "score", ref: "score.candidate" }]
      })
    ).toThrow();
  });
});


describe("recommendation explanation v1 response contract", () => {
  it.each(readdirSync(recommendationExplanationFixturesRoot))(
    "accepts valid recommendation explanation fixture %s",
    (fixtureName) => {
      const parsed = parseRecommendationExplanation(
        parseRecommendationExplanationFixture(fixtureName)
      );

      expect(parsed.schemaVersion).toBe(RECOMMENDATION_EXPLANATION_SCHEMA_VERSION);
      expect(parsed.generatedBy).toBe("RecommendationEngine");
      expect(parsed.scoringProfileVersion).toContain("scoring-profile");
      expect(parsed.catalogSnapshotVersion).toContain("catalog-snapshot");
    }
  );

  it("distinguishes song, transition, set, warning, and diagnostics sections", () => {
    const parsed = parseRecommendationExplanation(
      parseRecommendationExplanationFixture("complete-success.json")
    );

    expect(parsed.selectedSongs[0].scope).toBe("selected_song");
    expect(parsed.adjacentTransitions[0].scope).toBe("adjacent_transition");
    expect(parsed.setLevel[0].scope).toBe("set_level");

    const warningPayload = parseRecommendationExplanation(
      parseRecommendationExplanationFixture("with-warnings.json")
    );
    expect(warningPayload.warnings[0].scope).toBe("warning");

    const adminPayload = parseRecommendationExplanation(
      parseRecommendationExplanationFixture("with-admin-diagnostics.json")
    );
    expect(adminPayload.diagnostics[0]).toMatchObject({
      scope: "diagnostic",
      audience: "admin"
    });
  });

  it("rejects unversioned or client-generated explanation payloads", () => {
    const payload = parseRecommendationExplanationFixture("complete-success.json") as Record<string, unknown>;

    expect(() =>
      parseRecommendationExplanation({ ...payload, schemaVersion: undefined })
    ).toThrow();
    expect(() =>
      parseRecommendationExplanation({ ...payload, generatedBy: "LLM" })
    ).toThrow();
  });
});
