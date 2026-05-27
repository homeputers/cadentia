package com.cadentia.intent;

public record SessionMergeConflict(String slotPath, Object existingValue, SlotValueSource existingSource, Object attemptedValue) {}
