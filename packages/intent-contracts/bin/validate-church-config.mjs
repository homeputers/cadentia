#!/usr/bin/env node
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { validateChurchConfigPackage, DEFAULT_CHURCH_CONFIG_APP_VERSION } from "../dist/index.js";

const args = process.argv.slice(2);
const packagePath = args.find((arg) => !arg.startsWith("--"));
const appVersionArg = args.find((arg) => arg.startsWith("--app-version="));
const applicationVersion = appVersionArg?.split("=")[1] ?? DEFAULT_CHURCH_CONFIG_APP_VERSION;

if (!packagePath) {
  console.error("Usage: validate-church-config <package.json> [--app-version=x.y.z]");
  process.exit(2);
}

let payload;
try {
  const resolvedPackagePath = resolveInputPath(packagePath);
  payload = JSON.parse(readFileSync(resolvedPackagePath, "utf8"));
} catch (error) {
  console.error(JSON.stringify({ ok: false, errors: [{ code: "malformed_json", path: "/", message: error instanceof Error ? error.message : String(error) }] }, null, 2));
  process.exit(1);
}

const result = validateChurchConfigPackage(payload, applicationVersion);
if (!result.ok) {
  console.error(JSON.stringify(result, null, 2));
  process.exit(1);
}

console.log(JSON.stringify({ ok: true, instanceId: result.package.instance.instanceId, schemaVersion: result.package.package.schemaVersion, packageVersion: result.package.package.packageVersion, warnings: result.warnings }, null, 2));

function resolveInputPath(inputPath) {
  const cwdPath = resolve(process.cwd(), inputPath);
  if (existsSync(cwdPath)) {
    return cwdPath;
  }
  if (process.env.INIT_CWD) {
    const initPath = resolve(process.env.INIT_CWD, inputPath);
    if (existsSync(initPath)) {
      return initPath;
    }
  }
  return cwdPath;
}
