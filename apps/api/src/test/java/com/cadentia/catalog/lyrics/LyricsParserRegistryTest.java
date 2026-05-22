package com.cadentia.catalog.lyrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.LyricsFormat;
import com.cadentia.catalog.model.LyricsParseStatus;
import org.junit.jupiter.api.Test;

class LyricsParserRegistryTest {

    @Test
    void resolvesParserByDeclaredFormat() {
        LyricsParserRegistry registry = new LyricsParserRegistry();

        assertThat(registry.findParser(LyricsFormat.PLAIN_TEXT, null)).isPresent();
        assertThat(registry.findParser(LyricsFormat.CHORDPRO, null)).isPresent();
        assertThat(registry.findParser(LyricsFormat.MARKDOWN, null)).isPresent();
    }

    @Test
    void resolvesParserDeterministicallyByPriorityThenName() {
        LyricsParserPlugin lowPriority = new StubPlugin("zeta", 10, false);
        LyricsParserPlugin highPriority = new StubPlugin("alpha", 100, true);
        LyricsParserPlugin samePriorityNameTieBreaker = new StubPlugin("beta", 100, true);
        LyricsParserRegistry registry = new LyricsParserRegistry(
                java.util.List.of(lowPriority, samePriorityNameTieBreaker, highPriority),
                new ParserCapabilityRegistry(),
                new ParserDiagnosticCodebook());

        assertThat(registry.findParser(LyricsFormat.PLAIN_TEXT, "source")).hasValue(highPriority);
    }

    @Test
    void recordsUnsupportedStatusForStoredButUnparsedFormats() {
        LyricsParserRegistry registry = new LyricsParserRegistry();

        LyricsParseResult result = registry.parse(LyricsFormat.ONSONG, "reference", "Verse\nFixture excerpt");

        assertThat(result.status()).isEqualTo(LyricsParseStatus.UNSUPPORTED);
        assertThat(result.error()).contains("No deterministic parser").contains("onsong");
    }

    private record StubPlugin(String parserName, int priority, boolean supported) implements LyricsParserPlugin {

        @Override
        public boolean supports(ParserSelectionInput selectionInput) {
            return supported && selectionInput != null && selectionInput.format() == LyricsFormat.PLAIN_TEXT;
        }

        @Override
        public LyricsFormat format() {
            return LyricsFormat.PLAIN_TEXT;
        }

        @Override
        public String parserVersion() {
            return "test-v1";
        }

        @Override
        public LyricsParseResult parse(String content) {
            return LyricsParseResult.parsed(parserName, parserVersion(), "[]", "[]", "[]");
        }
    }
}
