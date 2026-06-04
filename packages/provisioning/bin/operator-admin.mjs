#!/usr/bin/env node
import { authorizeOperatorAuditQuery, queryOperatorAuditRecords, runOperatorAdminAction } from "../dist/index.js";

const args = process.argv.slice(2);
const action = readOption("action") ?? args.find((arg) => !arg.startsWith("--"));
const outputDir = readOption("output-dir") ?? "deployment/provisioned";

if (action === "query-audit") {
  const credentialPath = readOption("credential");
  if (!credentialPath) {
    console.error("Usage: operator-admin --action=query-audit --credential=<operator-credential.json> [--audit-log=<operator-audit.jsonl>] [--operator=<id>] [--target-instance=<id>] [--filter-action=<action>] [--from=<iso>] [--to=<iso>]");
    process.exit(2);
  }
  const auditLogPath = readOption("audit-log") ?? `${outputDir}/operator-audit/operator-audit.jsonl`;
  authorizeOperatorAuditQuery(credentialPath, readOption("target-instance"));
  const records = queryOperatorAuditRecords({
    auditLogPath,
    operatorId: readOption("operator"),
    instanceId: readOption("target-instance"),
    action: readOption("filter-action"),
    from: readOption("from"),
    to: readOption("to")
  });
  console.log(JSON.stringify({ ok: true, count: records.length, records }, null, 2));
  process.exit(0);
}

const credentialPath = readOption("credential");
const targetInstanceId = readOption("target-instance");
const reason = readOption("reason");
if (!action || !credentialPath || !targetInstanceId || !reason) {
  console.error("Usage: operator-admin --action=list|inspect|upgrade|backup|restore|export|clone --credential=<operator-credential.json> --target-instance=<instance-id> --reason='ticket/reason' --manifest=<manifest.json>|--lifecycle-plan=<path>|--before-ref=<path>|--after-ref=<path> [--output-dir=deployment/provisioned]");
  console.error("       operator-admin --action=query-audit --credential=<operator-credential.json> [--audit-log=<operator-audit.jsonl>] [--operator=<id>] [--target-instance=<id>] [--filter-action=<action>] [--from=<iso>] [--to=<iso>]");
  process.exit(2);
}

try {
  const result = await runOperatorAdminAction({
    action,
    credentialPath,
    targetInstanceId,
    reason,
    outputDir,
    manifestPath: readOption("manifest"),
    packagePath: readOption("package"),
    beforeRef: readOption("before-ref"),
    afterRef: readOption("after-ref"),
    lifecyclePlanPath: readOption("lifecycle-plan")
  });
  console.log(JSON.stringify({
    ok: true,
    action: result.action,
    targetInstanceId: result.targetInstanceId,
    operatorId: result.operatorId,
    auditLogPath: result.auditLogPath,
    operationId: result.auditRecord.operationId,
    recordHash: result.auditRecord.recordHash,
    summary: result.summary
  }, null, 2));
} catch (error) {
  console.error(JSON.stringify({ ok: false, message: error instanceof Error ? error.message : String(error) }, null, 2));
  process.exit(1);
}

function readOption(name) {
  const prefix = `--${name}=`;
  return args.find((arg) => arg.startsWith(prefix))?.slice(prefix.length);
}
