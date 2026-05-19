package com.cadentia.catalog.service;

import com.cadentia.catalog.entity.Arrangement;
import com.cadentia.catalog.entity.LyricsDocument;
import com.cadentia.catalog.entity.Song;
import com.cadentia.catalog.entity.Tag;
import com.cadentia.catalog.model.LyricsParseStatus;
import com.cadentia.catalog.model.TagAssignmentTarget;
import com.cadentia.catalog.repository.SongRepository;
import com.cadentia.catalog.transposition.DeterministicTransposer;
import com.cadentia.catalog.transposition.MusicalKey;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {

    private final SongRepository songRepository;
    private final DeterministicTransposer transposer;

    @Autowired
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

    public Tag assignTag(TagAssignmentTarget target, UUID targetId, UUID tagId) {
        if (target == null) {
            throw new IllegalArgumentException("target is required");
        }
        if (targetId == null) {
            throw new IllegalArgumentException("targetId is required");
        }
        if (tagId == null) {
            throw new IllegalArgumentException("tagId is required");
        }
        Tag tag = songRepository.findTagById(tagId)
                .orElseThrow(() -> new IllegalArgumentException("tag does not exist: " + tagId));
        if (!tag.active()) {
            throw new IllegalArgumentException("tag is inactive: " + tagId);
        }
        boolean assigned = switch (target) {
            case SONG -> assignTagToSong(targetId, tagId);
            case ARRANGEMENT -> assignTagToArrangement(targetId, tagId);
            case LYRICS_DOCUMENT -> assignTagToLyricsDocument(targetId, tagId);
        };
        if (!assigned) {
            throw new IllegalArgumentException("tag is already assigned to target");
        }
        return tag;
    }

    private boolean assignTagToSong(UUID songId, UUID tagId) {
        if (songRepository.findById(songId).isEmpty()) {
            throw new IllegalArgumentException("song does not exist: " + songId);
        }
        return songRepository.addTagToSong(songId, tagId);
    }

    private boolean assignTagToArrangement(UUID arrangementId, UUID tagId) {
        if (songRepository.findArrangementById(arrangementId).isEmpty()) {
            throw new IllegalArgumentException("arrangement does not exist: " + arrangementId);
        }
        return songRepository.addTagToArrangement(arrangementId, tagId);
    }

    private boolean assignTagToLyricsDocument(UUID lyricsDocumentId, UUID tagId) {
        if (songRepository.findLyricsDocumentById(lyricsDocumentId).isEmpty()) {
            throw new IllegalArgumentException("lyrics document does not exist: " + lyricsDocumentId);
        }
        return songRepository.addTagToLyricsDocument(lyricsDocumentId, tagId);
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
