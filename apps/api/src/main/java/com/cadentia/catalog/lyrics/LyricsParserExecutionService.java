package com.cadentia.catalog.lyrics;

import com.cadentia.catalog.entity.LyricsDocument;
import com.cadentia.catalog.entity.ParserRunHistory;
import com.cadentia.catalog.repository.SongRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LyricsParserExecutionService {

    private final SongRepository songRepository;
    private final LyricsParserRegistry registry;

    public LyricsParserExecutionService(SongRepository songRepository, LyricsParserRegistry registry) {
        this.songRepository = songRepository;
        this.registry = registry;
    }

    public LyricsParseResult parseAndPersist(UUID lyricsDocumentId) {
        if (lyricsDocumentId == null) {
            throw new IllegalArgumentException("lyricsDocumentId is required");
        }
        LyricsDocument lyricsDocument = songRepository
                .findLyricsDocumentById(lyricsDocumentId)
                .orElseThrow(() -> new IllegalArgumentException("lyrics document does not exist: " + lyricsDocumentId));

        LyricsParseResult parseResult = registry.parse(lyricsDocument.format(), lyricsDocument.sourceReference(), lyricsDocument.content());
        songRepository.updateLyricsParseResult(lyricsDocumentId, parseResult.toCommand());
        ParserRunHistory latestRun = songRepository.findLatestParserRunHistoryByLyricsDocumentId(lyricsDocumentId).orElse(null);
        ParserRunHistory runHistory = new ParserRunHistory(
                UUID.randomUUID(),
                lyricsDocumentId,
                parseResult.parserName() == null ? "unsupported" : parseResult.parserName(),
                parseResult.parserVersion() == null ? "n/a" : parseResult.parserVersion(),
                lyricsDocument.contentHash(),
                "RECALCULATION",
                "system:lyrics-parser",
                null,
                latestRun == null ? null : latestRun.id(),
                null,
                parseResult.status(),
                List.of(),
                List.of());
        songRepository.appendParserRunHistory(runHistory);
        return parseResult;
    }
}
