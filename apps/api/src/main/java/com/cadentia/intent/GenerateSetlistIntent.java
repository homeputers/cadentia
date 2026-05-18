package com.cadentia.intent;

public record GenerateSetlistIntent(String contractVersion, GenerateSetlistSlots slots)
        implements ValidatedIntent {

    @Override
    public IntentType intentType() {
        return IntentType.GENERATE_SETLIST;
    }
}
