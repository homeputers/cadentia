package com.cadentia.catalog.transposition;

import com.cadentia.catalog.model.KeyMode;

public record MusicalKey(String tonic, KeyMode mode) {

    public MusicalKey {
        if (tonic == null || tonic.isBlank()) {
            throw new IllegalArgumentException("tonic is required");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }
        tonic = tonic.trim();
    }
}
