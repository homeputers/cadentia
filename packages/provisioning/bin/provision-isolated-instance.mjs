#!/usr/bin/env node
import { provisionCadentiaInstance } from "../dist/index.js";

const args = process.argv.slice(2);
const positional = args.filter((arg) => !arg.startsWith("--"));
const packagePath = readOption("package") ?? positional[0];
const outputDir = readOption("output-dir") ?? "deployment/provisioned";
const stateDir = readOption("state-dir") ?? `${outputDir}/state`;
const applicationVersion = readOption("app-version") ?? "0.1.0";
const operatorId = readOption("operator") ?? process.env.USER ?? "unknown-operator";
const action = readOption("action") ?? "provision";
const deploymentMode = readOption("mode") ?? "self-hosted";

if (!packagePath) {
  console.error("Usage: cadentia-provision-instance --package=<cadentia-church-package.json> [--output-dir=deployment/provisioned] [--state-dir=...] [--app-version=0.1.0] [--operator=ops@example.org] [--action=provision|upgrade|reconcile] [--mode=self-hosted|managed-single-tenant|private-cloud|church-managed]");
  process.exit(2);
}

try {
  const result = await provisionCadentiaInstance({
    packagePath,
    outputDir,
    stateDir,
    applicationVersion,
    operatorId,
    action,
    deploymentMode
  });
  console.log(JSON.stringify({ ok: true, manifestPath: result.manifestPath, envPath: result.envPath, statePath: result.statePath, instanceId: result.manifest.instanceId }, null, 2));
} catch (error) {
  console.error(JSON.stringify({ ok: false, message: error instanceof Error ? error.message : String(error) }, null, 2));
  process.exit(1);
}

function readOption(name) {
  const prefix = `--${name}=`;
  return args.find((arg) => arg.startsWith(prefix))?.slice(prefix.length);
}
