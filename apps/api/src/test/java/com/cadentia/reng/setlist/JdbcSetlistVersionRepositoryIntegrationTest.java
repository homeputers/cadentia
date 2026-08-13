package com.cadentia.reng.setlist;

import static com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistBaselineCommand;
import static com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistItemCommand;
import static com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistVersionCommand;
import static com.cadentia.reng.setlist.SetlistVersionModels.SetlistEditEventCommand;
import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.entity.Arrangement;
import com.cadentia.catalog.entity.Song;
import com.cadentia.catalog.model.ArrangementSourceType;
import com.cadentia.catalog.model.CreateArrangementCommand;
import com.cadentia.catalog.model.CreateSongCommand;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.model.SongStatus;
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
class JdbcSetlistVersionRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcSetlistVersionRepository repository;
    private JdbcSongRepository songRepository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).load().migrate();
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        repository = new JdbcSetlistVersionRepository(jdbcTemplate);
        songRepository = new JdbcSongRepository(jdbcTemplate);
    }

    @Test
    void createBaselineAndEditPersistImmutableLineageRecords() {
        Arrangement arrangementA = arrangement("A");
        Arrangement arrangementB = arrangement("B");

        var baseline = repository.createBaseline(new CreateSetlistBaselineCommand(
                "planner@cadentia",
                "scoring-v1",
                "engine-v1",
                "{\"request\":\"baseline\"}",
                "{\"intent\":\"GENERATE_SETLIST\"}",
                "[{\"fact\":\"baseline\"}]",
                List.of(new CreateSetlistItemCommand(0, arrangementA.id(), "G", "MAJOR", null, "GENERATED", "opening")),
                "LINEAR"));

        var edited = repository.createEditedVersion(new CreateSetlistVersionCommand(
                baseline.setlistId(),
                baseline.versionId(),
                "planner@cadentia",
                "scoring-v1",
                "engine-v1",
                "{\"request\":\"edited\"}",
                "{\"intent\":\"GENERATE_SETLIST\"}",
                "[{\"fact\":\"manual-edit\"}]",
                "swap closer",
                List.of(new CreateSetlistItemCommand(
                        0,
                        arrangementB.id(),
                        "A",
                        "MAJOR",
                        baseline.items().get(0).id(),
                        "MANUAL",
                        "swap")),
                List.of(new SetlistEditEventCommand(
                        0,
                        "REPLACE",
                        baseline.items().get(0).id(),
                        0,
                        0,
                        arrangementB.id(),
                        null,
                        null,
                        false,
                        "{\"reason\":\"tempo arc\"}"))));

        assertThat(edited.parentVersionId()).isEqualTo(baseline.versionId());
        assertThat(edited.versionNumber()).isEqualTo(2);
        assertThat(edited.requestPayload()).contains("\"request\"").contains("\"edited\"");
        assertThat(edited.parsedIntentPayload()).contains("\"intent\"").contains("\"GENERATE_SETLIST\"");
        assertThat(edited.explanationFactsPayload()).contains("\"fact\"").contains("\"manual-edit\"");
        assertThat(edited.items()).singleElement().satisfies(item -> {
            assertThat(item.catalogArrangementId()).isEqualTo(arrangementB.id());
            assertThat(item.itemProvenance()).isEqualTo("MANUAL");
            assertThat(item.sourceItemId()).isEqualTo(baseline.items().get(0).id());
        });

        assertThat(repository.findVersions(baseline.setlistId())).hasSize(2);
        assertThat(repository.findVersion(baseline.setlistId(), edited.versionId())).isPresent();
    }

    private Arrangement arrangement(String suffix) {
        Song song = songRepository.createSong(new CreateSongCommand(
                "Fixture Song " + suffix,
                "fixture-song-" + suffix.toLowerCase(),
                "en",
                "Fixture Artist",
                "Fixture Composer",
                "CCLI-" + suffix,
                2020,
                SongStatus.APPROVED,
                "notes"));
        return songRepository.createArrangement(new CreateArrangementCommand(
                song.id(),
                "Arrangement " + suffix,
                "arrangement-" + suffix.toLowerCase(),
                ArrangementSourceType.ORIGINAL,
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
    }
}
