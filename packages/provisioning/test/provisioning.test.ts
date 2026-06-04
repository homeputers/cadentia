import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";

import { provisionCadentiaInstance } from "../src/index.js";

const packagePath = new URL("../../intent-contracts/fixtures/church-config/v1/valid/complete-package.json", import.meta.url).pathname;
let tempDirs: string[] = [];

afterEach(() => {
  for (const tempDir of tempDirs) {
    rmSync(tempDir, { recursive: true, force: true });
  }
  tempDirs = [];
});

describe("isolated instance provisioning", () => {
  it("creates an auditable manifest and API env file with isolated resources", async () => {
    const tempDir = mkdtempSync(join(tmpdir(), "cadentia-provisioning-"));
    tempDirs.push(tempDir);

    const result = await provisionCadentiaInstance({
      packagePath,
      outputDir: join(tempDir, "out"),
      stateDir: join(tempDir, "state"),
      applicationVersion: "0.1.0",
      operatorId: "ops@example.org",
      action: "provision",
      deploymentMode: "managed-single-tenant",
      now: new Date("2026-06-04T12:00:00.000Z")
    });

    expect(result.manifest).toMatchObject({
      manifestVersion: "cadentia.provisioning.v1",
      instanceId: "river-city-worship",
      packageVersion: "1.2.3",
      applicationVersion: "0.1.0",
      deploymentMode: "managed-single-tenant",
      operator: { id: "ops@example.org", action: "provision" }
    });
    expect(result.manifest.resources.database.identifier).toBe("cadentia_river_city_worship_staging");
    expect(result.manifest.resources.objectStorage).toMatchObject({
      identifier: "cadentia-river-city-assets",
      namespacePrefix: "river-city-worship"
    });
    expect(result.manifest.resources.cache.namespace).toBe("cadentia:staging:river-city-worship");
    expect(result.manifest.resources.eventStreams.streams).toEqual(
      expect.arrayContaining([
        "staging.river-city-worship.audit-events",
        "staging.river-city-worship.catalog-import"
      ])
    );
    expect(result.manifest.resources.secrets.map((secret) => secret.ref)).toEqual(
      expect.arrayContaining([
        "secret-manager:/cadentia/river-city-worship/staging/database-url",
        "secret-manager:/cadentia/river-city/telegram-token",
        "env:PLANNING_CENTER_TOKEN"
      ])
    );

    const envText = readFileSync(result.envPath, "utf8");
    expect(envText).toContain("CADENTIA_INSTANCE_ID=river-city-worship");
    expect(envText).toContain("CADENTIA_DB_URL_REF=secret-manager:/cadentia/river-city-worship/staging/database-url");
    expect(envText).toContain("CADENTIA_CACHE_NAMESPACE=cadentia:staging:river-city-worship");
    expect(envText).not.toMatch(/password\s*=/i);
    expect(JSON.stringify(result.manifest)).not.toMatch(/jdbc:postgresql:\/\/[^\n]+:[^\n@]+@/i);
  });

  it("is idempotent and preserves resource identifiers on rerun", async () => {
    const tempDir = mkdtempSync(join(tmpdir(), "cadentia-provisioning-"));
    tempDirs.push(tempDir);
    const commonOptions = {
      packagePath,
      outputDir: join(tempDir, "out"),
      stateDir: join(tempDir, "state"),
      applicationVersion: "0.1.0",
      operatorId: "ops@example.org",
      deploymentMode: "self-hosted",
      now: new Date("2026-06-04T12:00:00.000Z")
    };

    const first = await provisionCadentiaInstance({ ...commonOptions, action: "provision" });
    const second = await provisionCadentiaInstance({ ...commonOptions, action: "reconcile" });

    expect(second.manifest.resources).toEqual(first.manifest.resources);
    expect(second.manifest.idempotency.key).toEqual(first.manifest.idempotency.key);
    expect(second.manifest.idempotency.previousManifestDigest).toBeDefined();
  });

  it("rejects invalid church packages before allocating resources", async () => {
    const tempDir = mkdtempSync(join(tmpdir(), "cadentia-provisioning-"));
    tempDirs.push(tempDir);
    const invalidPackagePath = new URL("../../intent-contracts/fixtures/church-config/v1/invalid/plaintext-integration-secret.json", import.meta.url).pathname;

    await expect(provisionCadentiaInstance({
      packagePath: invalidPackagePath,
      outputDir: join(tempDir, "out"),
      stateDir: join(tempDir, "state"),
      operatorId: "ops@example.org"
    })).rejects.toThrow("failed validation");
  });
});
