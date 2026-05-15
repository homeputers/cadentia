package com.cadentia.scraperadmin;

import java.util.Collection;
import java.util.List;

public interface SongDeduper {

    List<DuplicateSuggestion> suggestDuplicates(
            ImportSongCandidate candidate,
            Collection<CatalogSongCandidate> catalogSongs);
}
