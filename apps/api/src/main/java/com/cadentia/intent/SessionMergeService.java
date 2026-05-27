package com.cadentia.intent;

public interface SessionMergeService {
    SessionMergeResult merge(GenerateSetlistSlots baseline, SessionSlotUpdate update);
}
