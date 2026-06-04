#!/usr/bin/env node
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";

const failures = [];
const controllerDir = "apps/api/src/main/java/com/cadentia/api/controller";
const securityFile = "apps/api/src/main/java/com/cadentia/api/security/RbacAuthorities.java";
const operatorTool = "packages/provisioning/bin/operator-admin.mjs";
const provisioningSource = "packages/provisioning/src/index.ts";

const forbiddenNormalApiPatterns = [
  /operator-admin/i,
  /cross-instance/i,
  /operator\.instances\./i,
  /cadentia-operator/i,
  /operator-audit/i
];

for (const file of walk(controllerDir)) {
  const text = readFileSync(file, "utf8");
  for (const pattern of forbiddenNormalApiPatterns) {
    if (pattern.test(text)) {
      failures.push(`Normal application controller ${file} contains operator administration pattern ${pattern}`);
    }
  }
}

const rbac = readFileSync(securityFile, "utf8");
if (/ROLE_OPERATOR|cadentia-operator|operator\.instances\./.test(rbac)) {
  failures.push("Normal church RBAC authorities must not define operator administration roles or scopes");
}

const toolText = readFileSync(operatorTool, "utf8");
const sourceText = readFileSync(provisioningSource, "utf8");
for (const required of ["credential", "target-instance", "reason", "query-audit", "authorizeOperatorAuditQuery"]) {
  if (!toolText.includes(required)) {
    failures.push(`Operator CLI is missing required argument or mode: ${required}`);
  }
}
for (const required of [
  "cadentia.operator-audit.v1",
  "activityType: \"operator-support\"",
  "normalChurchUserRoleAllowed: false",
  "localApprovalBypassAllowed: false",
  "previousRecordHash",
  "recordHash",
  "queryOperatorAuditRecords"
]) {
  if (!sourceText.includes(required)) {
    failures.push(`Operator audit source is missing guardrail: ${required}`);
  }
}

if (failures.length > 0) {
  console.error(JSON.stringify({ ok: false, failures }, null, 2));
  process.exit(1);
}
console.log(JSON.stringify({ ok: true, checked: ["normal-api-no-operator-admin", "normal-rbac-no-operator-role", "operator-cli-explicit-credential-target-reason", "operator-audit-query-and-hash-chain"] }, null, 2));

function walk(dir) {
  const entries = readdirSync(dir).sort();
  const files = [];
  for (const entry of entries) {
    const path = join(dir, entry);
    const stat = statSync(path);
    if (stat.isDirectory()) {
      files.push(...walk(path));
    } else if (path.endsWith(".java")) {
      files.push(path);
    }
  }
  return files;
}
