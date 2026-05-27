package com.cadentia.intent;

public record SessionSlotUpdate(GenerateSetlistSlots slots, SlotValueSource source, boolean preferInferredValueOnConflict) {}
