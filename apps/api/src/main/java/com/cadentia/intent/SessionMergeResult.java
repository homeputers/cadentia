package com.cadentia.intent;

import java.util.List;
import java.util.Map;

public record SessionMergeResult(
        GenerateSetlistSlots mergedSlots,
        Map<String, SlotValueSource> slotSources,
        List<SessionMergeEvent> events,
        List<SessionMergeConflict> conflicts) {}
