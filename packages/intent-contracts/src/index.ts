import { z } from "zod";

export const intentOutputSchema = z.object({
  intent: z.literal("GENERATE_SETLIST"),
  slots: z.object({
    verseText: z.string().min(1),
    themeHints: z.array(z.string()).default([]),
    counts: z.object({
      praise: z.number().int().min(0).max(25).default(10),
      worship: z.number().int().min(0).max(25).default(5)
    }),
    keyPolicy: z.object({
      preferSameKey: z.boolean().default(true),
      allowRelativeMajorMinor: z.boolean().default(true),
      maxKeyCenters: z.number().int().min(1).max(12).default(2)
    }),
    tempoPolicy: z.object({
      maxJumpBpm: z.number().int().min(1).max(60).default(12)
    })
  })
});

export type IntentOutput = z.infer<typeof intentOutputSchema>;

export function parseIntentOutput(payload: unknown): IntentOutput {
  return intentOutputSchema.parse(payload);
}
