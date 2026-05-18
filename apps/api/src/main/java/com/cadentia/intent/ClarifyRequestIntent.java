package com.cadentia.intent;

import java.util.List;

public record ClarifyRequestIntent(
        String contractVersion,
        String reasonCode,
        String clarificationQuestion,
        List<String> missingSlots) implements ValidatedIntent {

    public ClarifyRequestIntent {
        missingSlots = List.copyOf(missingSlots);
    }

    @Override
    public IntentType intentType() {
        return IntentType.CLARIFY_REQUEST;
    }
}
