#!/usr/bin/env node
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

const args = process.argv.slice(2);
const planPath = readOption("plan") ?? args.find((arg) => !arg.startsWith("--"));
if (!planPath) {
  console.error("Usage: verify-lifecycle-workflow --plan=<lifecycle-plan.json>");
  process.exit(2);
}

const resolvedPlanPath = resolve(planPath);
const failures = [];
if (!existsSync(resolvedPlanPath)) {
  failures.push(`Lifecycle plan does not exist: ${resolvedPlanPath}`);
} else {
  const planText = readFileSync(resolvedPlanPath, "utf8");
  rejectPlaintextSecrets(planText, "lifecycle plan");
  const plan = JSON.parse(planText);
  assertEquals(plan.workflowVersion, "cadentia.lifecycle.v1", "workflow version");
  requireOneOf(plan.workflow, ["upgrade", "backup", "restore", "export", "staging-clone"], "workflow");
  assertText(plan.instanceId, "instance id");
  assertText(plan.environment, "environment");
  assertText(plan.operator?.id, "operator id");
  assertText(plan.operator?.reason, "operator reason");
  assertEquals(plan.compatibility?.status, "validated", "compatibility status");
  assertEquals(plan.resourceScope?.secretReferencesOnly, true, "secret references only");
  assertEquals(plan.resourceScope?.crossInstanceNormalUserReadsAllowed, false, "normal user cross-instance reads");
  assertEquals(plan.resourceScope?.starterCatalogEligibility, "instance-local-approval-required", "starter catalog eligibility");
  if (["upgrade", "restore", "staging-clone"].includes(plan.workflow)) {
    assertEquals(plan.compatibility?.backupValidated, true, `${plan.workflow} backup validation`);
  }
  if (plan.workflow === "export") {
    assertEquals(plan.exportPolicy?.churchOwnedDataOnly, true, "export church-owned scope");
    assertEquals(plan.exportPolicy?.excludesOperatorSecrets, true, "export secret redaction");
    assertEquals(plan.exportPolicy?.excludesOtherInstances, true, "export instance isolation");
  }
  if (plan.workflow === "staging-clone") {
    if (plan.environment === "production") {
      failures.push("staging clone target environment must not be production");
    }
    assertEquals(plan.clonePolicy?.productionSecretsCopied, false, "clone production secret handling");
    assertEquals(plan.clonePolicy?.integrations, "disabled-or-overridden", "clone integration handling");
  }
  for (const command of plan.verificationCommands ?? []) {
    if (typeof command !== "string" || command.length === 0) {
      failures.push("verification commands must be non-empty strings");
    }
  }
}

if (failures.length > 0) {
  console.error(JSON.stringify({ ok: false, failures }, null, 2));
  process.exit(1);
}
console.log(JSON.stringify({ ok: true, checked: ["lifecycle-plan", "compatibility", "audit-evidence", "secret-redaction", "instance-scope"] }, null, 2));

function readOption(name) {
  const prefix = `--${name}=`;
  return args.find((arg) => arg.startsWith(prefix))?.slice(prefix.length);
}

function assertEquals(actual, expected, label) {
  if (actual !== expected) {
    failures.push(`${label} expected ${expected} but found ${actual}`);
  }
}

function assertText(value, label) {
  if (typeof value !== "string" || value.length === 0) {
    failures.push(`${label} must be non-empty text`);
  }
}

function requireOneOf(actual, expected, label) {
  if (!expected.includes(actual)) {
    failures.push(`${label} expected one of ${expected.join(", ")} but found ${actual}`);
  }
}

function rejectPlaintextSecrets(text, label) {
  const redFlags = [/password\s*=/i, /token\s*[:=]\s*[A-Za-z0-9_-]{16,}/i, /secret\s*[:=]\s*[A-Za-z0-9_-]{16,}/i, /jdbc:postgresql:\/\/[^\n]+:[^\n@]+@/i];
  for (const redFlag of redFlags) {
    if (redFlag.test(text)) {
      failures.push(`${label} appears to contain plaintext credential material matching ${redFlag}`);
    }
  }
}
