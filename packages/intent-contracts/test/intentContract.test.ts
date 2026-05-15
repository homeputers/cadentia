import { describe, expect, it } from "vitest";

import { parseIntentOutput } from "../src/index.js";

describe("intentOutputSchema", () => {
  it("accepts the required GENERATE_SETLIST contract", () => {
    const result = parseIntentOutput({
      intent: "GENERATE_SETLIST",
      slots: {
        verseText: "Psalm 100",
        themeHints: ["thanksgiving"],
        counts: { praise: 10, worship: 5 },
        keyPolicy: {
          preferSameKey: true,
          allowRelativeMajorMinor: true,
          maxKeyCenters: 2
        },
        tempoPolicy: { maxJumpBpm: 12 }
      }
    });

    expect(result.slots.counts).toEqual({ praise: 10, worship: 5 });
  });
});
