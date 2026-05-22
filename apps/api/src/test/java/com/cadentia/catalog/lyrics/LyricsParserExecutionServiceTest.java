package com.cadentia.catalog.lyrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cadentia.catalog.entity.LyricsDocument;
import com.cadentia.catalog.entity.ParserRunHistory;
import com.cadentia.catalog.model.LyricsFormat;
import com.cadentia.catalog.model.LyricsParseStatus;
import com.cadentia.catalog.repository.SongRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LyricsParserExecutionServiceTest {

    @Mock
    private SongRepository songRepository;

    @Captor
    private ArgumentCaptor<ParserRunHistory> parserRunHistoryCaptor;

    @Test
    void executesParserAndPersistsSuccessfulResult() {
        UUID lyricsDocumentId = UUID.randomUUID();
        LyricsDocument document = lyricsDocument(lyricsDocumentId, LyricsFormat.PLAIN_TEXT, "Verse 1\nLine");
        LyricsParserRegistry registry = new LyricsParserRegistry();
        LyricsParserExecutionService service = new LyricsParserExecutionService(songRepository, registry);
        when(songRepository.findLyricsDocumentById(lyricsDocumentId)).thenReturn(Optional.of(document));
        when(songRepository.findLatestParserRunHistoryByLyricsDocumentId(lyricsDocumentId)).thenReturn(Optional.empty());

        LyricsParseResult result = service.parseAndPersist(lyricsDocumentId);

        assertThat(result.status()).isEqualTo(LyricsParseStatus.PARSED);
        verify(songRepository).updateLyricsParseResult(eq(lyricsDocumentId), eq(result.toCommand()));
        verify(songRepository).appendParserRunHistory(parserRunHistoryCaptor.capture());
        assertThat(parserRunHistoryCaptor.getValue().supersedesRunId()).isNull();
        assertThat(parserRunHistoryCaptor.getValue().status()).isEqualTo(LyricsParseStatus.PARSED);
    }

    @Test
    void persistsFailureWhenParserReturnsFailure() {
        UUID lyricsDocumentId = UUID.randomUUID();
        LyricsDocument document = lyricsDocument(lyricsDocumentId, LyricsFormat.PLAIN_TEXT, "   ");
        LyricsParserRegistry registry = new LyricsParserRegistry();
        LyricsParserExecutionService service = new LyricsParserExecutionService(songRepository, registry);
        when(songRepository.findLyricsDocumentById(lyricsDocumentId)).thenReturn(Optional.of(document));
        ParserRunHistory existingRun = new ParserRunHistory(
                UUID.randomUUID(),
                lyricsDocumentId,
                "plain_text",
                "1.0.0",
                "hash",
                "RECALCULATION",
                "system:lyrics-parser",
                Instant.parse("2026-05-17T01:00:00Z"),
                null,
                null,
                LyricsParseStatus.PARSED,
                java.util.List.of(),
                java.util.List.of());
        when(songRepository.findLatestParserRunHistoryByLyricsDocumentId(lyricsDocumentId)).thenReturn(Optional.of(existingRun));

        LyricsParseResult result = service.parseAndPersist(lyricsDocumentId);

        assertThat(result.status()).isEqualTo(LyricsParseStatus.FAILED);
        assertThat(result.error()).contains("content is required");
        verify(songRepository).updateLyricsParseResult(eq(lyricsDocumentId), eq(result.toCommand()));
        verify(songRepository).appendParserRunHistory(parserRunHistoryCaptor.capture());
        assertThat(parserRunHistoryCaptor.getValue().supersedesRunId()).isEqualTo(existingRun.id());
        assertThat(parserRunHistoryCaptor.getValue().status()).isEqualTo(LyricsParseStatus.FAILED);
    }

    @Test
    void throwsWhenLyricsDocumentDoesNotExist() {
        UUID lyricsDocumentId = UUID.randomUUID();
        LyricsParserExecutionService service = new LyricsParserExecutionService(songRepository, new LyricsParserRegistry());
        when(songRepository.findLyricsDocumentById(lyricsDocumentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.parseAndPersist(lyricsDocumentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lyrics document does not exist");
    }

    private static LyricsDocument lyricsDocument(UUID id, LyricsFormat format, String content) {
        return new LyricsDocument(
                id,
                UUID.randomUUID(),
                format,
                content,
                "hash",
                1,
                true,
                false,
                false,
                "fixture-source",
                "tester",
                Instant.parse("2026-05-17T00:00:00Z"),
                LyricsParseStatus.NOT_REQUESTED,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
