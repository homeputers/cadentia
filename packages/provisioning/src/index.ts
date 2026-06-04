import { createHash } from "node:crypto";
import { appendFileSync, existsSync, mkdirSync, readFileSync, readdirSync, renameSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";

import {
  DEFAULT_CHURCH_CONFIG_APP_VERSION,
  type ChurchConfigPackage,
  validateChurchConfigPackage
} from "@cadentia/intent-contracts";

export type DeploymentMode = "self-hosted" | "private-cloud" | "managed-single-tenant" | "church-managed";
export type ProvisioningAction = "provision" | "upgrade" | "reconcile" | "rollback";
export type LifecycleWorkflowType = "upgrade" | "backup" | "restore" | "export" | "staging-clone";

export type LifecycleWorkflowOptions = {
  workflow: LifecycleWorkflowType;
  packagePath: string;
  manifestPath: string;
  outputDir: string;
  applicationVersion?: string;
  operatorId: string;
  reason: string;
  backupManifestPath?: string;
  restoreBackupPath?: string;
  sourceManifestPath?: string;
  now?: Date;
};

export type LifecycleWorkflowPlan = {
  workflowVersion: "cadentia.lifecycle.v1";
  workflow: LifecycleWorkflowType;
  instanceId: string;
  environment: string;
  operator: {
    id: string;
    reason: string;
    plannedAt: string;
  };
  inputs: {
    packagePath: string;
    packageDigest: string;
    provisioningManifestPath: string;
    provisioningManifestDigest: string;
    backupManifestPath?: string;
    backupManifestDigest?: string;
    restoreBackupPath?: string;
    sourceManifestPath?: string;
    sourceManifestDigest?: string;
  };
  compatibility: {
    status: "validated";
    applicationVersion: string;
    packageSchemaVersion: string;
    packageVersion: string;
    manifestPackageVersion: string;
    manifestApplicationVersion: string;
    currentSchemaMigration: string | null;
    targetSchemaMigration: string | null;
    backupValidated: boolean;
  };
  auditEvidence: {
    operationId: string;
    evidencePath: string;
    records: string[];
  };
  resourceScope: {
    database: string;
    objectStorage: string;
    objectStorageNamespace: string;
    cacheNamespace: string;
    eventNamespace: string;
    secretReferencesOnly: true;
    crossInstanceNormalUserReadsAllowed: false;
    starterCatalogEligibility: "instance-local-approval-required";
  };
  steps: string[];
  verificationCommands: string[];
  rollbackSteps: string[];
  retention: string;
  failureTriage: string[];
  exportPolicy?: {
    churchOwnedDataOnly: true;
    excludesOperatorSecrets: true;
    excludesOtherInstances: true;
  };
  clonePolicy?: {
    sourceInstanceId: string;
    sourceEnvironment: string;
    productionSecretsCopied: false;
    integrations: "disabled-or-overridden";
    provenance: string;
  };
};

export type OperatorAdminAction = "list" | "inspect" | "upgrade" | "backup" | "restore" | "export" | "clone";
export type OperatorAuditAction = OperatorAdminAction | "query-audit";

export type OperatorScope =
  | "operator.instances.list"
  | "operator.instances.inspect"
  | "operator.instances.upgrade"
  | "operator.instances.backup"
  | "operator.instances.restore"
  | "operator.instances.export"
  | "operator.instances.clone"
  | "operator.audit.query";

export type OperatorCredential = {
  credentialVersion: "cadentia.operator-credential.v1";
  credentialId: string;
  operatorId: string;
  role: "cadentia-operator" | "cadentia-break-glass-operator";
  issuedAt: string;
  expiresAt: string;
  scopes: OperatorScope[];
  allowedInstanceIds: string[];
  issuer: string;
  publicKeyRef?: string;
  breakGlass?: {
    incidentId: string;
    approvedBy: string;
  };
};

export type OperatorAdminOptions = {
  action: OperatorAdminAction;
  credentialPath: string;
  targetInstanceId: string;
  reason: string;
  outputDir: string;
  manifestPath?: string;
  packagePath?: string;
  beforeRef?: string;
  afterRef?: string;
  lifecyclePlanPath?: string;
  now?: Date;
};

export type OperatorAuditQuery = {
  auditLogPath: string;
  operatorId?: string;
  instanceId?: string;
  action?: OperatorAuditAction;
  from?: string;
  to?: string;
};

export type OperatorAuditRecord = {
  recordVersion: "cadentia.operator-audit.v1";
  activityType: "operator-support";
  action: OperatorAuditAction;
  operationId: string;
  operator: {
    id: string;
    credentialId: string;
    credentialRole: OperatorCredential["role"];
    credentialScopes: OperatorScope[];
  };
  target: {
    instanceId: string;
    environment?: string;
    manifestPath?: string;
    manifestDigest?: string;
  };
  reason: string;
  occurredAt: string;
  references: {
    beforeRef?: string;
    beforeHash?: string;
    afterRef?: string;
    afterHash?: string;
    lifecyclePlanPath?: string;
    lifecyclePlanDigest?: string;
  };
  dataPolicy: {
    secretValuesLogged: false;
    privateLyricsLogged: false;
    sensitivePersonalDataLogged: false;
    normalChurchUserRoleAllowed: false;
    localApprovalBypassAllowed: false;
  };
  queryKeys: {
    operatorId: string;
    instanceId: string;
    action: OperatorAuditAction;
    occurredAt: string;
  };
  previousRecordHash: string | null;
  recordHash: string;
};

export type OperatorAdminResult = {
  ok: true;
  action: OperatorAdminAction;
  targetInstanceId: string;
  operatorId: string;
  auditLogPath: string;
  auditRecord: OperatorAuditRecord;
  summary: Record<string, unknown>;
};


export type ProvisioningOptions = {
  packagePath: string;
  outputDir: string;
  stateDir: string;
  applicationVersion?: string;
  operatorId: string;
  action?: ProvisioningAction | string;
  deploymentMode?: DeploymentMode | string;
  now?: Date;
};

export type ProvisioningResult = {
  manifest: ProvisioningManifest;
  manifestPath: string;
  envPath: string;
  statePath: string;
};

type ResourceDescriptor = {
  kind: string;
  identifier: string;
  isolationScope: "instance";
  managedBy: "cadentia-provisioning";
};

type SecretBinding = {
  name: string;
  ref: string;
  source: "church-config" | "provisioning";
};

export type ProvisioningManifest = {
  manifestVersion: "cadentia.provisioning.v1";
  instanceId: string;
  environment: string;
  packageVersion: string;
  packageSchemaVersion: string;
  applicationVersion: string;
  packageDigest: string;
  deploymentMode: string;
  operator: {
    id: string;
    action: string;
    executedAt: string;
  };
  resources: {
    database: ResourceDescriptor & { jdbcUrlRef: string; migrationSchemaHistoryTable: string };
    objectStorage: ResourceDescriptor & { provider: string; namespacePrefix: string; encryptionKeyRef: string };
    cache: ResourceDescriptor & { namespace: string; connectionRef: string };
    eventStreams: ResourceDescriptor & { namespace: string; streams: string[] };
    secrets: SecretBinding[];
    applicationConfiguration: ResourceDescriptor & { envFile: string; churchConfigPath: string };
  };
  migrationState: {
    status: "pending" | "applied";
    migrationTool: "spring-flyway";
    locations: string[];
    appliedBy: "api-startup" | "provisioning-smoke";
    latestKnownMigration: string | null;
  };
  idempotency: {
    key: string;
    statePath: string;
    previousManifestDigest?: string;
  };
  smokeChecks: {
    command: string;
    expectedResourceGuards: string[];
  };
};

type ProvisioningState = {
  instanceId: string;
  environment: string;
  createdAt: string;
  resources: ProvisioningManifest["resources"];
  idempotencyKey: string;
  lastManifestDigest?: string;
};

export async function provisionCadentiaInstance(options: ProvisioningOptions): Promise<ProvisioningResult> {
  const applicationVersion = options.applicationVersion ?? DEFAULT_CHURCH_CONFIG_APP_VERSION;
  const packagePath = resolve(options.packagePath);
  const rawPackage = readFileSync(packagePath, "utf8");
  const payload = JSON.parse(rawPackage) as unknown;
  const validationResult = validateChurchConfigPackage(payload, applicationVersion as typeof DEFAULT_CHURCH_CONFIG_APP_VERSION);
  if (!validationResult.ok) {
    throw new Error(`Church configuration package failed validation: ${validationResult.errors.map((error) => `${error.path} ${error.message}`).join("; ")}`);
  }

  const churchPackage = validationResult.package;
  const instanceId = churchPackage.instance.instanceId;
  const environment = churchPackage.instance.environment;
  const statePath = resolve(options.stateDir, `${instanceId}.${environment}.state.json`);
  const existingState = readState(statePath);
  const resources = existingState?.resources ?? allocateResources(churchPackage, options.outputDir, packagePath);
  const latestKnownMigration = findLatestKnownMigration();
  const executedAt = (options.now ?? new Date()).toISOString();
  const manifest: ProvisioningManifest = {
    manifestVersion: "cadentia.provisioning.v1",
    instanceId,
    environment,
    packageVersion: churchPackage.package.packageVersion,
    packageSchemaVersion: churchPackage.package.schemaVersion,
    applicationVersion,
    packageDigest: sha256(rawPackage),
    deploymentMode: options.deploymentMode ?? "self-hosted",
    operator: {
      id: options.operatorId,
      action: options.action ?? "provision",
      executedAt
    },
    resources,
    migrationState: {
      status: "pending",
      migrationTool: "spring-flyway",
      locations: ["apps/api/src/main/resources/db/migration"],
      appliedBy: "api-startup",
      latestKnownMigration
    },
    idempotency: {
      key: idempotencyKey(instanceId, environment),
      statePath,
      previousManifestDigest: existingState?.lastManifestDigest
    },
    smokeChecks: {
      command: `node packages/provisioning/bin/smoke-check-instance.mjs --manifest=${manifestPathFor(options.outputDir, instanceId, environment)}`,
      expectedResourceGuards: [
        "CADENTIA_INSTANCE_ID matches the manifest instance",
        "CADENTIA_CHURCH_CONFIG_PATH points at the validated package",
        "database, cache, event, object storage, and secret references match only this instance's manifest",
        "generated files contain secret references only, never plaintext credentials"
      ]
    }
  };

  const manifestPath = manifestPathFor(options.outputDir, instanceId, environment);
  const envPath = resolve(options.outputDir, "env", `${instanceId}.${environment}.api.env`);
  writeJsonAtomic(manifestPath, manifest);
  writeTextAtomic(envPath, renderApiEnv(manifest));
  const state: ProvisioningState = {
    instanceId,
    environment,
    createdAt: existingState?.createdAt ?? executedAt,
    resources,
    idempotencyKey: manifest.idempotency.key,
    lastManifestDigest: sha256(JSON.stringify(manifest))
  };
  writeJsonAtomic(statePath, state);
  return { manifest, manifestPath, envPath, statePath };
}

function allocateResources(churchPackage: ChurchConfigPackage, outputDir: string, packagePath: string): ProvisioningManifest["resources"] {
  const instanceId = churchPackage.instance.instanceId;
  const environment = churchPackage.instance.environment;
  const safeId = instanceId.replaceAll("-", "_");
  const resourceStem = `${instanceId}-${environment}`;
  const namespace = `${environment}.${instanceId}`;
  const envFile = resolve(outputDir, "env", `${instanceId}.${environment}.api.env`);
  const configuredSecrets = secretBindingsFromPackage(churchPackage);
  return {
    database: {
      kind: "postgres-database",
      identifier: `cadentia_${safeId}_${environment}`,
      isolationScope: "instance",
      managedBy: "cadentia-provisioning",
      jdbcUrlRef: `secret-manager:/cadentia/${instanceId}/${environment}/database-url`,
      migrationSchemaHistoryTable: "flyway_schema_history"
    },
    objectStorage: {
      kind: "object-storage-namespace",
      identifier: churchPackage.assetStorage.bucketOrContainer,
      isolationScope: "instance",
      managedBy: "cadentia-provisioning",
      provider: churchPackage.assetStorage.provider,
      namespacePrefix: churchPackage.assetStorage.namespacePrefix,
      encryptionKeyRef: churchPackage.assetStorage.encryptionKeyRef
    },
    cache: {
      kind: "cache-namespace",
      identifier: `cadentia-cache-${resourceStem}`,
      isolationScope: "instance",
      managedBy: "cadentia-provisioning",
      namespace: `cadentia:${environment}:${instanceId}`,
      connectionRef: `secret-manager:/cadentia/${instanceId}/${environment}/cache-url`
    },
    eventStreams: {
      kind: "event-stream-namespace",
      identifier: `cadentia-events-${resourceStem}`,
      isolationScope: "instance",
      managedBy: "cadentia-provisioning",
      namespace,
      streams: [
        `${namespace}.audit-events`,
        `${namespace}.catalog-import`,
        `${namespace}.recommendation-events`,
        `${namespace}.workflow-events`
      ]
    },
    secrets: [
      { name: "database-url", ref: `secret-manager:/cadentia/${instanceId}/${environment}/database-url`, source: "provisioning" },
      { name: "cache-url", ref: `secret-manager:/cadentia/${instanceId}/${environment}/cache-url`, source: "provisioning" },
      ...configuredSecrets
    ],
    applicationConfiguration: {
      kind: "api-runtime-configuration",
      identifier: `cadentia-api-config-${resourceStem}`,
      isolationScope: "instance",
      managedBy: "cadentia-provisioning",
      envFile,
      churchConfigPath: packagePath
    }
  };
}

function secretBindingsFromPackage(churchPackage: ChurchConfigPackage): SecretBinding[] {
  const bindings = new Map<string, SecretBinding>();
  bindings.set("asset-storage-encryption-key", {
    name: "asset-storage-encryption-key",
    ref: churchPackage.assetStorage.encryptionKeyRef,
    source: "church-config"
  });
  for (const provider of churchPackage.integrations.providers) {
    bindings.set(`integration-${provider.ref}`, {
      name: `integration-${provider.ref}`,
      ref: provider.secretRef ?? `secret-manager:/cadentia/${churchPackage.instance.instanceId}/${churchPackage.instance.environment}/integrations/${provider.ref}`,
      source: "church-config"
    });
  }
  if (churchPackage.observability.exports.secretRef) {
    bindings.set("observability-export-writer", {
      name: "observability-export-writer",
      ref: churchPackage.observability.exports.secretRef,
      source: "church-config"
    });
  }
  return [...bindings.values()].sort((left, right) => left.name.localeCompare(right.name));
}

function renderApiEnv(manifest: ProvisioningManifest): string {
  return [
    "# Generated by Cadentia isolated-instance provisioning. Values ending in _REF must be resolved by the operator secret runtime.",
    `CADENTIA_INSTANCE_ID=${manifest.instanceId}`,
    `CADENTIA_APPLICATION_VERSION=${manifest.applicationVersion}`,
    `CADENTIA_CHURCH_CONFIG_PATH=${manifest.resources.applicationConfiguration.churchConfigPath}`,
    `CADENTIA_DB_URL_REF=${manifest.resources.database.jdbcUrlRef}`,
    `CADENTIA_ASSET_STORAGE_PROVIDER=${manifest.resources.objectStorage.provider}`,
    `CADENTIA_ASSET_STORAGE_BUCKET=${manifest.resources.objectStorage.identifier}`,
    `CADENTIA_ASSET_STORAGE_NAMESPACE=${manifest.resources.objectStorage.namespacePrefix}`,
    `CADENTIA_ASSET_STORAGE_ENCRYPTION_KEY_REF=${manifest.resources.objectStorage.encryptionKeyRef}`,
    `CADENTIA_CACHE_NAMESPACE=${manifest.resources.cache.namespace}`,
    `CADENTIA_CACHE_URL_REF=${manifest.resources.cache.connectionRef}`,
    `CADENTIA_EVENT_NAMESPACE=${manifest.resources.eventStreams.namespace}`,
    `CADENTIA_EVENT_STREAMS=${manifest.resources.eventStreams.streams.join(",")}`,
    `CADENTIA_PROVISIONING_MANIFEST=${manifest.instanceId}:${manifest.packageVersion}:${manifest.packageDigest}`,
    ""
  ].join("\n");
}

function findLatestKnownMigration(): string | null {
  const candidateDirs = [
    resolve("apps/api/src/main/resources/db/migration"),
    resolve("../../apps/api/src/main/resources/db/migration")
  ];
  const migrationDir = candidateDirs.find((candidate) => existsSync(candidate));
  if (!migrationDir) {
    return null;
  }
  const migrations = readdirSync(migrationDir)
    .filter((name) => /^V\d+__.+\.sql$/.test(name))
    .sort((left, right) => left.localeCompare(right, undefined, { numeric: true }));
  return migrations.at(-1) ?? null;
}

function readState(statePath: string): ProvisioningState | null {
  if (!existsSync(statePath)) {
    return null;
  }
  return JSON.parse(readFileSync(statePath, "utf8")) as ProvisioningState;
}

function manifestPathFor(outputDir: string, instanceId: string, environment: string): string {
  return resolve(outputDir, "manifests", `${instanceId}.${environment}.provisioning-manifest.json`);
}

function idempotencyKey(instanceId: string, environment: string): string {
  return sha256(`${instanceId}:${environment}`).slice(0, 32);
}

function sha256(value: string): string {
  return `sha256:${createHash("sha256").update(value).digest("hex")}`;
}

function writeJsonAtomic(path: string, payload: unknown): void {
  writeTextAtomic(path, `${JSON.stringify(payload, null, 2)}\n`);
}

function writeTextAtomic(path: string, text: string): void {
  mkdirSync(dirname(path), { recursive: true });
  const temporaryPath = `${path}.${process.pid}.tmp`;
  writeFileSync(temporaryPath, text, "utf8");
  renameSync(temporaryPath, path);
}

export async function runOperatorAdminAction(options: OperatorAdminOptions): Promise<OperatorAdminResult> {
  const credential = readOperatorCredential(options.credentialPath);
  const occurredAt = (options.now ?? new Date()).toISOString();
  validateOperatorCredential(credential, options.action, options.targetInstanceId, occurredAt, options.reason);
  if (!options.manifestPath && !options.beforeRef && !options.afterRef && !options.lifecyclePlanPath) {
    throw new Error("Operator action requires a manifest, lifecycle plan, or before/after reference");
  }
  const manifestInfo = options.manifestPath ? readManifestSummary(options.manifestPath, options.targetInstanceId) : undefined;
  const targetEnvironment = manifestInfo?.environment;
  const auditLogPath = resolve(options.outputDir, "operator-audit", "operator-audit.jsonl");
  const references = readOperatorReferences(options.beforeRef, options.afterRef, options.lifecyclePlanPath);
  const operationId = `${options.action}-${options.targetInstanceId}-${sha256(`${occurredAt}:${credential.credentialId}:${options.reason}`).slice(7, 19)}`;
  const previousRecordHash = lastOperatorAuditHash(auditLogPath);
  const unsignedRecord = {
    recordVersion: "cadentia.operator-audit.v1" as const,
    activityType: "operator-support" as const,
    action: options.action,
    operationId,
    operator: {
      id: credential.operatorId,
      credentialId: credential.credentialId,
      credentialRole: credential.role,
      credentialScopes: credential.scopes
    },
    target: {
      instanceId: options.targetInstanceId,
      environment: targetEnvironment,
      manifestPath: options.manifestPath ? resolve(options.manifestPath) : undefined,
      manifestDigest: manifestInfo?.digest
    },
    reason: options.reason,
    occurredAt,
    references,
    dataPolicy: {
      secretValuesLogged: false as const,
      privateLyricsLogged: false as const,
      sensitivePersonalDataLogged: false as const,
      normalChurchUserRoleAllowed: false as const,
      localApprovalBypassAllowed: false as const
    },
    queryKeys: {
      operatorId: credential.operatorId,
      instanceId: options.targetInstanceId,
      action: options.action,
      occurredAt
    },
    previousRecordHash
  };
  const recordHash = sha256(stableJson(unsignedRecord));
  const auditRecord: OperatorAuditRecord = { ...unsignedRecord, recordHash };
  rejectSensitiveOperatorAuditRecord(auditRecord);
  appendOperatorAuditRecord(auditLogPath, auditRecord);
  return {
    ok: true,
    action: options.action,
    targetInstanceId: options.targetInstanceId,
    operatorId: credential.operatorId,
    auditLogPath,
    auditRecord,
    summary: operatorSummary(options.action, manifestInfo, references)
  };
}

export function queryOperatorAuditRecords(query: OperatorAuditQuery): OperatorAuditRecord[] {
  const auditLogPath = resolve(query.auditLogPath);
  if (!existsSync(auditLogPath)) {
    return [];
  }
  const from = query.from ? Date.parse(query.from) : Number.NEGATIVE_INFINITY;
  const to = query.to ? Date.parse(query.to) : Number.POSITIVE_INFINITY;
  return readFileSync(auditLogPath, "utf8")
    .split("\n")
    .filter((line) => line.trim().length > 0)
    .map((line) => JSON.parse(line) as OperatorAuditRecord)
    .filter((record) => !query.operatorId || record.queryKeys.operatorId === query.operatorId)
    .filter((record) => !query.instanceId || record.queryKeys.instanceId === query.instanceId)
    .filter((record) => !query.action || record.queryKeys.action === query.action)
    .filter((record) => {
      const occurred = Date.parse(record.queryKeys.occurredAt);
      return occurred >= from && occurred <= to;
    });
}

export function authorizeOperatorAuditQuery(credentialPath: string, instanceId?: string, now: Date = new Date()): { operatorId: string; credentialId: string } {
  const credential = readOperatorCredential(credentialPath);
  const normalChurchRoles = new Set(["role.worship_leader", "role.catalog_editor", "role.doctrinal_reviewer", "role.musical_reviewer", "role.admin"]);
  if (normalChurchRoles.has(credential.role)) {
    throw new Error("Normal church user roles cannot authorize operator audit queries");
  }
  if (credential.role !== "cadentia-operator" && credential.role !== "cadentia-break-glass-operator") {
    throw new Error(`Unsupported operator credential role: ${credential.role}`);
  }
  const occurredAt = now.toISOString();
  const timestamp = Date.parse(occurredAt);
  if (Date.parse(credential.issuedAt) > timestamp || Date.parse(credential.expiresAt) <= timestamp) {
    throw new Error("Operator credential is not valid at the audit query timestamp");
  }
  if (!credential.scopes.includes("operator.audit.query")) {
    throw new Error("Operator credential is missing required scope operator.audit.query");
  }
  if (instanceId && !credential.allowedInstanceIds.includes(instanceId) && !credential.allowedInstanceIds.includes("*")) {
    throw new Error(`Operator credential is not scoped to audit query instance ${instanceId}`);
  }
  if (credential.role === "cadentia-break-glass-operator" && (!credential.breakGlass?.incidentId || !credential.breakGlass.approvedBy)) {
    throw new Error("Break-glass operator credentials require incident id and approver");
  }
  return { operatorId: credential.operatorId, credentialId: credential.credentialId };
}

function readOperatorCredential(path: string): OperatorCredential {
  const credential = JSON.parse(readFileSync(resolve(path), "utf8")) as OperatorCredential;
  if (credential.credentialVersion !== "cadentia.operator-credential.v1") {
    throw new Error("Operator credential must use cadentia.operator-credential.v1");
  }
  return credential;
}

function validateOperatorCredential(
  credential: OperatorCredential,
  action: OperatorAdminAction,
  targetInstanceId: string,
  occurredAt: string,
  reason: string
): void {
  const normalChurchRoles = new Set(["role.worship_leader", "role.catalog_editor", "role.doctrinal_reviewer", "role.musical_reviewer", "role.admin"]);
  if (normalChurchRoles.has(credential.role)) {
    throw new Error("Normal church user roles cannot authorize operator administration");
  }
  if (credential.role !== "cadentia-operator" && credential.role !== "cadentia-break-glass-operator") {
    throw new Error(`Unsupported operator credential role: ${credential.role}`);
  }
  if (!credential.operatorId || !credential.credentialId) {
    throw new Error("Operator credential requires operator identity and credential id");
  }
  if (!targetInstanceId || targetInstanceId.trim().length === 0) {
    throw new Error("Operator action requires explicit target instance selection");
  }
  if (!reason || reason.trim().length < 12) {
    throw new Error("Operator action requires a non-empty reason of at least 12 characters");
  }
  const now = Date.parse(occurredAt);
  if (Date.parse(credential.issuedAt) > now || Date.parse(credential.expiresAt) <= now) {
    throw new Error("Operator credential is not valid at the action timestamp");
  }
  const requiredScope = scopeForAction(action);
  if (!credential.scopes.includes(requiredScope)) {
    throw new Error(`Operator credential is missing required scope ${requiredScope}`);
  }
  if (!credential.allowedInstanceIds.includes(targetInstanceId) && !credential.allowedInstanceIds.includes("*")) {
    throw new Error(`Operator credential is not scoped to target instance ${targetInstanceId}`);
  }
  if (credential.role === "cadentia-break-glass-operator" && (!credential.breakGlass?.incidentId || !credential.breakGlass.approvedBy)) {
    throw new Error("Break-glass operator credentials require incident id and approver");
  }
}

function scopeForAction(action: OperatorAdminAction): OperatorScope {
  const scopes: Record<OperatorAdminAction, OperatorScope> = {
    list: "operator.instances.list",
    inspect: "operator.instances.inspect",
    upgrade: "operator.instances.upgrade",
    backup: "operator.instances.backup",
    restore: "operator.instances.restore",
    export: "operator.instances.export",
    clone: "operator.instances.clone"
  };
  return scopes[action];
}

function readManifestSummary(manifestPath: string, targetInstanceId: string): { digest: string; environment: string; packageVersion: string; applicationVersion: string } {
  const resolved = resolve(manifestPath);
  const rawManifest = readFileSync(resolved, "utf8");
  rejectPlaintextCredentialText(rawManifest, "operator manifest input");
  const manifest = JSON.parse(rawManifest) as ProvisioningManifest;
  if (manifest.instanceId !== targetInstanceId) {
    throw new Error(`Manifest instance ${manifest.instanceId} does not match explicit target ${targetInstanceId}`);
  }
  return {
    digest: sha256(rawManifest),
    environment: manifest.environment,
    packageVersion: manifest.packageVersion,
    applicationVersion: manifest.applicationVersion
  };
}

function readOperatorReferences(beforeRef?: string, afterRef?: string, lifecyclePlanPath?: string): OperatorAuditRecord["references"] {
  return {
    beforeRef,
    beforeHash: beforeRef && existsSync(resolve(beforeRef)) ? sha256(readFileSync(resolve(beforeRef), "utf8")) : undefined,
    afterRef,
    afterHash: afterRef && existsSync(resolve(afterRef)) ? sha256(readFileSync(resolve(afterRef), "utf8")) : undefined,
    lifecyclePlanPath: lifecyclePlanPath ? resolve(lifecyclePlanPath) : undefined,
    lifecyclePlanDigest: lifecyclePlanPath && existsSync(resolve(lifecyclePlanPath)) ? sha256(readFileSync(resolve(lifecyclePlanPath), "utf8")) : undefined
  };
}

function operatorSummary(
  action: OperatorAdminAction,
  manifestInfo: { environment: string; packageVersion: string; applicationVersion: string } | undefined,
  references: OperatorAuditRecord["references"]
): Record<string, unknown> {
  return {
    operatorToolOnly: true,
    normalChurchUserRoleAllowed: false,
    action,
    manifest: manifestInfo,
    references,
    catalogApprovalBypassAllowed: false
  };
}

function appendOperatorAuditRecord(auditLogPath: string, record: OperatorAuditRecord): void {
  mkdirSync(dirname(auditLogPath), { recursive: true });
  appendFileSync(auditLogPath, `${JSON.stringify(record)}\n`, "utf8");
}

function lastOperatorAuditHash(auditLogPath: string): string | null {
  if (!existsSync(auditLogPath)) {
    return null;
  }
  const lines = readFileSync(auditLogPath, "utf8").split("\n").filter((line) => line.trim().length > 0);
  if (lines.length === 0) {
    return null;
  }
  return (JSON.parse(lines.at(-1) ?? "{}") as Partial<OperatorAuditRecord>).recordHash ?? null;
}

function rejectSensitiveOperatorAuditRecord(record: OperatorAuditRecord): void {
  rejectPlaintextCredentialText(JSON.stringify(record), "operator audit record");
}

function rejectPlaintextCredentialText(text: string, label: string): void {
  const redFlags = [/password\s*=/i, /authorization\s*[:=]/i, /api[_-]?key\s*[:=]/i, /token\s*[:=]\s*[A-Za-z0-9_-]{16,}/i, /secret\s*[:=]\s*[A-Za-z0-9_-]{16,}/i, /jdbc:postgresql:\/\/[^\n]+:[^\n@]+@/i];
  for (const redFlag of redFlags) {
    if (redFlag.test(text)) {
      throw new Error(`${label} appears to contain sensitive credential material matching ${redFlag}`);
    }
  }
}

function stableJson(value: unknown): string {
  if (Array.isArray(value)) {
    return `[${value.map(stableJson).join(",")}]`;
  }
  if (value && typeof value === "object") {
    return `{${Object.entries(value as Record<string, unknown>)
      .filter(([, entryValue]) => entryValue !== undefined)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, entryValue]) => `${JSON.stringify(key)}:${stableJson(entryValue)}`)
      .join(",")}}`;
  }
  return JSON.stringify(value);
}

export async function createLifecycleWorkflowPlan(options: LifecycleWorkflowOptions): Promise<{ plan: LifecycleWorkflowPlan; planPath: string }> {
  const supportedWorkflows: LifecycleWorkflowType[] = ["upgrade", "backup", "restore", "export", "staging-clone"];
  if (!supportedWorkflows.includes(options.workflow)) {
    throw new Error(`Unsupported lifecycle workflow: ${options.workflow}`);
  }
  if (options.reason.trim().length === 0) {
    throw new Error("Lifecycle workflow requires an operator reason");
  }
  const applicationVersion = options.applicationVersion ?? DEFAULT_CHURCH_CONFIG_APP_VERSION;
  const packagePath = resolve(options.packagePath);
  const rawPackage = readFileSync(packagePath, "utf8");
  const validationResult = validateChurchConfigPackage(JSON.parse(rawPackage) as unknown, applicationVersion as typeof DEFAULT_CHURCH_CONFIG_APP_VERSION);
  if (!validationResult.ok) {
    throw new Error(`Church configuration package failed validation: ${validationResult.errors.map((error) => `${error.path} ${error.message}`).join("; ")}`);
  }

  const churchPackage = validationResult.package;
  const manifestPath = resolve(options.manifestPath);
  const rawManifest = readFileSync(manifestPath, "utf8");
  const manifest = JSON.parse(rawManifest) as ProvisioningManifest;
  validateManifestForPackage(manifest, churchPackage, options.workflow);

  const backup = options.backupManifestPath ? readOptionalInputDigest(options.backupManifestPath) : undefined;
  if ((options.workflow === "upgrade" || options.workflow === "restore" || options.workflow === "staging-clone") && !backup) {
    throw new Error(`${options.workflow} workflow requires --backup-manifest before changes are planned`);
  }
  if (options.workflow === "restore" && !options.restoreBackupPath) {
    throw new Error("restore workflow requires --restore-backup=<backup artifact URI or path>");
  }
  const source = options.sourceManifestPath ? readOptionalInputDigest(options.sourceManifestPath) : undefined;
  if (options.workflow === "staging-clone") {
    if (!source) {
      throw new Error("staging-clone workflow requires --source-manifest=<production manifest>");
    }
    if (churchPackage.instance.environment === "production") {
      throw new Error("staging-clone target package must be non-production");
    }
  }

  const plannedAt = (options.now ?? new Date()).toISOString();
  const operationId = `${options.workflow}-${churchPackage.instance.instanceId}-${churchPackage.instance.environment}-${sha256(`${plannedAt}:${options.operatorId}:${options.reason}`).slice(7, 19)}`;
  const evidencePath = resolve(options.outputDir, "lifecycle", `${operationId}.json`);
  const plan: LifecycleWorkflowPlan = {
    workflowVersion: "cadentia.lifecycle.v1",
    workflow: options.workflow,
    instanceId: churchPackage.instance.instanceId,
    environment: churchPackage.instance.environment,
    operator: {
      id: options.operatorId,
      reason: options.reason,
      plannedAt
    },
    inputs: {
      packagePath,
      packageDigest: sha256(rawPackage),
      provisioningManifestPath: manifestPath,
      provisioningManifestDigest: sha256(rawManifest),
      backupManifestPath: backup?.path,
      backupManifestDigest: backup?.digest,
      restoreBackupPath: options.restoreBackupPath,
      sourceManifestPath: source?.path,
      sourceManifestDigest: source?.digest
    },
    compatibility: {
      status: "validated",
      applicationVersion,
      packageSchemaVersion: churchPackage.package.schemaVersion,
      packageVersion: churchPackage.package.packageVersion,
      manifestPackageVersion: manifest.packageVersion,
      manifestApplicationVersion: manifest.applicationVersion,
      currentSchemaMigration: manifest.migrationState.latestKnownMigration,
      targetSchemaMigration: findLatestKnownMigration(),
      backupValidated: Boolean(backup)
    },
    auditEvidence: {
      operationId,
      evidencePath,
      records: auditRecordsFor(options.workflow)
    },
    resourceScope: {
      database: manifest.resources.database.identifier,
      objectStorage: manifest.resources.objectStorage.identifier,
      objectStorageNamespace: manifest.resources.objectStorage.namespacePrefix,
      cacheNamespace: manifest.resources.cache.namespace,
      eventNamespace: manifest.resources.eventStreams.namespace,
      secretReferencesOnly: true,
      crossInstanceNormalUserReadsAllowed: false,
      starterCatalogEligibility: "instance-local-approval-required"
    },
    steps: stepsFor(options.workflow),
    verificationCommands: verificationCommandsFor(options.workflow, evidencePath, manifestPath),
    rollbackSteps: rollbackStepsFor(options.workflow),
    retention: retentionFor(options.workflow),
    failureTriage: failureTriageFor(options.workflow),
    exportPolicy: options.workflow === "export" ? {
      churchOwnedDataOnly: true,
      excludesOperatorSecrets: true,
      excludesOtherInstances: true
    } : undefined,
    clonePolicy: options.workflow === "staging-clone" && source ? {
      sourceInstanceId: readProvisioningManifest(source.path).instanceId,
      sourceEnvironment: readProvisioningManifest(source.path).environment,
      productionSecretsCopied: false,
      integrations: "disabled-or-overridden",
      provenance: `cloned-from:${source.digest}`
    } : undefined
  };
  rejectPlaintextSecretMaterial(JSON.stringify(plan), "lifecycle workflow plan");
  writeJsonAtomic(evidencePath, plan);
  return { plan, planPath: evidencePath };
}

function validateManifestForPackage(manifest: ProvisioningManifest, churchPackage: ChurchConfigPackage, workflow: LifecycleWorkflowType): void {
  if (manifest.manifestVersion !== "cadentia.provisioning.v1") {
    throw new Error("Provisioning manifest must be cadentia.provisioning.v1");
  }
  if (manifest.instanceId !== churchPackage.instance.instanceId) {
    throw new Error(`Manifest instance ${manifest.instanceId} does not match package instance ${churchPackage.instance.instanceId}`);
  }
  if (manifest.environment !== churchPackage.instance.environment) {
    throw new Error(`Manifest environment ${manifest.environment} does not match package environment ${churchPackage.instance.environment}`);
  }
  if (workflow !== "upgrade" && manifest.packageVersion !== churchPackage.package.packageVersion) {
    throw new Error(`Manifest package version ${manifest.packageVersion} must match package version ${churchPackage.package.packageVersion} for ${workflow}`);
  }
}

function readOptionalInputDigest(path: string): { path: string; digest: string } {
  const resolved = resolve(path);
  return { path: resolved, digest: sha256(readFileSync(resolved, "utf8")) };
}

function auditRecordsFor(workflow: LifecycleWorkflowType): string[] {
  return [
    "validated church package digest and provisioning manifest digest",
    "operator identity, reason, and timestamp",
    `${workflow} step transcript and verification output`,
    "pre/post package, application, schema, and backup artifact references"
  ];
}

function stepsFor(workflow: LifecycleWorkflowType): string[] {
  const common = [
    "Validate church package against the target application version.",
    "Validate provisioning manifest identity, isolated resources, and secret references.",
    "Write this lifecycle plan to immutable operator audit evidence."
  ];
  const byWorkflow: Record<LifecycleWorkflowType, string[]> = {
    upgrade: [
      "Verify the referenced backup manifest was completed and restorable for this instance.",
      "Stop background workers, place the API in maintenance mode, and record pre-migration Flyway state.",
      "Deploy the target application artifact and package, then allow Spring Flyway to migrate the isolated database only.",
      "Record post-migration Flyway state, package version, application version, and smoke-check output."
    ],
    backup: [
      "Snapshot the isolated database using the database resource identifier from the manifest.",
      "Copy object storage assets under the manifest namespace prefix only.",
      "Archive the church package, provisioning manifest, API env template, and non-secret secret reference inventory.",
      "Write backup checksums and retention metadata to audit evidence."
    ],
    restore: [
      "Verify the backup artifact checksum and manifest identity before restore.",
      "Restore database and asset namespace into resources owned by this instance only.",
      "Restore package, provisioning manifest, API env template, and non-secret reference inventory.",
      "Start API with resolved environment-specific secrets and verify local approval-gated catalog eligibility."
    ],
    export: [
      "Use operator-scoped export credentials for this instance only, never normal user credentials.",
      "Export church-owned catalog metadata, arrangements, service plans, approval history, and assets as allowed by policy.",
      "Exclude operator secrets, secret values, other instances, cache data, and event-stream operational internals.",
      "Write export checksums and a redaction report."
    ],
    "staging-clone": [
      "Restore production backup into isolated non-production resources for the target manifest only.",
      "Replace every secret binding with staging-safe references; do not copy production secret values.",
      "Disable or override outbound integrations, telemetry exports, webhooks, and scheduled jobs for staging.",
      "Record source manifest digest, backup digest, target package digest, and clone provenance."
    ]
  };
  return [...common, ...byWorkflow[workflow]];
}

function verificationCommandsFor(workflow: LifecycleWorkflowType, evidencePath: string, manifestPath: string): string[] {
  return [
    `node packages/provisioning/bin/verify-lifecycle-workflow.mjs --plan=${evidencePath}`,
    `node packages/provisioning/bin/smoke-check-instance.mjs --manifest=${manifestPath}`,
    workflow === "upgrade" ? "flyway info -url=$CADENTIA_DB_URL -table=flyway_schema_history" : "jq '.compatibility.status,.resourceScope.starterCatalogEligibility' < " + evidencePath
  ];
}

function rollbackStepsFor(workflow: LifecycleWorkflowType): string[] {
  const common = ["Keep rollback within the same instance resources; never point at another church instance."];
  const byWorkflow: Record<LifecycleWorkflowType, string[]> = {
    upgrade: ["Stop the upgraded API, restore the validated pre-upgrade backup, redeploy the prior package/application artifact, and rerun smoke checks."],
    backup: ["Delete incomplete backup artifacts, keep prior successful backups, and rerun backup from the unchanged source instance."],
    restore: ["Stop the restored API, preserve failed restore evidence, and restore from the previous known-good backup or rebuild from provisioning manifest."],
    export: ["Revoke the export artifact, delete partial files, preserve redaction logs, and rerun export after fixing scope/redaction failures."],
    "staging-clone": ["Destroy the staging clone resources or restore the prior staging backup; never reconnect staging to production secrets."]
  };
  return [...common, ...byWorkflow[workflow]];
}

function retentionFor(workflow: LifecycleWorkflowType): string {
  const byWorkflow: Record<LifecycleWorkflowType, string> = {
    upgrade: "Retain upgrade evidence, pre/post version records, and the pre-upgrade backup per the production backup retention schedule, at least 35 days.",
    backup: "Retain daily backups for 35 days, monthly backups for 13 months, and annual backups for 7 years unless church policy requires longer.",
    restore: "Retain restore evidence and source backup checksums for the same period as the restored backup artifact.",
    export: "Retain export artifacts only for the church-approved delivery window; delete operator working copies within 7 days after acceptance.",
    "staging-clone": "Retain staging clones for the approved test window, normally no more than 30 days, then destroy or refresh from a new backup."
  };
  return byWorkflow[workflow];
}

function failureTriageFor(workflow: LifecycleWorkflowType): string[] {
  return [
    "Stop before making further changes if package validation, manifest identity, backup validation, or secret-redaction checks fail.",
    "Compare package, manifest, backup, and schema digests recorded in the lifecycle plan.",
    "Confirm tooling is scoped to one instance resource set and is using operator credentials rather than normal user credentials.",
    `Follow the ${workflow} rollback steps and attach command output to the operator audit record.`
  ];
}

function rejectPlaintextSecretMaterial(text: string, label: string): void {
  const redFlags = [/password\s*=/i, /token\s*[:=]\s*[A-Za-z0-9_-]{16,}/i, /secret\s*[:=]\s*[A-Za-z0-9_-]{16,}/i, /jdbc:postgresql:\/\/[^\n]+:[^\n@]+@/i];
  for (const redFlag of redFlags) {
    if (redFlag.test(text)) {
      throw new Error(`${label} appears to contain plaintext credential material matching ${redFlag}`);
    }
  }
}

function readProvisioningManifest(path: string): ProvisioningManifest {
  return JSON.parse(readFileSync(path, "utf8")) as ProvisioningManifest;
}
