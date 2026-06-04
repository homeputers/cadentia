#!/usr/bin/env node
import { createLifecycleWorkflowPlan } from "../dist/index.js";

const args = process.argv.slice(2);
const workflow = readOption("workflow") ?? args.find((arg) => !arg.startsWith("--"));
const packagePath = readOption("package");
const manifestPath = readOption("manifest");
const outputDir = readOption("output-dir") ?? "deployment/provisioned";
const applicationVersion = readOption("app-version");
const operatorId = readOption("operator") ?? process.env.USER ?? "unknown-operator";
const reason = readOption("reason") ?? "unspecified operator lifecycle workflow";
const backupManifestPath = readOption("backup-manifest");
const restoreBackupPath = readOption("restore-backup");
const sourceManifestPath = readOption("source-manifest");

if (!workflow || !packagePath || !manifestPath) {
  console.error("Usage: plan-instance-lifecycle --workflow=upgrade|backup|restore|export|staging-clone --package=<cadentia-church-package.json> --manifest=<provisioning-manifest.json> [--backup-manifest=<backup.json>] [--restore-backup=<uri>] [--source-manifest=<manifest.json>] [--output-dir=deployment/provisioned] [--app-version=0.1.0] [--operator=ops@example.org] [--reason='change request']");
  process.exit(2);
}

try {
  const result = await createLifecycleWorkflowPlan({
    workflow,
    packagePath,
    manifestPath,
    outputDir,
    applicationVersion,
    operatorId,
    reason,
    backupManifestPath,
    restoreBackupPath,
    sourceManifestPath
  });
  console.log(JSON.stringify({ ok: true, planPath: result.planPath, workflow: result.plan.workflow, instanceId: result.plan.instanceId, verificationCommands: result.plan.verificationCommands }, null, 2));
} catch (error) {
  console.error(JSON.stringify({ ok: false, message: error instanceof Error ? error.message : String(error) }, null, 2));
  process.exit(1);
}

function readOption(name) {
  const prefix = `--${name}=`;
  return args.find((arg) => arg.startsWith(prefix))?.slice(prefix.length);
}
