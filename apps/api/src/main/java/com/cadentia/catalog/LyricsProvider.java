package com.cadentia.catalog;

import java.util.Optional;
import java.util.UUID;

public interface LyricsProvider {

    Optional<String> findApprovedLyrics(UUID songId);
}
