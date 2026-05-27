package com.cadentia.intent;

public record SessionMergeEvent(String slotPath, Object priorValue, Object mergedValue, SlotValueSource source) {}
