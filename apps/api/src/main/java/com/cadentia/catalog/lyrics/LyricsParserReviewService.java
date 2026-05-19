package com.cadentia.catalog.lyrics;

import com.cadentia.catalog.entity.LyricsDocument;
import com.cadentia.catalog.repository.SongRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LyricsParserReviewService {

    private final SongRepository songRepository;
    private final LyricsParserExecutionService executionService;

    public LyricsParserReviewService(SongRepository songRepository, LyricsParserExecutionService executionService) {
        this.songRepository = songRepository;
        this.executionService = executionService;
    }

    public ParserReviewResultView getLatestResult(UUID lyricsDocumentId) {
        LyricsDocument document = loadDocument(lyricsDocumentId);
        return ParserReviewResultView.from(document, false);
    }

    public ParserReviewResultView getLatestResultForSourceHash(UUID lyricsDocumentId, String sourceContentHash) {
        LyricsDocument document = loadDocument(lyricsDocumentId);
        boolean stale = sourceContentHash != null && !sourceContentHash.isBlank() && !sourceContentHash.equals(document.contentHash());
        return ParserReviewResultView.from(document, stale);
    }

    public ParserRecalculationResult recalculate(UUID lyricsDocumentId, String previousSourceContentHash) {
        loadDocument(lyricsDocumentId);
        executionService.parseAndPersist(lyricsDocumentId);
        LyricsDocument after = loadDocument(lyricsDocumentId);
        boolean superseded = previousSourceContentHash != null
                && !previousSourceContentHash.isBlank()
                && !previousSourceContentHash.equals(after.contentHash());
        return new ParserRecalculationResult(ParserReviewResultView.from(after, false), superseded);
    }

    public ParserReviewAnnotationCommand prepareAnnotation(ParserReviewAnnotationCommand command) {
        loadDocument(command.lyricsDocumentId());
        return command;
    }

    private LyricsDocument loadDocument(UUID lyricsDocumentId) {
        if (lyricsDocumentId == null) {
            throw new IllegalArgumentException("lyricsDocumentId is required");
        }
        return songRepository
                .findLyricsDocumentById(lyricsDocumentId)
                .orElseThrow(() -> new IllegalArgumentException("lyrics document does not exist: " + lyricsDocumentId));
    }
}
