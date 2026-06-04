import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, readdirSync, renameSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";

import {
  DEFAULT_CHURCH_CONFIG_APP_VERSION,
  type ChurchConfigPackage,
  validateChurchConfigPackage
} from "@cadentia/intent-contracts";

export type DeploymentMode = "self-hosted" | "private-cloud" | "managed-single-tenant" | "church-managed";
export type ProvisioningAction = "provision" | "upgrade" | "reconcile" | "rollback";

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
  const migrationDir = resolve("apps/api/src/main/resources/db/migration");
  if (!existsSync(migrationDir)) {
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
