package com.cadentia.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.entity.Song;
import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.ApprovalType;
import com.cadentia.catalog.model.ArrangementSourceType;
import com.cadentia.catalog.model.CreateApprovalRecordCommand;
import com.cadentia.catalog.model.CreateArrangementCommand;
import com.cadentia.catalog.model.CreateSongCommand;
import com.cadentia.catalog.model.CreateTagCommand;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.model.SongStatus;
import com.cadentia.catalog.model.TagType;
import com.cadentia.catalog.repository.JdbcSongRepository;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcApprovedSearchDocumentProviderIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcApprovedSearchDocumentProvider provider;
    private JdbcSongRepository songRepository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).load().migrate();
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        provider = new JdbcApprovedSearchDocumentProvider(jdbcTemplate);
        songRepository = new JdbcSongRepository(jdbcTemplate);
    }

    @Test
    void populatesScriptureReferencesFromCuratedScriptureTags() {
        // Arrange
        Song song = approvedSong("scripture-tagged", SongStatus.APPROVED);
        songRepository.addTagToSong(song.id(), songRepository.createTag(new CreateTagCommand(
                TagType.SCRIPTURE, "Philippians 4:10-20", "philippians-4-10-20", "Fixture tag", true)).id());
        songRepository.addTagToSong(song.id(), songRepository.createTag(new CreateTagCommand(
                TagType.SCRIPTURE, "Psalm 23", "psalm-23", "Fixture tag", true)).id());
        songRepository.addTagToSong(song.id(), songRepository.createTag(new CreateTagCommand(
                TagType.THEME, "Repentance", "repentance", "Fixture tag", true)).id());

        // Act
        List<ApprovedSearchModels.ApprovedSearchDocument> documents = provider.documents();

        // Assert
        assertThat(documents)
                .filteredOn(document -> document.songId().equals(song.id()))
                .singleElement()
                .satisfies(document -> assertThat(document.scriptureReferences()).containsExactly(
                        new NormalizedScriptureReference("philippians", 4, 10, 20),
                        new NormalizedScriptureReference("psalms", 23, null, null)));
    }

    @Test
    void returnsEmptyScriptureReferencesForUntaggedSongs() {
        // Arrange
        Song song = approvedSong("untagged", SongStatus.APPROVED);

        // Act
        List<ApprovedSearchModels.ApprovedSearchDocument> documents = provider.documents();

        // Assert
        assertThat(documents)
                .filteredOn(document -> document.songId().equals(song.id()))
                .singleElement()
                .satisfies(document -> assertThat(document.scriptureReferences()).isEmpty());
    }

    private Song approvedSong(String slug, SongStatus status) {
        Song song = songRepository.createSong(new CreateSongCommand(
                "Fixture Song " + slug,
                "fixture-song-" + slug,
                "en",
                "Fixture Artist",
                "Fixture Writer",
                "CCLI-" + slug,
                2026,
                status,
                null));
        songRepository.createArrangement(new CreateArrangementCommand(
                song.id(),
                "Default Arrangement",
                "default-arrangement",
                ArrangementSourceType.UNKNOWN,
                "en",
                "G",
                KeyMode.MAJOR,
                96,
                "4/4",
                240,
                3,
                2,
                true,
                true));
        songRepository.createApprovalRecord(new CreateApprovalRecordCommand(
                song.id(), null, null, ApprovalType.EDITORIAL, ApprovalStatus.APPROVED, "reviewer@example.test", null));
        songRepository.createApprovalRecord(new CreateApprovalRecordCommand(
                song.id(), null, null, ApprovalType.LICENSING, ApprovalStatus.APPROVED, "reviewer@example.test", null));
        return song;
    }
}
