package com.cadentia.catalog.lyrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.LyricsFormat;
import com.cadentia.catalog.model.LyricsParseStatus;
import org.junit.jupiter.api.Test;

class LyricsParserRegistryTest {

    @Test
    void resolvesParserByDeclaredFormat() {
        // Arrange
        LyricsParserRegistry registry = new LyricsParserRegistry();

        // Act / Assert
        assertThat(registry.findParser(LyricsFormat.PLAIN_TEXT)).isPresent();
        assertThat(registry.findParser(LyricsFormat.CHORDPRO)).isPresent();
        assertThat(registry.findParser(LyricsFormat.MARKDOWN)).isPresent();
    }

    @Test
    void recordsUnsupportedStatusForStoredButUnparsedFormats() {
        // Arrange
        LyricsParserRegistry registry = new LyricsParserRegistry();

        // Act
        LyricsParseResult result = registry.parse(LyricsFormat.ONSONG, "Verse\nFixture excerpt");

        // Assert
        assertThat(result.status()).isEqualTo(LyricsParseStatus.UNSUPPORTED);
        assertThat(result.error()).contains("No deterministic parser").contains("onsong");
    }
}
