package com.cadentia.intent;

public sealed interface ValidatedIntent permits GenerateSetlistIntent, ClarifyRequestIntent, UnsupportedRequestIntent {

    String contractVersion();

    IntentType intentType();
}
