import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
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

describe("isolated instance lifecycle workflows", () => {
  it("plans backup and upgrade with compatibility, audit evidence, and deterministic verification commands", async () => {
    const tempDir = mkdtempSync(join(tmpdir(), "cadentia-lifecycle-"));
    tempDirs.push(tempDir);
    const provisioned = await provisionCadentiaInstance({
      packagePath,
      outputDir: join(tempDir, "out"),
      stateDir: join(tempDir, "state"),
      applicationVersion: "0.1.0",
      operatorId: "ops@example.org",
      action: "provision",
      now: new Date("2026-06-04T12:00:00.000Z")
    });
    const { createLifecycleWorkflowPlan } = await import("../src/index.js");

    const backup = await createLifecycleWorkflowPlan({
      workflow: "backup",
      packagePath,
      manifestPath: provisioned.manifestPath,
      outputDir: join(tempDir, "out"),
      applicationVersion: "0.1.0",
      operatorId: "ops@example.org",
      reason: "nightly backup before package upgrade",
      now: new Date("2026-06-04T12:30:00.000Z")
    });
    expect(backup.plan.workflow).toBe("backup");
    expect(backup.plan.resourceScope).toMatchObject({
      secretReferencesOnly: true,
      crossInstanceNormalUserReadsAllowed: false,
      starterCatalogEligibility: "instance-local-approval-required"
    });
    expect(backup.plan.steps).toEqual(expect.arrayContaining([
      "Snapshot the isolated database using the database resource identifier from the manifest.",
      "Copy object storage assets under the manifest namespace prefix only."
    ]));

    const upgrade = await createLifecycleWorkflowPlan({
      workflow: "upgrade",
      packagePath,
      manifestPath: provisioned.manifestPath,
      outputDir: join(tempDir, "out"),
      applicationVersion: "0.1.0",
      operatorId: "ops@example.org",
      reason: "change request CR-22",
      backupManifestPath: backup.planPath,
      now: new Date("2026-06-04T13:00:00.000Z")
    });

    expect(upgrade.plan.compatibility).toMatchObject({
      status: "validated",
      packageVersion: "1.2.3",
      manifestPackageVersion: "1.2.3",
      manifestApplicationVersion: "0.1.0",
      backupValidated: true
    });
    expect(upgrade.plan.compatibility.currentSchemaMigration).toMatch(/^V\d+__.+\.sql$/);
    expect(upgrade.plan.compatibility.targetSchemaMigration).toMatch(/^V\d+__.+\.sql$/);
    expect(upgrade.plan.verificationCommands).toEqual(expect.arrayContaining([
      `node packages/provisioning/bin/verify-lifecycle-workflow.mjs --plan=${upgrade.planPath}`,
      `node packages/provisioning/bin/smoke-check-instance.mjs --manifest=${provisioned.manifestPath}`
    ]));
    expect(JSON.stringify(upgrade.plan)).not.toMatch(/password\s*=/i);
  });

  it("requires a validated backup before upgrade or restore planning", async () => {
    const tempDir = mkdtempSync(join(tmpdir(), "cadentia-lifecycle-"));
    tempDirs.push(tempDir);
    const provisioned = await provisionCadentiaInstance({
      packagePath,
      outputDir: join(tempDir, "out"),
      stateDir: join(tempDir, "state"),
      applicationVersion: "0.1.0",
      operatorId: "ops@example.org"
    });
    const { createLifecycleWorkflowPlan } = await import("../src/index.js");

    await expect(createLifecycleWorkflowPlan({
      workflow: "upgrade",
      packagePath,
      manifestPath: provisioned.manifestPath,
      outputDir: join(tempDir, "out"),
      operatorId: "ops@example.org",
      reason: "unsafe migration"
    })).rejects.toThrow("requires --backup-manifest");
  });

  it("plans exports and staging clones without operator secrets or production secret copying", async () => {
    const tempDir = mkdtempSync(join(tmpdir(), "cadentia-lifecycle-"));
    tempDirs.push(tempDir);
    const provisioned = await provisionCadentiaInstance({
      packagePath,
      outputDir: join(tempDir, "out"),
      stateDir: join(tempDir, "state"),
      applicationVersion: "0.1.0",
      operatorId: "ops@example.org"
    });
    const { createLifecycleWorkflowPlan } = await import("../src/index.js");
    const backup = await createLifecycleWorkflowPlan({
      workflow: "backup",
      packagePath,
      manifestPath: provisioned.manifestPath,
      outputDir: join(tempDir, "out"),
      operatorId: "ops@example.org",
      reason: "source backup for clone"
    });

    const exportPlan = await createLifecycleWorkflowPlan({
      workflow: "export",
      packagePath,
      manifestPath: provisioned.manifestPath,
      outputDir: join(tempDir, "out"),
      operatorId: "ops@example.org",
      reason: "church-requested data portability"
    });
    expect(exportPlan.plan.exportPolicy).toEqual({
      churchOwnedDataOnly: true,
      excludesOperatorSecrets: true,
      excludesOtherInstances: true
    });

    const clonePlan = await createLifecycleWorkflowPlan({
      workflow: "staging-clone",
      packagePath,
      manifestPath: provisioned.manifestPath,
      outputDir: join(tempDir, "out"),
      operatorId: "ops@example.org",
      reason: "release candidate staging validation",
      backupManifestPath: backup.planPath,
      sourceManifestPath: provisioned.manifestPath
    });
    expect(clonePlan.plan.clonePolicy).toMatchObject({
      sourceInstanceId: "river-city-worship",
      sourceEnvironment: "staging",
      productionSecretsCopied: false,
      integrations: "disabled-or-overridden"
    });
    expect(clonePlan.plan.steps).toEqual(expect.arrayContaining([
      "Replace every secret binding with staging-safe references; do not copy production secret values.",
      "Disable or override outbound integrations, telemetry exports, webhooks, and scheduled jobs for staging."
    ]));
  });
});


describe("cross-instance operator administration", () => {
  it("requires scoped operator credentials, explicit target instance, reason, and writes hash-chained support audit records", async () => {
    const tempDir = mkdtempSync(join(tmpdir(), "cadentia-operator-admin-"));
    tempDirs.push(tempDir);
    const provisioned = await provisionCadentiaInstance({
      packagePath,
      outputDir: join(tempDir, "out"),
      stateDir: join(tempDir, "state"),
      applicationVersion: "0.1.0",
      operatorId: "provisioner@example.org",
      now: new Date("2026-06-04T12:00:00.000Z")
    });
    const credentialPath = join(tempDir, "operator-credential.json");
    writeFileSync(credentialPath, JSON.stringify({
      credentialVersion: "cadentia.operator-credential.v1",
      credentialId: "cred-2026-06-04T12",
      operatorId: "ops@example.org",
      role: "cadentia-operator",
      issuedAt: "2026-06-04T11:00:00.000Z",
      expiresAt: "2026-06-04T15:00:00.000Z",
      scopes: ["operator.instances.inspect", "operator.instances.backup", "operator.audit.query"],
      allowedInstanceIds: ["river-city-worship"],
      issuer: "security@example.org",
      publicKeyRef: "kms:/cadentia/operators/ops@example.org/2026-06-04"
    }, null, 2));
    const { queryOperatorAuditRecords, runOperatorAdminAction } = await import("../src/index.js");

    const inspect = await runOperatorAdminAction({
      action: "inspect",
      credentialPath,
      targetInstanceId: "river-city-worship",
      reason: "support ticket SUP-100 inspect package version",
      outputDir: join(tempDir, "out"),
      manifestPath: provisioned.manifestPath,
      now: new Date("2026-06-04T12:05:00.000Z")
    });
    const backup = await runOperatorAdminAction({
      action: "backup",
      credentialPath,
      targetInstanceId: "river-city-worship",
      reason: "support ticket SUP-100 pre-maintenance backup",
      outputDir: join(tempDir, "out"),
      manifestPath: provisioned.manifestPath,
      beforeRef: provisioned.manifestPath,
      now: new Date("2026-06-04T12:10:00.000Z")
    });

    expect(inspect.auditRecord).toMatchObject({
      recordVersion: "cadentia.operator-audit.v1",
      activityType: "operator-support",
      action: "inspect",
      operator: { id: "ops@example.org", credentialId: "cred-2026-06-04T12" },
      target: { instanceId: "river-city-worship", environment: "staging" },
      dataPolicy: {
        secretValuesLogged: false,
        privateLyricsLogged: false,
        sensitivePersonalDataLogged: false,
        normalChurchUserRoleAllowed: false,
        localApprovalBypassAllowed: false
      }
    });
    expect(backup.auditRecord.previousRecordHash).toBe(inspect.auditRecord.recordHash);
    expect(backup.auditRecord.references.beforeHash).toMatch(/^sha256:/);
    expect(readFileSync(backup.auditLogPath, "utf8")).not.toMatch(/password\s*=/i);

    const queried = queryOperatorAuditRecords({
      auditLogPath: backup.auditLogPath,
      operatorId: "ops@example.org",
      instanceId: "river-city-worship",
      action: "backup",
      from: "2026-06-04T12:00:00.000Z",
      to: "2026-06-04T12:30:00.000Z"
    });
    expect(queried).toHaveLength(1);
    expect(queried[0].operationId).toBe(backup.auditRecord.operationId);
  });

  it("rejects normal church roles, missing scopes, expired credentials, and mismatched target manifests", async () => {
    const tempDir = mkdtempSync(join(tmpdir(), "cadentia-operator-admin-"));
    tempDirs.push(tempDir);
    const provisioned = await provisionCadentiaInstance({
      packagePath,
      outputDir: join(tempDir, "out"),
      stateDir: join(tempDir, "state"),
      applicationVersion: "0.1.0",
      operatorId: "provisioner@example.org"
    });
    const { runOperatorAdminAction } = await import("../src/index.js");
    const baseCredential = {
      credentialVersion: "cadentia.operator-credential.v1",
      credentialId: "cred-test",
      operatorId: "ops@example.org",
      role: "cadentia-operator",
      issuedAt: "2026-06-04T11:00:00.000Z",
      expiresAt: "2026-06-04T15:00:00.000Z",
      scopes: ["operator.instances.inspect"],
      allowedInstanceIds: ["river-city-worship"],
      issuer: "security@example.org"
    };
    const credentialPath = join(tempDir, "credential.json");
    const writeCredential = (credential: Record<string, unknown>) => writeFileSync(credentialPath, JSON.stringify(credential, null, 2));

    writeCredential({ ...baseCredential, role: "role.admin" });
    await expect(runOperatorAdminAction({
      action: "inspect",
      credentialPath,
      targetInstanceId: "river-city-worship",
      reason: "support ticket SUP-200 role rejection",
      outputDir: join(tempDir, "out"),
      manifestPath: provisioned.manifestPath,
      now: new Date("2026-06-04T12:05:00.000Z")
    })).rejects.toThrow("Normal church user roles");

    writeCredential({ ...baseCredential, expiresAt: "2026-06-04T12:00:00.000Z" });
    await expect(runOperatorAdminAction({
      action: "inspect",
      credentialPath,
      targetInstanceId: "river-city-worship",
      reason: "support ticket SUP-200 expired credential",
      outputDir: join(tempDir, "out"),
      manifestPath: provisioned.manifestPath,
      now: new Date("2026-06-04T12:05:00.000Z")
    })).rejects.toThrow("not valid");

    writeCredential(baseCredential);
    await expect(runOperatorAdminAction({
      action: "backup",
      credentialPath,
      targetInstanceId: "river-city-worship",
      reason: "support ticket SUP-200 missing scope",
      outputDir: join(tempDir, "out"),
      manifestPath: provisioned.manifestPath,
      now: new Date("2026-06-04T12:05:00.000Z")
    })).rejects.toThrow("missing required scope operator.instances.backup");

    await expect(runOperatorAdminAction({
      action: "inspect",
      credentialPath,
      targetInstanceId: "other-church",
      reason: "support ticket SUP-200 target mismatch",
      outputDir: join(tempDir, "out"),
      manifestPath: provisioned.manifestPath,
      now: new Date("2026-06-04T12:05:00.000Z")
    })).rejects.toThrow("not scoped to target instance other-church");
  });
});
