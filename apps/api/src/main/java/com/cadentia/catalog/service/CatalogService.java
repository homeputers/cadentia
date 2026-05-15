package com.cadentia.catalog.service;

import com.cadentia.catalog.entity.Arrangement;
import com.cadentia.catalog.entity.LyricsDocument;
import com.cadentia.catalog.entity.Song;
import com.cadentia.catalog.model.LyricsParseStatus;
import com.cadentia.catalog.repository.SongRepository;
import com.cadentia.catalog.transposition.DeterministicTransposer;
import com.cadentia.catalog.transposition.MusicalKey;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {

    private final SongRepository songRepository;
    private final DeterministicTransposer transposer;

    public CatalogService(SongRepository songRepository) {
        this(songRepository, new DeterministicTransposer());
    }

    CatalogService(SongRepository songRepository, DeterministicTransposer transposer) {
        this.songRepository = songRepository;
        this.transposer = transposer;
    }

    public Optional<Song> findSong(UUID id) {
        return songRepository.findById(id);
    }

    public Optional<ArrangementRetrievalResult> retrieveArrangement(
            UUID arrangementId, Optional<MusicalKey> requestedTargetKey) {
        if (arrangementId == null) {
            throw new IllegalArgumentException("arrangementId is required");
        }
        if (requestedTargetKey == null) {
            throw new IllegalArgumentException("requestedTargetKey is required");
        }
        return songRepository.findArrangementById(arrangementId)
                .map(arrangement -> buildArrangementResult(arrangement, requestedTargetKey));
    }

    private ArrangementRetrievalResult buildArrangementResult(
            Arrangement arrangement, Optional<MusicalKey> requestedTargetKey) {
        MusicalKey baseKey = new MusicalKey(arrangement.musicalKey(), arrangement.keyMode());
        MusicalKey targetKey = requestedTargetKey.orElse(baseKey);
        int interval = requestedTargetKey.isPresent() ? transposer.semitoneInterval(baseKey, targetKey) : 0;
        boolean dynamicallyTransposed = requestedTargetKey.isPresent() && interval != 0;
        LyricsDocument currentLyricsDocument = findCurrentLyricsDocument(arrangement.id()).orElse(null);
        if (currentLyricsDocument == null || !dynamicallyTransposed) {
            return new ArrangementRetrievalResult(
                    arrangement,
                    currentLyricsDocument,
                    baseKey,
                    targetKey,
                    interval,
                    dynamicallyTransposed,
                    ArrangementTranspositionSource.NONE,
                    currentLyricsDocument == null ? null : currentLyricsDocument.content(),
                    currentLyricsDocument == null ? null : currentLyricsDocument.chordMapJson());
        }
        if (hasParsedChordMap(currentLyricsDocument)) {
            return new ArrangementRetrievalResult(
                    arrangement,
                    currentLyricsDocument,
                    baseKey,
                    targetKey,
                    interval,
                    true,
                    ArrangementTranspositionSource.PARSED_CHORD_MAP,
                    currentLyricsDocument.content(),
                    transposer.transposeChordMapJson(baseKey, targetKey, currentLyricsDocument.chordMapJson()));
        }
        if (currentLyricsDocument.containsChords()) {
            return new ArrangementRetrievalResult(
                    arrangement,
                    currentLyricsDocument,
                    baseKey,
                    targetKey,
                    interval,
                    true,
                    ArrangementTranspositionSource.CHORD_SHEET_CONTENT,
                    transposer.transposeChordSheet(baseKey, targetKey, currentLyricsDocument.content()),
                    currentLyricsDocument.chordMapJson());
        }
        return new ArrangementRetrievalResult(
                arrangement,
                currentLyricsDocument,
                baseKey,
                targetKey,
                interval,
                true,
                ArrangementTranspositionSource.NONE,
                currentLyricsDocument.content(),
                currentLyricsDocument.chordMapJson());
    }

    private Optional<LyricsDocument> findCurrentLyricsDocument(UUID arrangementId) {
        return songRepository.findLyricsDocumentsByArrangementId(arrangementId).stream()
                .filter(LyricsDocument::current)
                .max(Comparator.comparingInt(LyricsDocument::versionNumber));
    }

    private static boolean hasParsedChordMap(LyricsDocument lyricsDocument) {
        return lyricsDocument.parseStatus() == LyricsParseStatus.PARSED
                && lyricsDocument.chordMapJson() != null
                && !lyricsDocument.chordMapJson().isBlank();
    }
}
