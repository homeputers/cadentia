import { describe, expect, it } from "vitest";
import Ajv from "ajv";
import { readFileSync, readdirSync } from "node:fs";

import {
  INTENT_CONTRACT_VERSION,
  intentContractV1SchemaPath,
  parseIntentOutput
} from "../src/index.js";

const fixturesRoot = new URL("../fixtures/v1/", import.meta.url);
const schema = JSON.parse(readFileSync(intentContractV1SchemaPath, "utf8"));
const ajv = new Ajv({ allErrors: true, strict: false });
const validate = ajv.compile(schema);

function readFixture(kind: "valid" | "invalid", name: string): string {
  return readFileSync(new URL(`${kind}/${name}`, fixturesRoot), "utf8");
}

function parseFixture(kind: "valid" | "invalid", name: string): unknown {
  return JSON.parse(readFixture(kind, name));
}

describe("intent contract v1 schema artifact", () => {
  it("exposes the versioned v1 schema artifact", () => {
    expect(INTENT_CONTRACT_VERSION).toBe("v1");
    expect(schema.$id).toContain("/schemas/intent/v1/intent.schema.json");
    expect(schema.description).toContain("Defaults are annotations");
  });

  it.each(readdirSync(new URL("valid/", fixturesRoot)))
    ("accepts valid fixture %s", (fixtureName) => {
      const payload = parseFixture("valid", fixtureName);

      expect(validate(payload), JSON.stringify(validate.errors, null, 2)).toBe(true);
      expect(() => parseIntentOutput(payload)).not.toThrow();
    });

  it.each([
    "top-level-unsupported-field.json",
    "slot-unsupported-field.json",
    "out-of-bounds-count.json",
    "unsupported-enum.json"
  ])("rejects invalid fixture %s", (fixtureName) => {
    const payload = parseFixture("invalid", fixtureName);

    expect(validate(payload)).toBe(false);
    expect(() => parseIntentOutput(payload)).toThrow();
  });

  it("keeps malformed fixture outside schema validation", () => {
    expect(() => JSON.parse(readFixture("invalid", "malformed.json"))).toThrow();
  });

  it("applies documented backend defaults in shared TypeScript tooling", () => {
    const result = parseIntentOutput(
      parseFixture("valid", "generate-setlist-missing-optional.json")
    );

    expect(result).toEqual({
      intent: "GENERATE_SETLIST",
      slots: {
        verseText: "",
        scriptureReferences: [],
        themeHints: [],
        counts: { praise: 10, worship: 5 },
        keyPolicy: {
          preferSameKey: true,
          allowRelativeMajorMinor: true,
          maxKeyCenters: 2
        },
        tempoPolicy: { maxJumpBpm: 12 },
        language: null,
        energyArc: null,
        excludedSongs: [],
        serviceMoment: null
      }
    });
  });

  it("does not permit song-selection or catalog decision fields", () => {
    const forbiddenTerms = [
      "selectedSongs",
      "arrangementIds",
      "approvalDecision",
      "provenanceRecords",
      "databaseWrites"
    ];

    for (const term of forbiddenTerms) {
      expect(JSON.stringify(schema)).not.toContain(term);
    }
  });

  it("allows only bounded counts, tempo, key centers, arrays, strings, and enums", () => {
    const definitions = schema.definitions;

    expect(definitions.counts.properties.praise).toMatchObject({
      type: "integer",
      minimum: 0,
      maximum: 25
    });
    expect(definitions.tempoPolicy.properties.maxJumpBpm).toMatchObject({
      type: "integer",
      minimum: 1,
      maximum: 60
    });
    expect(definitions.keyPolicy.properties.maxKeyCenters).toMatchObject({
      type: "integer",
      minimum: 1,
      maximum: 12
    });
    expect(definitions.generateSetlistSlots.additionalProperties).toBe(false);
    expect(definitions.generateSetlistIntent.additionalProperties).toBe(false);
    expect(definitions.serviceMoment.enum).toEqual([
      "opening",
      "communion",
      "response",
      "altar_call",
      "sending",
      "other",
      null
    ]);
  });
});
