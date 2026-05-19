package com.cadentia.catalog.lyrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.cadentia.catalog.entity.LyricsDocument;
import com.cadentia.catalog.model.LyricsFormat;
import com.cadentia.catalog.model.LyricsParseStatus;
import com.cadentia.catalog.repository.SongRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LyricsParserReviewServiceTest {

    @Mock
    private SongRepository songRepository;

    @Test
    void marksResultStaleWhenSourceContentHashDiffers() {
        UUID lyricsDocumentId = UUID.randomUUID();
        LyricsDocument document = lyricsDocument(lyricsDocumentId, "current-hash", "Verse 1\nLine");
        LyricsParserReviewService service = new LyricsParserReviewService(songRepository, new LyricsParserExecutionService(songRepository, new LyricsParserRegistry()));
        when(songRepository.findLyricsDocumentById(lyricsDocumentId)).thenReturn(Optional.of(document));

        ParserReviewResultView result = service.getLatestResultForSourceHash(lyricsDocumentId, "older-hash");

        assertThat(result.stale()).isTrue();
    }

    @Test
    void recalculationReportsSupersededWhenSourceHashChanged() {
        UUID lyricsDocumentId = UUID.randomUUID();
        LyricsDocument document = lyricsDocument(lyricsDocumentId, "new-hash", "Verse 1\nLine");
        LyricsParserReviewService service = new LyricsParserReviewService(songRepository, new LyricsParserExecutionService(songRepository, new LyricsParserRegistry()));
        when(songRepository.findLyricsDocumentById(lyricsDocumentId)).thenReturn(Optional.of(document));

        ParserRecalculationResult result = service.recalculate(lyricsDocumentId, "old-hash");

        assertThat(result.supersededPriorResult()).isTrue();
        assertThat(result.latestResult().parseStatus()).isEqualTo(LyricsParseStatus.PARSED);
    }

    @Test
    void throwsWhenLyricsDocumentDoesNotExist() {
        UUID lyricsDocumentId = UUID.randomUUID();
        LyricsParserReviewService service = new LyricsParserReviewService(songRepository, new LyricsParserExecutionService(songRepository, new LyricsParserRegistry()));
        when(songRepository.findLyricsDocumentById(lyricsDocumentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLatestResult(lyricsDocumentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lyrics document does not exist");
    }

    private static LyricsDocument lyricsDocument(UUID id, String contentHash, String content) {
        return new LyricsDocument(
                id,
                UUID.randomUUID(),
                LyricsFormat.PLAIN_TEXT,
                content,
                contentHash,
                2,
                true,
                true,
                true,
                "fixture-source",
                "tester",
                Instant.parse("2026-05-18T00:00:00Z"),
                LyricsParseStatus.PARSED,
                null,
                "deterministic-lyrics-parser",
                "2.0.0",
                Instant.parse("2026-05-18T00:05:00Z"),
                "[]",
                "[]",
                "[]");
    }
}
