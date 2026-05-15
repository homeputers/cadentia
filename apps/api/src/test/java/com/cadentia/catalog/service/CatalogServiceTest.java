package com.cadentia.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cadentia.catalog.entity.Arrangement;
import com.cadentia.catalog.entity.LyricsDocument;
import com.cadentia.catalog.model.ArrangementSourceType;
import com.cadentia.catalog.model.CreateArrangementCommand;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.model.LyricsFormat;
import com.cadentia.catalog.model.LyricsParseStatus;
import com.cadentia.catalog.repository.SongRepository;
import com.cadentia.catalog.transposition.MusicalKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private SongRepository songRepository;

    @Test
    void retrieveArrangementReturnsParsedChordMapTranspositionWithoutMutatingStoredDocument() {
        // Arrange
        Arrangement arrangement = arrangement("C", KeyMode.MAJOR);
        LyricsDocument lyricsDocument = lyricsDocument(
                arrangement.id(),
                "[C]Alpha [F]Beta",
                true,
                LyricsParseStatus.PARSED,
                "[{\"chord\":\"C\",\"line\":1,\"characterOffset\":0},"
                        + "{\"chord\":\"F\",\"line\":1,\"characterOffset\":8}]");
        when(songRepository.findArrangementById(arrangement.id())).thenReturn(Optional.of(arrangement));
        when(songRepository.findLyricsDocumentsByArrangementId(arrangement.id()))
                .thenReturn(List.of(lyricsDocument));
        CatalogService service = new CatalogService(songRepository);

        // Act
        ArrangementRetrievalResult result = service.retrieveArrangement(
                arrangement.id(), Optional.of(new MusicalKey("D", KeyMode.MAJOR))).orElseThrow();

        // Assert
        assertThat(result.baseKey()).isEqualTo(new MusicalKey("C", KeyMode.MAJOR));
        assertThat(result.requestedTargetKey()).isEqualTo(new MusicalKey("D", KeyMode.MAJOR));
        assertThat(result.transpositionInterval()).isEqualTo(2);
        assertThat(result.dynamicallyTransposed()).isTrue();
        assertThat(result.transpositionSource()).isEqualTo(ArrangementTranspositionSource.PARSED_CHORD_MAP);
        assertThat(result.lyricsContent()).isEqualTo("[C]Alpha [F]Beta");
        assertThat(result.chordMapJson()).contains("\"chord\":\"D\"").contains("\"chord\":\"G\"");
        assertThat(lyricsDocument.content()).isEqualTo("[C]Alpha [F]Beta");
        verify(songRepository, never()).createArrangement(any(CreateArrangementCommand.class));
    }

    @Test
    void retrieveArrangementFallsBackToChordSheetContentWhenParsedChordMapIsUnavailable() {
        // Arrange
        Arrangement arrangement = arrangement("C", KeyMode.MAJOR);
        LyricsDocument lyricsDocument = lyricsDocument(
                arrangement.id(), "[Verse]\n[C]Alpha [G]Omega\n", true, LyricsParseStatus.NOT_REQUESTED, null);
        when(songRepository.findArrangementById(arrangement.id())).thenReturn(Optional.of(arrangement));
        when(songRepository.findLyricsDocumentsByArrangementId(arrangement.id()))
                .thenReturn(List.of(lyricsDocument));
        CatalogService service = new CatalogService(songRepository);

        // Act
        ArrangementRetrievalResult result = service.retrieveArrangement(
                arrangement.id(), Optional.of(new MusicalKey("Bb", KeyMode.MAJOR))).orElseThrow();

        // Assert
        assertThat(result.transpositionInterval()).isEqualTo(10);
        assertThat(result.transpositionSource()).isEqualTo(ArrangementTranspositionSource.CHORD_SHEET_CONTENT);
        assertThat(result.lyricsContent()).isEqualTo("[Verse]\n[Bb]Alpha [F]Omega\n");
        assertThat(lyricsDocument.content()).isEqualTo("[Verse]\n[C]Alpha [G]Omega\n");
    }

    @Test
    void retrieveArrangementLeavesEligibilityStateUntouchedForBaseKeyRequests() {
        // Arrange
        Arrangement arrangement = arrangement("G", KeyMode.MAJOR);
        LyricsDocument lyricsDocument = lyricsDocument(
                arrangement.id(), "[G]Alpha", true, LyricsParseStatus.NOT_REQUESTED, null);
        when(songRepository.findArrangementById(arrangement.id())).thenReturn(Optional.of(arrangement));
        when(songRepository.findLyricsDocumentsByArrangementId(arrangement.id()))
                .thenReturn(List.of(lyricsDocument));
        CatalogService service = new CatalogService(songRepository);

        // Act
        ArrangementRetrievalResult result = service.retrieveArrangement(
                arrangement.id(), Optional.empty()).orElseThrow();

        // Assert
        assertThat(result.requestedTargetKey()).isEqualTo(new MusicalKey("G", KeyMode.MAJOR));
        assertThat(result.transpositionInterval()).isZero();
        assertThat(result.dynamicallyTransposed()).isFalse();
        assertThat(result.transpositionSource()).isEqualTo(ArrangementTranspositionSource.NONE);
        assertThat(result.lyricsContent()).isEqualTo("[G]Alpha");
        verify(songRepository, never()).isArrangementDoctrinallyApprovedForRecommendation(arrangement.id());
    }

    private static Arrangement arrangement(String musicalKey, KeyMode keyMode) {
        return new Arrangement(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Fixture Arrangement",
                "fixture-arrangement",
                ArrangementSourceType.CUSTOM,
                "en",
                musicalKey,
                keyMode,
                100,
                "4/4",
                240,
                3,
                2,
                true,
                true,
                Instant.parse("2026-05-15T00:00:00Z"),
                Instant.parse("2026-05-15T00:00:00Z"));
    }

    private static LyricsDocument lyricsDocument(
            UUID arrangementId,
            String content,
            boolean containsChords,
            LyricsParseStatus parseStatus,
            String chordMapJson) {
        return new LyricsDocument(
                UUID.randomUUID(),
                arrangementId,
                LyricsFormat.CHORDPRO,
                content,
                "fixture-hash",
                1,
                true,
                containsChords,
                true,
                "fixture://lyrics",
                "catalog-service-test",
                Instant.parse("2026-05-15T00:00:00Z"),
                parseStatus,
                null,
                parseStatus == LyricsParseStatus.PARSED ? "deterministic-chordpro-parser" : null,
                parseStatus == LyricsParseStatus.PARSED ? "adr-004-v1" : null,
                parseStatus == LyricsParseStatus.PARSED ? Instant.parse("2026-05-15T00:00:00Z") : null,
                null,
                chordMapJson,
                null);
    }
}
