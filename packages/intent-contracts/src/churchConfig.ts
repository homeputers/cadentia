import { z } from "zod";

export const CHURCH_CONFIG_SCHEMA_VERSION = "church-config.v1" as const;
export const CHURCH_CONFIG_SUPPORTED_SCHEMA_VERSIONS = [CHURCH_CONFIG_SCHEMA_VERSION] as const;
export const CHURCH_CONFIG_DEPRECATED_SCHEMA_VERSIONS = [] as const;
export const CHURCH_CONFIG_REJECTED_SCHEMA_VERSIONS = ["church-config.v0"] as const;
export const DEFAULT_CHURCH_CONFIG_APP_VERSION = "0.1.0" as const;

const idSchema = z.string().min(3).max(80).regex(/^[a-z][a-z0-9-]*[a-z0-9]$/);
const semverSchema = z.string().regex(/^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$/);
const secretRefSchema = z.string().min(8).max(250).regex(/^(secret-manager|env|vault|aws-sm|gcp-sm|azure-kv):[A-Za-z0-9_./:@-]+$/);
const resourceRefSchema = z.string().min(3).max(200).regex(/^[A-Za-z0-9][A-Za-z0-9_./:@-]*$/);
const integrationRefSchema = z.string().min(5).max(120).regex(/^[a-z][a-z0-9-]*\.[a-z][a-z0-9-]*(?:\.v\d+)?$/);
const pluginRefSchema = z.string().min(5).max(120).regex(/^@[a-z0-9-]+\/[a-z][a-z0-9-]*(?:@\d+\.\d+\.\d+)?$/);
const hexColorSchema = z.string().regex(/^#[0-9A-Fa-f]{6}$/);
const boundedNameSchema = z.string().min(1).max(120);

const semverFieldsSchema = z
  .object({
    schemaVersion: z.literal(CHURCH_CONFIG_SCHEMA_VERSION),
    packageVersion: semverSchema,
    generatedAt: z.string().datetime(),
    applicationCompatibility: z
      .object({
        minVersion: semverSchema,
        maxExclusiveVersion: semverSchema,
        deprecatedAfterVersion: semverSchema.optional()
      })
      .strict()
  })
  .strict();

const instanceIdentitySchema = z
  .object({
    instanceId: idSchema,
    displayName: boundedNameSchema,
    environment: z.enum(["development", "staging", "production"]),
    region: z.string().min(2).max(60),
    timezone: z.string().min(3).max(80),
    locale: z.string().min(2).max(20).regex(/^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$/),
    supportContact: z.string().email()
  })
  .strict();

const modulesSchema = z
  .object({
    recommendation: z.object({ enabled: z.literal(true) }).strict(),
    catalogImport: z.object({ enabled: z.boolean(), allowedSources: z.array(z.enum(["local_fixture", "ccli", "songselect", "denominational_seed"])).min(1) }).strict(),
    servicePlanning: z.object({ enabled: z.boolean() }).strict(),
    teamScheduling: z.object({ enabled: z.boolean() }).strict(),
    feedbackTuning: z.object({ enabled: z.boolean() }).strict(),
    externalMessaging: z.object({ enabled: z.boolean(), channels: z.array(z.enum(["telegram", "whatsapp", "email"])).default([]) }).strict()
  })
  .strict();

const policiesSchema = z
  .object({
    recommendationPolicy: z
      .object({
        counts: z.object({ praise: z.number().int().min(0).max(25), worship: z.number().int().min(0).max(25) }).strict(),
        keyPolicy: z.object({ preferSameKey: z.boolean(), allowRelativeMajorMinor: z.boolean(), maxKeyCenters: z.number().int().min(1).max(12) }).strict(),
        tempoPolicy: z.object({ maxJumpBpm: z.number().int().min(1).max(60) }).strict(),
        requireApprovedOnly: z.literal(true),
        requireDatasetReferences: z.literal(true)
      })
      .strict(),
    dataRetentionPolicy: z.object({ auditLogDays: z.number().int().min(30), exportRetentionDays: z.number().int().min(1), backupRetentionDays: z.number().int().min(1) }).strict(),
    securityPolicy: z.object({ requireMfaForAdmins: z.boolean(), sessionTimeoutMinutes: z.number().int().min(5).max(1440) }).strict()
  })
  .strict();

const scoringProfilesSchema = z
  .object({
    activeProfile: idSchema,
    profiles: z
      .array(
        z
          .object({
            id: idSchema,
            label: boundedNameSchema,
            weights: z
              .object({
                themeFit: z.number().min(0),
                scriptureFit: z.number().min(0),
                musicalFit: z.number().min(0),
                energyFit: z.number().min(0),
                familiarity: z.number().min(0)
              })
              .strict(),
            deterministicTieBreakers: z.array(z.enum(["approval_date", "title", "arrangement_id"])).min(1)
          })
          .strict()
      )
      .min(1)
  })
  .strict()
  .superRefine((value, ctx) => {
    if (!value.profiles.some((profile) => profile.id === value.activeProfile)) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["activeProfile"], message: "activeProfile must reference a declared scoring profile" });
    }
  });

const vocabulariesSchema = z
  .object({
    themeTags: z.array(z.object({ id: idSchema, label: boundedNameSchema }).strict()).min(1),
    serviceMoments: z.array(z.enum(["opening", "communion", "response", "altar_call", "sending", "other"])).min(1),
    languages: z.array(z.string().regex(/^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$/)).min(1),
    energyArcs: z.array(z.enum(["steady", "rising", "falling", "low_to_high", "high_to_low"])).min(1)
  })
  .strict();

const approvalGatesSchema = z
  .object({
    requireLyricsProvenance: z.literal(true),
    requireDoctrinalReview: z.literal(true),
    requireLicensingReview: z.boolean(),
    minimumReviewers: z.number().int().min(1).max(10),
    allowedReviewerRoles: z.array(z.enum(["worship_admin", "pastor", "music_director", "copyright_admin"])).min(1),
    blockRecommendationStatuses: z.array(z.enum(["pending", "rejected", "expired"])).min(1)
  })
  .strict();

const workflowDefaultsSchema = z
  .object({
    servicePlanLeadDays: z.number().int().min(0).max(365),
    rehearsalReminderHours: z.number().int().min(1).max(720),
    defaultSetLengthMinutes: z.number().int().min(5).max(180),
    defaultServiceMoments: z.array(z.enum(["opening", "communion", "response", "altar_call", "sending", "other"])).min(1)
  })
  .strict();

const brandingSchema = z
  .object({
    displayName: boundedNameSchema,
    primaryColor: hexColorSchema,
    secondaryColor: hexColorSchema,
    logoAssetRef: resourceRefSchema.optional(),
    supportUrl: z.string().url().optional()
  })
  .strict();

const integrationsSchema = z
  .object({
    providers: z
      .array(
        z
          .object({
            ref: integrationRefSchema,
            type: z.enum(["messaging", "lyrics", "calendar", "identity", "export"]),
            enabled: z.boolean(),
            secretRef: secretRefSchema.optional(),
            endpoint: z.string().url().optional()
          })
          .strict()
      )
      .default([])
  })
  .strict();

const pluginAllowListSchema = z
  .object({
    plugins: z
      .array(
        z
          .object({
            ref: pluginRefSchema,
            enabled: z.boolean(),
            permissions: z.array(z.enum(["read_catalog", "write_catalog_staging", "read_service_plans", "write_exports", "send_messages"])).default([])
          })
          .strict()
      )
      .default([])
  })
  .strict();

const assetStorageSchema = z
  .object({
    provider: z.enum(["s3", "gcs", "azure_blob", "local"]),
    bucketOrContainer: resourceRefSchema,
    namespacePrefix: idSchema,
    encryptionKeyRef: secretRefSchema,
    publicAssetsBaseUrl: z.string().url().optional()
  })
  .strict();

const featureFlagsSchema = z.record(z.string().regex(/^[a-z][a-z0-9-]*$/), z.boolean());

const observabilitySchema = z
  .object({
    metrics: z.object({ enabled: z.boolean(), namespace: idSchema }).strict(),
    logs: z.object({ level: z.enum(["DEBUG", "INFO", "WARN", "ERROR"]), redactSecrets: z.literal(true) }).strict(),
    traces: z.object({ enabled: z.boolean(), exporter: z.enum(["otlp", "none"]) }).strict(),
    exports: z.object({ enabled: z.boolean(), destinationRef: resourceRefSchema, secretRef: secretRefSchema.optional() }).strict()
  })
  .strict();

const moduleSpecificSchema = z
  .object({
    catalogImport: z.object({ seedPackageRefs: z.array(resourceRefSchema).default([]) }).strict().optional(),
    servicePlanning: z.object({ calendarIntegrationRef: integrationRefSchema.optional() }).strict().optional(),
    teamScheduling: z.object({ messagingIntegrationRef: integrationRefSchema.optional() }).strict().optional()
  })
  .strict()
  .optional();

const packageSchema = z
  .object({
    package: semverFieldsSchema,
    instance: instanceIdentitySchema,
    modules: modulesSchema,
    policies: policiesSchema,
    scoringProfiles: scoringProfilesSchema,
    vocabularies: vocabulariesSchema,
    approvalGates: approvalGatesSchema,
    workflowDefaults: workflowDefaultsSchema,
    branding: brandingSchema,
    integrations: integrationsSchema,
    pluginAllowList: pluginAllowListSchema,
    assetStorage: assetStorageSchema,
    featureFlags: featureFlagsSchema,
    observability: observabilitySchema,
    moduleSpecific: moduleSpecificSchema,
    extensions: z.record(z.unknown()).optional()
  })
  .strict()
  .superRefine((value, ctx) => {
    if (compareSemver(value.package.applicationCompatibility.minVersion, value.package.applicationCompatibility.maxExclusiveVersion) >= 0) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["package", "applicationCompatibility"], message: "minVersion must be lower than maxExclusiveVersion" });
    }

    const integrationRefs = new Set(value.integrations.providers.map((provider) => provider.ref));
    const moduleSpecificValue = value.moduleSpecific;
    if (moduleSpecificValue?.servicePlanning?.calendarIntegrationRef && !integrationRefs.has(moduleSpecificValue.servicePlanning.calendarIntegrationRef)) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["moduleSpecific", "servicePlanning", "calendarIntegrationRef"], message: "calendarIntegrationRef must reference integrations.providers[].ref" });
    }
    if (moduleSpecificValue?.teamScheduling?.messagingIntegrationRef && !integrationRefs.has(moduleSpecificValue.teamScheduling.messagingIntegrationRef)) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["moduleSpecific", "teamScheduling", "messagingIntegrationRef"], message: "messagingIntegrationRef must reference integrations.providers[].ref" });
    }
  });

export const churchConfigPackageSchema = packageSchema;
export type ChurchConfigPackage = z.infer<typeof churchConfigPackageSchema>;

export const churchConfigPackageV1SchemaPath = new URL(
  "../schemas/church-config/v1/church-config-package.schema.json",
  import.meta.url
).pathname;

export type ChurchConfigValidationError = {
  code: string;
  path: string;
  message: string;
};

export type ChurchConfigValidationResult =
  | { ok: true; package: ChurchConfigPackage; warnings: string[] }
  | { ok: false; errors: ChurchConfigValidationError[]; warnings: string[] };

export function validateChurchConfigPackage(payload: unknown, applicationVersion = DEFAULT_CHURCH_CONFIG_APP_VERSION): ChurchConfigValidationResult {
  const parsed = churchConfigPackageSchema.safeParse(payload);
  const warnings: string[] = [];
  if (!parsed.success) {
    return { ok: false, errors: parsed.error.issues.map(formatZodIssue), warnings };
  }

  const packageVersion = parsed.data.package.schemaVersion;
  if ((CHURCH_CONFIG_REJECTED_SCHEMA_VERSIONS as readonly string[]).includes(packageVersion)) {
    return { ok: false, errors: [{ code: "incompatible_schema_version", path: "/package/schemaVersion", message: `${packageVersion} is rejected by this application release` }], warnings };
  }
  if (!(CHURCH_CONFIG_SUPPORTED_SCHEMA_VERSIONS as readonly string[]).includes(packageVersion)) {
    return { ok: false, errors: [{ code: "unsupported_schema_version", path: "/package/schemaVersion", message: `${packageVersion} is not supported by this application release` }], warnings };
  }
  if ((CHURCH_CONFIG_DEPRECATED_SCHEMA_VERSIONS as readonly string[]).includes(packageVersion)) {
    warnings.push(`${packageVersion} is deprecated and should be upgraded before the next application release`);
  }

  const compatibility = parsed.data.package.applicationCompatibility;
  if (compareSemver(applicationVersion, compatibility.minVersion) < 0 || compareSemver(applicationVersion, compatibility.maxExclusiveVersion) >= 0) {
    return {
      ok: false,
      errors: [
        {
          code: "incompatible_application_version",
          path: "/package/applicationCompatibility",
          message: `application ${applicationVersion} is outside supported range [${compatibility.minVersion}, ${compatibility.maxExclusiveVersion})`
        }
      ],
      warnings
    };
  }

  return { ok: true, package: parsed.data, warnings };
}

export function parseChurchConfigPackage(payload: unknown, applicationVersion = DEFAULT_CHURCH_CONFIG_APP_VERSION): ChurchConfigPackage {
  const result = validateChurchConfigPackage(payload, applicationVersion);
  if (!result.ok) {
    throw new ChurchConfigValidationException(result.errors);
  }
  return result.package;
}

export class ChurchConfigValidationException extends Error {
  constructor(public readonly errors: ChurchConfigValidationError[]) {
    super(errors.map((error) => `${error.path}: ${error.message}`).join("; "));
    this.name = "ChurchConfigValidationException";
  }
}

function formatZodIssue(issue: z.ZodIssue): ChurchConfigValidationError {
  return {
    code: issue.code,
    path: `/${issue.path.join("/")}`,
    message: issue.message
  };
}

function compareSemver(left: string, right: string): number {
  const leftParts = semverCore(left);
  const rightParts = semverCore(right);
  for (let index = 0; index < 3; index += 1) {
    const difference = leftParts[index] - rightParts[index];
    if (difference !== 0) {
      return difference > 0 ? 1 : -1;
    }
  }
  return 0;
}

function semverCore(value: string): [number, number, number] {
  const [major = "0", minor = "0", patch = "0"] = value.split(/[+-]/)[0].split(".");
  return [Number(major), Number(minor), Number(patch)];
}
