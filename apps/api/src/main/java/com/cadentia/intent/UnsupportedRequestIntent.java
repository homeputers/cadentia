package com.cadentia.intent;

public record UnsupportedRequestIntent(
        String contractVersion,
        String reasonCode,
        String safeMessage) implements ValidatedIntent {

    @Override
    public IntentType intentType() {
        return IntentType.UNSUPPORTED_REQUEST;
    }
}
