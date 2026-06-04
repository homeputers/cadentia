#!/usr/bin/env node
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

const args = process.argv.slice(2);
const manifestPath = readOption("manifest") ?? args.find((arg) => !arg.startsWith("--"));
if (!manifestPath) {
  console.error("Usage: smoke-check-instance --manifest=<provisioning-manifest.json>");
  process.exit(2);
}

const manifest = JSON.parse(readFileSync(resolve(manifestPath), "utf8"));
const failures = [];
assertEquals(manifest.manifestVersion, "cadentia.provisioning.v1", "manifest version");
assertText(manifest.instanceId, "instance id");
assertText(manifest.resources.database.identifier, "database identifier");
assertText(manifest.resources.objectStorage.identifier, "object storage identifier");
assertText(manifest.resources.cache.namespace, "cache namespace");
assertText(manifest.resources.eventStreams.namespace, "event stream namespace");

const envFile = manifest.resources.applicationConfiguration.envFile;
if (!existsSync(envFile)) {
  failures.push(`API env file does not exist: ${envFile}`);
} else {
  const envText = readFileSync(envFile, "utf8");
  requireContains(envText, `CADENTIA_INSTANCE_ID=${manifest.instanceId}`, "instance id env binding");
  requireContains(envText, `CADENTIA_CHURCH_CONFIG_PATH=${manifest.resources.applicationConfiguration.churchConfigPath}`, "church package path env binding");
  requireContains(envText, `CADENTIA_DB_URL_REF=${manifest.resources.database.jdbcUrlRef}`, "database ref env binding");
  requireContains(envText, `CADENTIA_CACHE_NAMESPACE=${manifest.resources.cache.namespace}`, "cache namespace env binding");
  requireContains(envText, `CADENTIA_EVENT_NAMESPACE=${manifest.resources.eventStreams.namespace}`, "event namespace env binding");
  rejectPlaintextSecrets(envText, "API env file");
}

rejectPlaintextSecrets(JSON.stringify(manifest), "manifest");

for (const stream of manifest.resources.eventStreams.streams) {
  if (!stream.startsWith(`${manifest.resources.eventStreams.namespace}.`)) {
    failures.push(`event stream ${stream} is outside namespace ${manifest.resources.eventStreams.namespace}`);
  }
}

for (const secret of manifest.resources.secrets) {
  if (!/^(secret-manager|env|vault|aws-sm|gcp-sm|azure-kv):/.test(secret.ref)) {
    failures.push(`secret binding ${secret.name} is not a reference`);
  }
}

if (failures.length > 0) {
  console.error(JSON.stringify({ ok: false, failures }, null, 2));
  process.exit(1);
}

console.log(JSON.stringify({ ok: true, instanceId: manifest.instanceId, checked: ["manifest", "api-env", "secret-redaction", "namespace-isolation"] }, null, 2));

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

function requireContains(text, needle, label) {
  if (!text.includes(needle)) {
    failures.push(`${label} missing ${needle}`);
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
