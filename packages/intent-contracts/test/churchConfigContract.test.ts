import { describe, expect, it } from "vitest";
import { readFileSync, readdirSync } from "node:fs";

import {
  CHURCH_CONFIG_SCHEMA_VERSION,
  churchConfigPackageV1SchemaPath,
  parseChurchConfigPackage,
  validateChurchConfigPackage
} from "../src/index.js";

const fixturesRoot = new URL("../fixtures/church-config/v1/", import.meta.url);

function fixture(kind: "valid" | "invalid", name: string): unknown {
  return JSON.parse(readFileSync(new URL(`${kind}/${name}`, fixturesRoot), "utf8"));
}

describe("church configuration package contract", () => {
  it("exposes the canonical v1 schema artifact", () => {
    const schema = JSON.parse(readFileSync(churchConfigPackageV1SchemaPath, "utf8"));

    expect(CHURCH_CONFIG_SCHEMA_VERSION).toBe("church-config.v1");
    expect(schema.$id).toContain("/schemas/church-config/v1/church-config-package.schema.json");
    expect(schema.required).toEqual(
      expect.arrayContaining([
        "instance",
        "modules",
        "policies",
        "scoringProfiles",
        "vocabularies",
        "approvalGates",
        "workflowDefaults",
        "branding",
        "integrations",
        "pluginAllowList",
        "assetStorage",
        "featureFlags",
        "observability"
      ])
    );
  });

  it("accepts a complete package with all ADR-022 categories", () => {
    const payload = fixture("valid", "complete-package.json");

    const result = validateChurchConfigPackage(payload, "0.1.0");

    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.package.instance.instanceId).toBe("river-city-worship");
      expect(result.package.policies.recommendationPolicy.requireApprovedOnly).toBe(true);
      expect(result.package.assetStorage.encryptionKeyRef).toMatch(/^aws-sm:/);
    }
    expect(() => parseChurchConfigPackage(payload)).not.toThrow();
  });

  it("accepts distinct isolated-instance package fixtures for ADR-022 regression coverage", () => {
    const river = fixture("valid", "river-city-isolation-package.json");
    const hillside = fixture("valid", "hillside-isolation-package.json");

    const riverResult = validateChurchConfigPackage(river, "0.1.0");
    const hillsideResult = validateChurchConfigPackage(hillside, "0.1.0");

    expect(riverResult.ok).toBe(true);
    expect(hillsideResult.ok).toBe(true);
    if (riverResult.ok && hillsideResult.ok) {
      expect(riverResult.package.instance.instanceId).toBe("river-city-isolation");
      expect(hillsideResult.package.instance.instanceId).toBe("hillside-isolation");
      expect(riverResult.package.policies.recommendationPolicy.counts).not.toEqual(
        hillsideResult.package.policies.recommendationPolicy.counts
      );
      expect(riverResult.package.scoringProfiles.activeProfile).not.toBe(
        hillsideResult.package.scoringProfiles.activeProfile
      );
      expect(riverResult.package.branding.primaryColor).not.toBe(hillsideResult.package.branding.primaryColor);
      expect(riverResult.package.assetStorage.namespacePrefix).not.toBe(
        hillsideResult.package.assetStorage.namespacePrefix
      );
    }
  });

  it.each(readdirSync(new URL("invalid/", fixturesRoot)))
    ("rejects invalid package fixture %s before provisioning or startup", (fixtureName) => {
      const payload = fixture("invalid", fixtureName);

      const result = validateChurchConfigPackage(payload, "0.1.0");

      expect(result.ok, fixtureName).toBe(false);
      if (!result.ok) {
        expect(result.errors.length).toBeGreaterThan(0);
      }
      expect(() => parseChurchConfigPackage(payload)).toThrow();
    });

  it("returns a specific compatibility error when the app release is outside the declared range", () => {
    const result = validateChurchConfigPackage(
      fixture("invalid", "incompatible-application-version.json"),
      "0.1.0"
    );

    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.errors).toContainEqual(
        expect.objectContaining({
          code: "incompatible_application_version",
          path: "/package/applicationCompatibility"
        })
      );
    }
  });
});
