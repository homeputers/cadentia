import { z } from "zod";

export const INTENT_CONTRACT_VERSION = "v1" as const;

const slotStringSchema = z.string().min(1).max(120);

const countsSchema = z
  .object({
    praise: z.number().int().min(0).max(25).default(10),
    worship: z.number().int().min(0).max(25).default(5)
  })
  .strict()
  .default({ praise: 10, worship: 5 });

const keyPolicySchema = z
  .object({
    preferSameKey: z.boolean().default(true),
    allowRelativeMajorMinor: z.boolean().default(true),
    maxKeyCenters: z.number().int().min(1).max(12).default(2)
  })
  .strict()
  .default({
    preferSameKey: true,
    allowRelativeMajorMinor: true,
    maxKeyCenters: 2
  });

const tempoPolicySchema = z
  .object({
    maxJumpBpm: z.number().int().min(1).max(60).default(12)
  })
  .strict()
  .default({ maxJumpBpm: 12 });

export const generateSetlistSlotsSchema = z
  .object({
    verseText: z.string().max(5000).default(""),
    scriptureReferences: z.array(z.string().min(1).max(100)).max(20).default([]),
    themeHints: z.array(slotStringSchema).max(20).default([]),
    counts: countsSchema,
    keyPolicy: keyPolicySchema,
    tempoPolicy: tempoPolicySchema,
    language: z
      .string()
      .min(2)
      .max(35)
      .regex(/^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$/)
      .nullable()
      .default(null),
    energyArc: z
      .enum(["steady", "rising", "falling", "low_to_high", "high_to_low"])
      .nullable()
      .default(null),
    excludedSongs: z.array(slotStringSchema).max(25).default([]),
    serviceMoment: z
      .enum(["opening", "communion", "response", "altar_call", "sending", "other"])
      .nullable()
      .default(null)
  })
  .strict()
  .default({});

export const generateSetlistIntentSchema = z
  .object({
    intent: z.literal("GENERATE_SETLIST"),
    slots: generateSetlistSlotsSchema
  })
  .strict();

export const clarifyRequestIntentSchema = z
  .object({
    intent: z.literal("CLARIFY_REQUEST"),
    reasonCode: z.enum([
      "MISSING_REQUIRED_INFORMATION",
      "AMBIGUOUS_REQUEST",
      "INSUFFICIENT_CONTEXT"
    ]),
    clarificationQuestion: z.string().min(1).max(500),
    missingSlots: z
      .array(
        z.enum([
          "verseText",
          "scriptureReferences",
          "themeHints",
          "counts",
          "keyPolicy",
          "tempoPolicy",
          "language",
          "energyArc",
          "excludedSongs",
          "serviceMoment"
        ])
      )
      .max(20)
      .default([])
  })
  .strict();

export const unsupportedRequestIntentSchema = z
  .object({
    intent: z.literal("UNSUPPORTED_REQUEST"),
    reasonCode: z.enum(["OUT_OF_SCOPE", "UNSUPPORTED_ACTION", "UNSUPPORTED_INTENT"]),
    safeMessage: z.string().min(1).max(500)
  })
  .strict();

export const intentOutputSchema = z.discriminatedUnion("intent", [
  generateSetlistIntentSchema,
  clarifyRequestIntentSchema,
  unsupportedRequestIntentSchema
]);

export type IntentOutput = z.infer<typeof intentOutputSchema>;
export type GenerateSetlistIntent = z.infer<typeof generateSetlistIntentSchema>;
export type ClarifyRequestIntent = z.infer<typeof clarifyRequestIntentSchema>;
export type UnsupportedRequestIntent = z.infer<typeof unsupportedRequestIntentSchema>;

export const intentContractV1SchemaPath = new URL(
  "../schemas/v1/intent.schema.json",
  import.meta.url
).pathname;

export function parseIntentOutput(payload: unknown): IntentOutput {
  return intentOutputSchema.parse(payload);
}

export * from "./explanations.js";
