package com.cadentia.catalog.service;

import com.cadentia.catalog.entity.Arrangement;
import com.cadentia.catalog.entity.LyricsDocument;
import com.cadentia.catalog.transposition.MusicalKey;

public record ArrangementRetrievalResult(
        Arrangement arrangement,
        LyricsDocument lyricsDocument,
        MusicalKey baseKey,
        MusicalKey requestedTargetKey,
        int transpositionInterval,
        boolean dynamicallyTransposed,
        ArrangementTranspositionSource transpositionSource,
        String lyricsContent,
        String chordMapJson) {
}
