package com.cadentia.intent;

public record IntentKeyPolicy(
        boolean preferSameKey,
        boolean allowRelativeMajorMinor,
        int maxKeyCenters) {
}
